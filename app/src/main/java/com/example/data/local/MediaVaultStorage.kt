package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

data class StoredMediaFile(
    val file: File,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

class MediaVaultStorage(private val context: Context) {

    private val vaultDir: File by lazy {
        File(context.filesDir, "vault_media").apply {
            if (!exists()) mkdirs()
        }
    }

    fun createVoiceNoteTarget(): File {
        val file = File(vaultDir, "voice_${UUID.randomUUID()}.m4a")
        ensureConfined(file)
        return file
    }

    fun copyFromUri(uri: Uri, fallbackName: String = "attachment"): StoredMediaFile? {
        return runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val originalName = queryDisplayName(uri) ?: buildFallbackName(fallbackName, mime)
            val safeExt = originalName.substringAfterLast('.', "")
                .filter { it.isLetterOrDigit() }
                .take(8)
                .ifBlank { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty() }

            val targetName = if (safeExt.isNotBlank()) {
                "${UUID.randomUUID()}.$safeExt"
            } else {
                UUID.randomUUID().toString()
            }
            val destFile = File(vaultDir, targetName)
            ensureConfined(destFile)

            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            StoredMediaFile(
                file = destFile,
                displayName = sanitizeDisplayName(originalName),
                mimeType = mime,
                sizeBytes = destFile.length()
            )
        }.getOrNull()
    }

    fun saveDecryptedMedia(
        bytes: ByteArray,
        displayName: String,
        mimeType: String
    ): File {
        val ext = displayName.substringAfterLast('.', "")
            .filter { it.isLetterOrDigit() }
            .take(8)
            .ifBlank { MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType).orEmpty() }
        val fileName = if (ext.isNotBlank()) "${UUID.randomUUID()}.$ext" else UUID.randomUUID().toString()
        val destFile = File(vaultDir, fileName)
        ensureConfined(destFile)
        destFile.writeBytes(bytes)
        return destFile
    }

    fun getFileProviderUri(path: String): Uri? {
        val file = File(path)
        if (!file.exists()) return null
        ensureConfined(file)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun readTextPreview(path: String, maxBytes: Int = 64 * 1024): String? {
        return runCatching {
            val file = File(path)
            if (!file.exists()) return null
            ensureConfined(file)
            val len = minOf(file.length(), maxBytes.toLong()).toInt()
            val buffer = ByteArray(len)
            file.inputStream().use { it.read(buffer) }
            String(buffer, Charsets.UTF_8)
        }.getOrNull()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    private fun buildFallbackName(prefix: String, mime: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        return if (!ext.isNullOrBlank()) "$prefix.$ext" else prefix
    }

    private fun sanitizeDisplayName(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "file" }
    }

    private fun ensureConfined(file: File) {
        val baseCanonical = vaultDir.canonicalPath
        val targetCanonical = file.canonicalPath
        require(targetCanonical.startsWith(baseCanonical + File.separator)) {
            "File path escapes app-private media vault"
        }
    }
}
