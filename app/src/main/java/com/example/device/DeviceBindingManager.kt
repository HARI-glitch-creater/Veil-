package com.example.device

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import org.json.JSONObject
import java.security.MessageDigest

data class DeviceRecoveryAnchor(
    val deviceBindingId: String,
    val publicKey: String,
    val username: String,
    val motto: String,
    val gender: String,
    val boundAt: Long
)

class DeviceBindingManager(private val context: Context) {

    @SuppressLint("HardwareIds")
    fun computeDeviceBindingId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val raw = listOf(
            androidId,
            Build.MANUFACTURER.orEmpty(),
            Build.BRAND.orEmpty(),
            Build.MODEL.orEmpty(),
            Build.BOARD.orEmpty(),
            Build.HARDWARE.orEmpty(),
            Build.DEVICE.orEmpty()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun registerLocalAccountBinding(
        deviceBindingId: String,
        username: String,
        publicKey: String,
        motto: String,
        gender: String
    ) {
        // 1. Register local AccountManager entry (com.veil.app.account)
        runCatching {
            val am = AccountManager.get(context)
            val existing = am.getAccountsByType(ACCOUNT_TYPE)
            val account = existing.firstOrNull() ?: Account(username.ifBlank { "Veil" }, ACCOUNT_TYPE).also {
                am.addAccountExplicitly(it, null, null)
            }
            am.setUserData(account, KEY_DEVICE_ID, deviceBindingId)
            am.setUserData(account, KEY_PUB_KEY, publicKey)
            am.setUserData(account, KEY_USERNAME, username)
            am.setUserData(account, KEY_MOTTO, motto)
            am.setUserData(account, KEY_GENDER, gender)
        }

        // 2. Write scoped MediaStore recovery anchor (API 29+) so reinstalls on this phone
        // immediately detect the prior binding without requiring storage permissions.
        writeMediaStoreAnchor(
            DeviceRecoveryAnchor(
                deviceBindingId = deviceBindingId,
                publicKey = publicKey,
                username = username,
                motto = motto,
                gender = gender,
                boundAt = System.currentTimeMillis()
            )
        )
    }

    fun readExistingRecoveryAnchor(): DeviceRecoveryAnchor? {
        val expectedDeviceId = computeDeviceBindingId()

        // Check AccountManager first
        runCatching {
            val am = AccountManager.get(context)
            val acc = am.getAccountsByType(ACCOUNT_TYPE).firstOrNull()
            if (acc != null) {
                val id = am.getUserData(acc, KEY_DEVICE_ID).orEmpty()
                val username = am.getUserData(acc, KEY_USERNAME).orEmpty()
                val pub = am.getUserData(acc, KEY_PUB_KEY).orEmpty()
                if (id == expectedDeviceId && username.isNotBlank()) {
                    return DeviceRecoveryAnchor(
                        deviceBindingId = id,
                        publicKey = pub,
                        username = username,
                        motto = am.getUserData(acc, KEY_MOTTO).orEmpty(),
                        gender = am.getUserData(acc, KEY_GENDER).orEmpty().ifBlank { "Prefer not to say" },
                        boundAt = System.currentTimeMillis()
                    )
                }
            }
        }

        // Fallback to MediaStore scoped anchor (survives app uninstall on Android 10+)
        return readMediaStoreAnchor(expectedDeviceId)
    }

    private fun writeMediaStoreAnchor(anchor: DeviceRecoveryAnchor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = context.contentResolver
            val fileName = anchorFileName(anchor.deviceBindingId)
            val json = JSONObject().apply {
                put("device_binding_id", anchor.deviceBindingId)
                put("public_key", anchor.publicKey)
                put("username", anchor.username)
                put("motto", anchor.motto)
                put("gender", anchor.gender)
                put("bound_at", anchor.boundAt)
            }.toString()

            val existingUri = findMediaStoreAnchorUri(fileName)
            val targetUri = existingUri ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VeilAnchor")
                }
            ) ?: return

            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun readMediaStoreAnchor(expectedDeviceId: String): DeviceRecoveryAnchor? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val uri = findMediaStoreAnchorUri(anchorFileName(expectedDeviceId)) ?: return null
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return null
            val obj = JSONObject(raw)
            val id = obj.optString("device_binding_id")
            val username = obj.optString("username")
            if (id != expectedDeviceId || username.isBlank()) return null
            DeviceRecoveryAnchor(
                deviceBindingId = id,
                publicKey = obj.optString("public_key"),
                username = username,
                motto = obj.optString("motto"),
                gender = obj.optString("gender", "Prefer not to say"),
                boundAt = obj.optLong("bound_at", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    private fun findMediaStoreAnchorUri(fileName: String): android.net.Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        return context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            } else null
        }
    }

    private fun anchorFileName(deviceBindingId: String): String =
        "veil_anchor_${deviceBindingId.take(12)}.json"

    companion object {
        const val ACCOUNT_TYPE = "com.veil.app.account"
        private const val KEY_DEVICE_ID = "device_binding_id"
        private const val KEY_PUB_KEY = "public_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_MOTTO = "motto"
        private const val KEY_GENDER = "gender"
    }
}
