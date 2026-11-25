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

data class ParsedRoomInvite(
    val veilCode: String,
    val roomName: String,
    val backendType: BackendType,
    val baseUrl: String,
    val apiKey: String,
    val authType: AuthType,
    val authHeaderName: String,
    val hostPublicKey: String,
    val hostHandle: String,
    val roomKeyBase64: String,
    val webSocketUrl: String = "",
    val mediaUploadUrl: String = ""
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
        val sanitized = newHandle.trim().take(32).ifBlank { "node" }
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

    fun generateVeilCode(): String {
        val digits = StringBuilder(12)
        repeat(12) {
            digits.append(secureRandom.nextInt(10))
        }
        return digits.toString()
    }

    fun formatVeilCode(raw: String): String {
        val clean = raw.filter { it.isDigit() }.take(12)
        return clean.chunked(4).joinToString(" - ")
    }

    fun generateRandomRoomKeyBase64(): String {
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        return encodeB64(keyBytes)
    }

    fun isValidPublicKey(publicKeyB64: String): Boolean {
        return runCatching {
            decodePublicKey(publicKeyB64)
            true
        }.getOrDefault(false)
    }

    /**
     * Derives a 256-bit conversation key using ECDH P-256 between this device's private key
     * and the peer's public key, stretched with HKDF-SHA256 bound to the 12-digit Veil Code.
     * Because private keys never leave the devices, neither the Supabase owner nor any server
     * admin can compute this key.
     */
    fun deriveEcdhRoomKey(peerPublicKeyB64: String, veilCode: String): ByteArray {
        val privateKey = loadLocalPrivateKey()
        val peerPublicKey = decodePublicKey(peerPublicKeyB64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()
        return hkdfSha256(
            ikm = sharedSecret,
            salt = "veil-ecdh-salt-$veilCode".toByteArray(Charsets.UTF_8),
            info = "veil-room-aes256-gcm:$veilCode".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )
    }

    fun deriveSymmetricRoomKey(roomKeyB64: String, veilCode: String): ByteArray {
        val raw = decodeB64(roomKeyB64)
        return hkdfSha256(
            ikm = raw,
            salt = "veil-room-salt-$veilCode".toByteArray(Charsets.UTF_8),
            info = "veil-room-content-v1:$veilCode".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )
    }

    fun encryptText(keyBytes: ByteArray, plaintext: String): EncryptedEnvelope {
        return encryptBytes(keyBytes, plaintext.toByteArray(Charsets.UTF_8))
    }

    fun decryptText(keyBytes: ByteArray, ivBase64: String, ciphertextBase64: String): String {
        val bytes = decryptBytes(keyBytes, ivBase64, ciphertextBase64)
        return String(bytes, Charsets.UTF_8)
    }

    fun encryptBytes(keyBytes: ByteArray, plaintext: ByteArray): EncryptedEnvelope {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEnvelope(
            ivBase64 = encodeB64(iv),
            ciphertextBase64 = encodeB64(ciphertext)
        )
    }

    fun decryptBytes(keyBytes: ByteArray, ivBase64: String, ciphertextBase64: String): ByteArray {
        val iv = decodeB64(ivBase64)
        val ciphertext = decodeB64(ciphertextBase64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptBlobPacket(keyBytes: ByteArray, rawBytes: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(rawBytes)
        return iv + encrypted
    }

    fun decryptBlobPacket(keyBytes: ByteArray, packet: ByteArray): ByteArray {
        require(packet.size > 12) { "Encrypted media blob is malformed" }
        val iv = packet.copyOfRange(0, 12)
        val body = packet.copyOfRange(12, packet.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    /**
     * Computes a deterministic 60-digit Safety Number (12 groups of 5 digits) from both
     * participants' EC P-256 public keys, matching Signal-style symmetric fingerprint verification.
     */
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
                val fiveDigits = (value % 100_000L).toString().padStart(5, '0')
                digits.append(fiveDigits)
            }
        }
        return digits.toString().take(60)
    }

    fun formatSafetyNumberGroups(safetyNumber60: String): List<String> {
        val clean = safetyNumber60.filter { it.isDigit() }.padEnd(60, '0').take(60)
        return clean.chunked(5)
    }

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

    fun buildRoomInviteUri(
        veilCode: String,
        roomName: String,
        backendType: BackendType,
        baseUrl: String,
        apiKey: String,
        authType: AuthType,
        authHeaderName: String,
        hostPublicKey: String,
        hostHandle: String,
        roomKeyBase64: String,
        webSocketUrl: String = "",
        mediaUploadUrl: String = ""
    ): String {
        val builder = Uri.Builder()
            .scheme("veil")
            .authority("room")
            .appendQueryParameter("code", veilCode.filter { it.isDigit() })
            .appendQueryParameter("name", roomName)
            .appendQueryParameter("type", backendType.name)
            .appendQueryParameter("url", baseUrl)
            .appendQueryParameter("key", apiKey)
            .appendQueryParameter("auth", authType.name)
            .appendQueryParameter("ah", authHeaderName)
            .appendQueryParameter("pub", hostPublicKey)
            .appendQueryParameter("host", hostHandle)
            .appendQueryParameter("rk", roomKeyBase64)
        if (webSocketUrl.isNotBlank()) {
            builder.appendQueryParameter("ws", webSocketUrl)
        }
        if (mediaUploadUrl.isNotBlank()) {
            builder.appendQueryParameter("s3", mediaUploadUrl)
        }
        return builder.build().toString()
    }

    fun parseRoomInviteUri(raw: String): ParsedRoomInvite? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("veil://room")) return null
        return runCatching {
            val uri = Uri.parse(trimmed)
            val code = uri.getQueryParameter("code")?.filter { it.isDigit() } ?: return null
            if (code.length != 12) return null
            val typeStr = uri.getQueryParameter("type") ?: BackendType.SUPABASE.name
            val authStr = uri.getQueryParameter("auth") ?: AuthType.BEARER.name
            ParsedRoomInvite(
                veilCode = code,
                roomName = uri.getQueryParameter("name").orEmpty().ifBlank { "Veil Room" },
                backendType = runCatching { BackendType.valueOf(typeStr) }.getOrDefault(BackendType.SUPABASE),
                baseUrl = uri.getQueryParameter("url").orEmpty(),
                apiKey = uri.getQueryParameter("key").orEmpty(),
                authType = runCatching { AuthType.valueOf(authStr) }.getOrDefault(AuthType.BEARER),
                authHeaderName = uri.getQueryParameter("ah").orEmpty().ifBlank { "X-API-Key" },
                hostPublicKey = uri.getQueryParameter("pub").orEmpty(),
                hostHandle = uri.getQueryParameter("host").orEmpty().ifBlank { "Host" },
                roomKeyBase64 = uri.getQueryParameter("rk").orEmpty(),
                webSocketUrl = uri.getQueryParameter("ws").orEmpty(),
                mediaUploadUrl = uri.getQueryParameter("s3").orEmpty()
            )
        }.getOrNull()
    }

    private fun loadLocalPrivateKey(): PrivateKey {
        getOrCreateIdentity()
        val wrapped = prefs.getString(KEY_PRIV_WRAPPED, null)
            ?: error("Local identity private key missing")
        val raw = unprotectSecretBytes(wrapped)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(raw))
    }

    private fun decodePublicKey(publicKeyB64: String): PublicKey {
        val bytes = decodeB64(publicKeyB64)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun protectSecretBytes(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return "${encodeB64(iv)}:${encodeB64(ciphertext)}"
    }

    private fun unprotectSecretBytes(stored: String): ByteArray {
        val parts = stored.split(":")
        require(parts.size == 2) { "Corrupt wrapped secret" }
        val iv = decodeB64(parts[0])
        val ciphertext = decodeB64(parts[1])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
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
            // Fallback for local JVM/Robolectric tests where AndroidKeyStore isn't mounted
            val fallbackHex = prefs.getString(KEY_FALLBACK_WRAP, null)
            val raw = if (fallbackHex != null) {
                decodeB64(fallbackHex)
            } else {
                ByteArray(32).also {
                    secureRandom.nextBytes(it)
                    prefs.edit().putString(KEY_FALLBACK_WRAP, encodeB64(it)).apply()
                }
            }
            SecretKeySpec(raw, "AES")
        }
    }

    private fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
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

    private fun encodeB64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeB64(input: String): ByteArray =
        Base64.decode(input.trim(), Base64.DEFAULT)

    companion object {
        private const val PREFS_NAME = "veil_identity_keystore_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "veil_keystore_master_v1"
        private const val KEY_PUB = "identity_ec_pub"
        private const val KEY_PRIV_WRAPPED = "identity_ec_priv_wrapped"
        private const val KEY_HANDLE = "identity_handle"
        private const val KEY_FALLBACK_WRAP = "fallback_wrap_key"
    }
}
