package com.example.crypto

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.local.AuthType
import com.example.data.local.BackendType
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LocalIdentity(
    val handle: String,
    val publicKeyBase64: String,
    val shortFingerprint: String
)

data class EncryptedEnvelope(
    val ivBase64: String,
    val ciphertextBase64: String
)

class VeilCryptoEngine(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val secureRandom = SecureRandom()

    private val masterKey: SecretKey by lazy { getOrCreateMasterKey() }

    @Synchronized
    fun getOrCreateIdentity(): LocalIdentity {
        val existingPub = prefs.getString(KEY_PUB, null)
        val existingPrivWrapped = prefs.getString(KEY_PRIV_WRAPPED, null)
        val existingHandle = prefs.getString(KEY_HANDLE, null)

        if (!existingPub.isNullOrBlank() && !existingPrivWrapped.isNullOrBlank()) {
            val handle = existingHandle?.ifBlank { defaultHandle(existingPub) } ?: defaultHandle(existingPub)
            return LocalIdentity(
                handle = handle,
                publicKeyBase64 = existingPub,
                shortFingerprint = computeShortFingerprint(existingPub)
            )
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val keyPair = kpg.generateKeyPair()

        val pubB64 = encodeB64(keyPair.public.encoded)
        val privWrapped = protectSecretBytes(keyPair.private.encoded)
        val handle = existingHandle?.ifBlank { defaultHandle(pubB64) } ?: defaultHandle(pubB64)

        prefs.edit()
            .putString(KEY_PUB, pubB64)
            .putString(KEY_PRIV_WRAPPED, privWrapped)
            .putString(KEY_HANDLE, handle)
            .apply()

        return LocalIdentity(
            handle = handle,
            publicKeyBase64 = pubB64,
            shortFingerprint = computeShortFingerprint(pubB64)
        )
    }

    fun updateHandle(newHandle: String): LocalIdentity {
        val sanitized = stripEmojis(newHandle).trim().take(24).ifBlank { "node" }
        prefs.edit().putString(KEY_HANDLE, sanitized).apply()
        return getOrCreateIdentity()
    }

    fun protectString(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return protectSecretBytes(plaintext.toByteArray(Charsets.UTF_8))
    }

    fun unprotectString(protectedValue: String): String {
        if (protectedValue.isEmpty()) return ""
        return runCatching {
            String(unprotectSecretBytes(protectedValue), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /**
     * Generates a cryptographically secure 16-character Veil Code across uppercase letters,
     * lowercase letters, digits 0-9, and symbols (!@#$%^&*-_+=?) using SecureRandom.
     */
    fun generateVeilCode(): String {
        val chars = CharArray(VEIL_CODE_LENGTH)
        // Guarantee at least one character from each category for high entropy and visual variety.
        chars[0] = UPPER_CHARS[secureRandom.nextInt(UPPER_CHARS.length)]
        chars[1] = LOWER_CHARS[secureRandom.nextInt(LOWER_CHARS.length)]
        chars[2] = DIGIT_CHARS[secureRandom.nextInt(DIGIT_CHARS.length)]
        chars[3] = SYMBOL_CHARS[secureRandom.nextInt(SYMBOL_CHARS.length)]
        for (i in 4 until VEIL_CODE_LENGTH) {
            chars[i] = FULL_CODE_CHARSET[secureRandom.nextInt(FULL_CODE_CHARSET.length)]
        }
        // Fisher-Yates shuffle with SecureRandom
        for (i in chars.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    /**
     * Normalizes user-entered or pasted 16-character Veil Codes by removing display block
     * separators (" - ") and whitespace while preserving symbols (including hyphens inside blocks).
     */
    fun normalizeVeilCode(raw: String): String {
        val withoutDisplaySeps = raw.trim().replace(" - ", "")
        val compact = withoutDisplaySeps.filter { it in '!'..'~' }
        val unwrapped = if (
            compact.length == 19 &&
            compact[4] == '-' &&
            compact[9] == '-' &&
            compact[14] == '-'
        ) {
            compact.substring(0, 4) +
                compact.substring(5, 9) +
                compact.substring(10, 14) +
                compact.substring(15, 19)
        } else {
            compact
        }
        return unwrapped.take(VEIL_CODE_LENGTH)
    }

    fun isValidVeilCode(raw: String): Boolean = normalizeVeilCode(raw).length == VEIL_CODE_LENGTH

    fun formatVeilCode(raw: String): String {
        val clean = normalizeVeilCode(raw)
        return clean.chunked(4).joinToString(" - ")
    }

    fun generateRandomKeyBase64(): String {
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        return encodeB64(keyBytes)
    }

    fun generateRandomRoomKeyBase64(): String = generateRandomKeyBase64()

    fun isValidPublicKey(publicKeyB64: String): Boolean {
        return runCatching {
            decodePublicKey(publicKeyB64)
            true
        }.getOrDefault(false)
    }

    fun deriveEcdhRoomKey(peerPublicKeyB64: String, contextId: String): ByteArray {
        val privateKey = loadLocalPrivateKey()
        val peerPublicKey = decodePublicKey(peerPublicKeyB64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()
        return hkdfSha256(
            ikm = sharedSecret,
            salt = "veil-ecdh-salt-$contextId".toByteArray(Charsets.UTF_8),
            info = "veil-aes256-gcm:$contextId".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )
    }

    fun deriveSymmetricKey(secretB64: String, contextId: String): ByteArray {
        val raw = decodeB64(secretB64)
        return hkdfSha256(
            ikm = raw,
            salt = "veil-sym-salt-$contextId".toByteArray(Charsets.UTF_8),
            info = "veil-content-v1:$contextId".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )
    }

    fun deriveSymmetricRoomKey(roomKeyB64: String, veilCode: String): ByteArray =
        deriveSymmetricKey(roomKeyB64, veilCode)

    fun wrapCommunityKeyForPeer(
        communityKeyB64: String,
        peerPublicKeyB64: String,
        communityId: String
    ): String {
        val wrappingKey = deriveEcdhRoomKey(peerPublicKeyB64, "community-wrap:$communityId")
        val env = encryptText(wrappingKey, communityKeyB64)
        return "${env.ivBase64}:${env.ciphertextBase64}"
    }

    fun unwrapCommunityKeyFromPeer(
        wrappedPayload: String,
        senderPublicKeyB64: String,
        communityId: String
    ): String? {
        val parts = wrappedPayload.split(":")
        if (parts.size != 2) return null
        return runCatching {
            val wrappingKey = deriveEcdhRoomKey(senderPublicKeyB64, "community-wrap:$communityId")
            decryptText(wrappingKey, parts[0], parts[1])
        }.getOrNull()
    }

    fun encryptSignalForPeer(
        peerPublicKeyB64: String,
        roomCode: String,
        plaintext: String
    ): String {
        val key = deriveEcdhRoomKey(peerPublicKeyB64, "call-signal:$roomCode")
        val env = encryptText(key, plaintext)
        return "${env.ivBase64}:${env.ciphertextBase64}"
    }

    fun decryptSignalFromPeer(
        peerPublicKeyB64: String,
        roomCode: String,
        packedPayload: String
    ): String? {
        val parts = packedPayload.split(":")
        if (parts.size != 2) return null
        return runCatching {
            val key = deriveEcdhRoomKey(peerPublicKeyB64, "call-signal:$roomCode")
            decryptText(key, parts[0], parts[1])
        }.getOrNull()
    }

    fun encryptText(keyBytes: ByteArray, plaintext: String): EncryptedEnvelope =
        encryptBytes(keyBytes, plaintext.toByteArray(Charsets.UTF_8))

    fun decryptText(keyBytes: ByteArray, ivBase64: String, ciphertextBase64: String): String =
        String(decryptBytes(keyBytes, ivBase64, ciphertextBase64), Charsets.UTF_8)

    fun encryptBytes(keyBytes: ByteArray, plaintext: ByteArray): EncryptedEnvelope {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEnvelope(encodeB64(iv), encodeB64(ciphertext))
    }

    fun decryptBytes(keyBytes: ByteArray, ivBase64: String, ciphertextBase64: String): ByteArray {
        val iv = decodeB64(ivBase64)
        val ciphertext = decodeB64(ciphertextBase64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    fun generateMediaKeyAndIv(): Pair<String, String> {
        val key = ByteArray(32)
        val iv = ByteArray(12)
        secureRandom.nextBytes(key)
        secureRandom.nextBytes(iv)
        return encodeB64Url(key) to encodeB64Url(iv)
    }

    fun encryptBytesWithKeyAndIv(keyB64: String, ivB64: String, rawBytes: ByteArray): ByteArray {
        val keyBytes = decodeB64(keyB64)
        val ivBytes = decodeB64(ivB64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, ivBytes))
        return cipher.doFinal(rawBytes)
    }

    fun decryptBytesWithKeyAndIv(keyB64: String, ivB64: String, ciphertextBytes: ByteArray): ByteArray {
        val keyBytes = decodeB64(keyB64)
        val ivBytes = decodeB64(ivB64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, ivBytes))
        return cipher.doFinal(ciphertextBytes)
    }

    fun encryptBlobPacket(keyBytes: ByteArray, rawBytes: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(rawBytes)
    }

    fun decryptBlobPacket(keyBytes: ByteArray, packet: ByteArray): ByteArray {
        require(packet.size > 12) { "Encrypted media blob is malformed" }
        val iv = packet.copyOfRange(0, 12)
        val body = packet.copyOfRange(12, packet.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    fun computeSafetyNumber(localPublicKeyB64: String, peerPublicKeyB64: String): String {
        val a = runCatching { decodeB64(localPublicKeyB64) }.getOrElse { localPublicKeyB64.toByteArray() }
        val b = runCatching { decodeB64(peerPublicKeyB64) }.getOrElse { peerPublicKeyB64.toByteArray() }
        val ordered = if (localPublicKeyB64 <= peerPublicKeyB64) listOf(a, b) else listOf(b, a)

        val md = MessageDigest.getInstance("SHA-256")
        val digits = StringBuilder(60)
        for (part in ordered) {
            var digest = part
            repeat(520) {
                md.reset()
                md.update(digest)
                md.update(part)
                digest = md.digest()
            }
            for (chunkIndex in 0 until 6) {
                val offset = chunkIndex * 5
                val value = ((digest[offset].toLong() and 0xFFL) shl 32) or
                    ((digest[offset + 1].toLong() and 0xFFL) shl 24) or
                    ((digest[offset + 2].toLong() and 0xFFL) shl 16) or
                    ((digest[offset + 3].toLong() and 0xFFL) shl 8) or
                    (digest[offset + 4].toLong() and 0xFFL)
                digits.append((value % 100_000L).toString().padStart(5, '0'))
            }
        }
        return digits.toString().take(60)
    }

    fun formatSafetyNumberGroups(safetyNumber60: String): List<String> =
        safetyNumber60.filter { it.isDigit() }.padEnd(60, '0').take(60).chunked(5)

    fun computeShortFingerprint(publicKeyB64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyB64.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString(":") { "%02X".format(it) }
    }

    fun buildIdentityUri(identity: LocalIdentity): String {
        return Uri.Builder()
            .scheme("veil")
            .authority("id")
            .appendQueryParameter("handle", identity.handle)
            .appendQueryParameter("pub", identity.publicKeyBase64)
            .build()
            .toString()
    }

    private fun loadLocalPrivateKey(): PrivateKey {
        getOrCreateIdentity()
        val wrapped = prefs.getString(KEY_PRIV_WRAPPED, null)
            ?: error("Local identity private key missing")
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(unprotectSecretBytes(wrapped)))
    }

    private fun decodePublicKey(publicKeyB64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decodeB64(publicKeyB64)))

    private fun protectSecretBytes(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        return "${encodeB64(cipher.iv)}:${encodeB64(cipher.doFinal(plain))}"
    }

    private fun unprotectSecretBytes(stored: String): ByteArray {
        val parts = stored.split(":")
        require(parts.size == 2) { "Corrupt wrapped secret" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, decodeB64(parts[0])))
        return cipher.doFinal(decodeB64(parts[1]))
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrElse {
            val fallbackHex = prefs.getString(KEY_FALLBACK_WRAP, null)
            val raw = if (fallbackHex != null) decodeB64(fallbackHex) else ByteArray(32).also {
                secureRandom.nextBytes(it)
                prefs.edit().putString(KEY_FALLBACK_WRAP, encodeB64(it)).apply()
            }
            SecretKeySpec(raw, "AES")
        }
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        val result = ByteArray(outputLength)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < outputLength) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, outputLength - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    private fun defaultHandle(pubB64: String): String {
        val suffix = computeShortFingerprint(pubB64).replace(":", "").take(4).lowercase()
        return "veil-$suffix"
    }

    private fun encodeB64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun encodeB64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decodeB64(input: String): ByteArray {
        val trimmed = input.trim()
        val isUrlSafe = trimmed.contains('-') || trimmed.contains('_')
        val flags = if (isUrlSafe) Base64.URL_SAFE or Base64.NO_WRAP else Base64.DEFAULT
        return Base64.decode(trimmed, flags)
    }

    companion object {
        const val VEIL_CODE_LENGTH = 16
        private const val UPPER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWER_CHARS = "abcdefghijklmnopqrstuvwxyz"
        private const val DIGIT_CHARS = "0123456789"
        private const val SYMBOL_CHARS = "!@#\$%^&*-_+=?"
        private const val FULL_CODE_CHARSET = UPPER_CHARS + LOWER_CHARS + DIGIT_CHARS + SYMBOL_CHARS

        private const val PREFS_NAME = "veil_identity_keystore_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "veil_keystore_master_v1"
        private const val KEY_PUB = "identity_ec_pub"
        private const val KEY_PRIV_WRAPPED = "identity_ec_priv_wrapped"
        private const val KEY_HANDLE = "identity_handle"
        private const val KEY_FALLBACK_WRAP = "fallback_wrap_key"

        fun stripEmojis(input: String): String {
            val sb = StringBuilder(input.length)
            var i = 0
            while (i < input.length) {
                val cp = input.codePointAt(i)
                val charCount = Character.charCount(cp)
                if (!isEmojiCodePoint(cp)) {
                    sb.appendCodePoint(cp)
                }
                i += charCount
            }
            return sb.toString()
        }

        fun countWords(input: String): Int {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return 0
            return trimmed.split(Regex("\\s+")).count { it.isNotBlank() }
        }

        fun enforceMaxWords(input: String, maxWords: Int = 20): String {
            val cleaned = stripEmojis(input)
            val words = cleaned.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size <= maxWords) return cleaned
            return words.take(maxWords).joinToString(" ")
        }

        private fun isEmojiCodePoint(cp: Int): Boolean {
            val type = Character.getType(cp)
            if (type == Character.OTHER_SYMBOL.toInt() || type == Character.SURROGATE.toInt()) return true
            return (cp in 0x1F300..0x1FAFF) ||
                (cp in 0x2600..0x27BF) ||
                (cp in 0xFE00..0xFE0F) ||
                cp == 0x200D
        }
    }
}
