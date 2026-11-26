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

                val remoteVersion = tag.removePrefix("v").removePrefix("V")
                if (!isRemoteNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                    return@use null
                }

                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                var apkName = "Veil-$tag.apk"
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name")
                        val dl = asset.optString("browser_download_url")
                        if (name.endsWith(".apk", ignoreCase = true) && isTrustedGithubUrl(dl)) {
                            apkUrl = dl
                            apkName = sanitizeFileName(name)
                            break
                        }
                    }
                }
                if (apkUrl.isBlank()) return@use null

                ReleaseUpdateInfo(
                    versionTag = tag,
                    normalizedVersion = remoteVersion,
                    releaseNotes = json.optString("body").trim()
                        .ifBlank { "Security improvements, performance enhancements, and bug fixes." },
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName,
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

    fun downloadAndInstallApk(
        release: ReleaseUpdateInfo,
        onStatus: (String) -> Unit
    ): Result<Long> = runCatching {
        require(isTrustedGithubUrl(release.apkDownloadUrl)) {
            "Blocked untrusted APK download source"
        }

        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Unable to access app downloads directory")
        val targetFile = File(downloadsDir, release.apkFileName)
        if (targetFile.exists()) targetFile.delete()

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkDownloadUrl))
            .setTitle("Veil ${release.versionTag}")
            .setDescription("Downloading verified Veil release APK")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                release.apkFileName
            )

        val downloadId = dm.enqueue(request)
        onStatus("Downloading Veil ${release.versionTag}...")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId == downloadId) {
                    runCatching { appContext.unregisterReceiver(this) }
                    if (targetFile.exists() && targetFile.length() > 0L) {
                        onStatus("Download complete. Launching installer...")
                        launchApkInstaller(targetFile)
                    } else {
                        onStatus("APK download did not complete.")
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

    private fun launchApkInstaller(apkFile: File) {
        runCatching {
            val apkUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(installIntent)
        }
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
