package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseUpdateInfo(
    val versionTag: String,
    val normalizedVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val htmlUrl: String
)

data class UpdateDownloadState(
    val isDownloading: Boolean = false,
    val versionTag: String = "",
    val statusText: String = ""
)

class UpdateManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): Result<ReleaseUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Veil-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.code == 404) return@use null
                if (!resp.isSuccessful) error("Update check returned HTTP ${resp.code}")
                val raw = resp.body?.string().orEmpty()
                val json = JSONObject(raw)
                val tag = json.optString("tag_name").trim()
                if (tag.isBlank()) return@use null

                val remoteVersion = tag.removePrefix("v").removePrefix("V").trim()
                if (!isRemoteNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                    return@use null
                }

                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        val dl = asset.optString("browser_download_url")
                        if (name.endsWith(".apk", ignoreCase = true) && isTrustedGithubUrl(dl)) {
                            apkUrl = dl
                            break
                        }
                    }
                }
                if (apkUrl.isBlank()) return@use null

                // Always assign a strict, unique per-version filename (e.g., Veil-v1.0.3.apk)
                val versionedFileName = sanitizeFileName("Veil-v$remoteVersion.apk")

                ReleaseUpdateInfo(
                    versionTag = tag,
                    normalizedVersion = remoteVersion,
                    releaseNotes = json.optString("body").trim()
                        .ifBlank { "Security improvements, performance enhancements, and bug fixes." },
                    apkDownloadUrl = apkUrl,
                    apkFileName = versionedFileName,
                    htmlUrl = json.optString("html_url", RELEASES_PAGE)
                )
            }
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${appContext.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(intent)
        }
    }

    /**
     * Deletes any leftover downloaded Veil APK files whose version matches or is older than
     * the currently running BuildConfig.VERSION_NAME. Called automatically on app startup.
     */
    fun cleanupObsoleteApks() {
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val apkFiles = downloadsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".apk", ignoreCase = true)
        } ?: return

        apkFiles.forEach { file ->
            val extractedVersion = extractVersionFromApkName(file.name)
            val isObsolete = extractedVersion == null ||
                !isRemoteNewer(extractedVersion, BuildConfig.VERSION_NAME)
            if (isObsolete) {
                runCatching { file.delete() }
            }
        }
    }

    /**
     * Deletes all prior downloaded Veil APKs before starting a new release download
     * so DownloadManager never serves a stale cached file or creates duplicate suffixes.
     */
    private fun purgeAllDownloadedApks(downloadsDir: File) {
        downloadsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".apk", ignoreCase = true)
        }?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    fun downloadAndInstallApk(
        release: ReleaseUpdateInfo,
        onStatus: (String) -> Unit,
        onFinished: () -> Unit = {}
    ): Result<Long> = runCatching {
        require(isTrustedGithubUrl(release.apkDownloadUrl)) {
            "Blocked untrusted APK download source."
        }

        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Unable to access app downloads directory.")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        // Remove any older Veil-v*.apk files before saving the exact versioned APK
        purgeAllDownloadedApks(downloadsDir)

        val versionedFileName = sanitizeFileName("Veil-v${release.normalizedVersion}.apk")
        val targetFile = File(downloadsDir, versionedFileName)

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkDownloadUrl))
            .setTitle("Veil ${release.versionTag}")
            .setDescription("Downloading Veil ${release.versionTag} in-place update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                versionedFileName
            )

        val downloadId = dm.enqueue(request)
        onStatus("Downloading Veil ${release.versionTag}...")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId == downloadId) {
                    runCatching { appContext.unregisterReceiver(this) }
                    val succeeded = isDownloadSuccessful(dm, downloadId)
                    onFinished()
                    if (succeeded && targetFile.exists() && targetFile.length() > 0L) {
                        onStatus("Download complete. Launching in-place updater...")
                        val launched = launchApkInstaller(targetFile)
                        if (!launched) {
                            onStatus("Unable to open Android Package Installer for ${targetFile.name}.")
                        }
                    } else {
                        runCatching { if (targetFile.exists()) targetFile.delete() }
                        onStatus("APK download failed. Check your connection and try again.")
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        downloadId
    }

    private fun isDownloadSuccessful(dm: DownloadManager, downloadId: Long): Boolean {
        return runCatching {
            dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                statusIdx >= 0 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL
            } ?: false
        }.getOrDefault(false)
    }

    fun launchApkInstaller(apkFile: File): Boolean {
        return runCatching {
            val apkUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            appContext.startActivity(installIntent)
            true
        }.getOrDefault(false)
    }

    private fun isTrustedGithubUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://github.com/HARI-glitch-creater/Veil-/") ||
            trimmed.startsWith("https://objects.githubusercontent.com/")
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "Veil-update.apk" }

    companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/HARI-glitch-creater/Veil-/releases/latest"
        const val RELEASES_PAGE = "https://github.com/HARI-glitch-creater/Veil-/releases"

        private val VERSION_FILE_REGEX = Regex("(?i)veil[-_]?v?(\\d+(?:\\.\\d+)+).*\\.apk")

        fun extractVersionFromApkName(fileName: String): String? {
            return VERSION_FILE_REGEX.find(fileName)?.groupValues?.getOrNull(1)
        }

        fun isRemoteNewer(remote: String, current: String): Boolean {
            val rParts = remote.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val cParts = current.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }
}
