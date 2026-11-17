package com.anik.f6xcontroller.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String
)

sealed class CheckUpdateResult {
    data class UpdateAvailable(val info: UpdateInfo) : CheckUpdateResult()
    data class UpToDate(val currentVersion: String) : CheckUpdateResult()
    data class Error(val message: String) : CheckUpdateResult()
}

object AppUpdateManager {
    const val CURRENT_APP_VERSION = "1.0.6"
    const val DEFAULT_REPO = "anik-creates/robosoccer-f6x"

    suspend fun checkLatestRelease(repo: String = DEFAULT_REPO): CheckUpdateResult = withContext(Dispatchers.IO) {
        val targetRepo = if (repo.contains("/")) repo.trim() else "anik-creates/$repo"
        val candidates = listOf(targetRepo, "aniksarker/robosoccer-f6x")

        var lastError = "Unable to connect to GitHub"

        for (candidate in candidates.distinct()) {
            // 1. First attempt: Direct GitHub Web 302 Redirect (Bypasses API 403 rate limits completely!)
            try {
                val webUrl = "https://github.com/$candidate/releases/latest"
                val webConn = URL(webUrl).openConnection() as HttpURLConnection
                webConn.instanceFollowRedirects = false
                webConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
                webConn.connectTimeout = 6000
                webConn.readTimeout = 6000
                val redirect = webConn.getHeaderField("Location")
                if (!redirect.isNullOrBlank() && redirect.contains("/releases/tag/")) {
                    val tag = redirect.substringAfterLast("/").trim()
                    val apkUrl = "https://github.com/$candidate/releases/download/$tag/APEX_F6X_Controller_$tag.apk"
                    if (isNewerVersion(tag, CURRENT_APP_VERSION)) {
                        return@withContext CheckUpdateResult.UpdateAvailable(
                            UpdateInfo(
                                tagName = tag,
                                releaseName = "APEX-F6X $tag",
                                releaseNotes = "Latest release from $candidate",
                                apkDownloadUrl = apkUrl
                            )
                        )
                    } else {
                        return@withContext CheckUpdateResult.UpToDate(CURRENT_APP_VERSION)
                    }
                }
            } catch (e: Exception) {
                lastError = "Redirect check error: ${e.message}"
            }

            // 2. Second attempt: GitHub REST API fallback
            try {
                val apiUrl = "https://api.github.com/repos/$candidate/releases/latest"
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                val code = conn.responseCode
                if (code == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "")
                    val releaseName = json.optString("name", tagName)
                    val body = json.optString("body", "")

                    // Find .apk asset
                    var downloadUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (isNewerVersion(tagName, CURRENT_APP_VERSION) && downloadUrl.isNotEmpty()) {
                        return@withContext CheckUpdateResult.UpdateAvailable(
                            UpdateInfo(
                                tagName = tagName,
                                releaseName = releaseName,
                                releaseNotes = body,
                                apkDownloadUrl = downloadUrl
                            )
                        )
                    } else {
                        return@withContext CheckUpdateResult.UpToDate(CURRENT_APP_VERSION)
                    }
                } else if (code == 404) {
                    lastError = "Repository or release not found: $candidate"
                } else {
                    lastError = "GitHub returned HTTP $code"
                }
            } catch (e: Exception) {
                lastError = "Network error: ${e.message ?: "Connection failed"}. If connected to Robot Wi-Fi (No Internet), temporarily switch to Mobile Data to check for updates."
            }
        }

        CheckUpdateResult.Error(lastError)
    }

    fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val cleanRemote = remoteTag.removePrefix("v").removePrefix("V").trim()
        val cleanLocal  = localVersion.removePrefix("v").removePrefix("V").trim()
        if (cleanRemote.isEmpty()) return false
        if (cleanRemote == cleanLocal) return false

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts  = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val url = URL(apkUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.connect()

            if (conn.responseCode !in 200..299) {
                // If 302/301 redirect
                if (conn.responseCode in 300..399) {
                    val redirectUrl = conn.getHeaderField("Location")
                    if (!redirectUrl.isNullOrEmpty()) {
                        downloadAndInstallApk(context, redirectUrl, onProgress, onError)
                        return@withContext
                    }
                }
                withContext(Dispatchers.Main) {
                    onError("Download HTTP error code: ${conn.responseCode}")
                }
                return@withContext
            }

            val totalLength = conn.contentLength.toFloat()
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val apkFile = File(updatesDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalLength > 0) {
                            val progress = (totalRead / totalLength).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            // Launch package installer on Main Thread
            withContext(Dispatchers.Main) {
                installApk(context, apkFile, onError)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Failed to download APK: ${e.message}")
            }
        }
    }

    fun openReleasesInBrowser(context: Context, repo: String = DEFAULT_REPO) {
        try {
            val targetRepo = if (repo.contains("/")) repo.trim() else "anik-creates/$repo"
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/$targetRepo/releases")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {}
    }

    private fun installApk(context: Context, apkFile: File, onError: (String) -> Unit) {
        try {
            val authority = "${context.packageName}.provider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            onError("Cannot launch package installer: ${e.message}")
        }
    }
}
