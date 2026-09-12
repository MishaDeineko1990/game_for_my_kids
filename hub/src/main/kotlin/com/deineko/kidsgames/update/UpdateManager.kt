package com.deineko.kidsgames.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.deineko.kidsgames.BuildConfig
import com.deineko.kidsgames.catalog.HubUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    data object UpToDate : UpdateState()
    data class Available(val info: HubUpdateInfo) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/** Checks the repo catalog for a newer hub build, downloads the APK, and hands it to the
 *  system package installer. Android always requires the user to tap "Install" on that final
 *  system screen for a sideloaded app -- there is no way to skip that without device-owner/root,
 *  so "automatic" here means auto-check + auto-download, not a fully silent install. */
class UpdateManager(private val context: Context) {

    fun isNewer(info: HubUpdateInfo): Boolean =
        info.apkUrl != null && info.versionCode > BuildConfig.VERSION_CODE

    suspend fun download(info: HubUpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val url = requireNotNull(info.apkUrl) { "No APK URL in update info" }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "kids-games-${info.versionCode}.apk")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val total = connection.contentLength
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var downloaded = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) onProgress((downloaded * 100L / total).toInt())
                }
            }
        }
        target
    }

    fun promptInstall(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
