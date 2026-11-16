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

object AppUpdateManager {
    const val CURRENT_APP_VERSION = "1.0.0"

    // Target GitHub repository for Anik Sarker
    private const val GITHUB_OWNER = "anik-creates"
    private const val GITHUB_REPO  = "robosoccer-f6x"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    suspend fun checkLatestRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "APEX-Soccerbot-App")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
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
                    return@withContext UpdateInfo(
                        tagName = tagName,
                        releaseName = releaseName,
                        releaseNotes = body,
                        apkDownloadUrl = downloadUrl
                    )
                }
            }
        } catch (_: Exception) {
            // Silently ignore network failures when offline or connected to local ESP AP
        }
        null
    }

    private fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
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
    ) = withContext(Dispatchers.IO) {
        try {
            val url = URL(apkUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.connect()

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                withContext(Dispatchers.Main) {
                    onError("Download failed: Server returned HTTP ${conn.responseCode}")
                }
                return@withContext
            }

            val fileLength = conn.contentLength
            val cacheFile = File(context.cacheDir, "update.apk")
            if (cacheFile.exists()) cacheFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val progress = total.toFloat() / fileLength.toFloat()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }

            // Trigger Installation
            withContext(Dispatchers.Main) {
                installApk(context, cacheFile)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Update failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
