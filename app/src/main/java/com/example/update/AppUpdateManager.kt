package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.tts.TtsDiagnosticLogger
import com.example.tts.LogEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val hasUpdate: Boolean = false,
    val isMandatory: Boolean = false,
    val latestVersionCode: Int = BuildConfig.VERSION_CODE,
    val latestVersionName: String = BuildConfig.VERSION_NAME,
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val downloadProgressPercent: Int = 0,
    val isDownloading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * In-App Remote Self-Update Manager (OTA Updates without Play Store / App Store)
 * Performs remote version checks, background APK downloading, and automatic package installation triggers.
 */
class AppUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _updateState = MutableStateFlow(AppUpdateInfo())
    val updateState: StateFlow<AppUpdateInfo> = _updateState.asStateFlow()

    private var activeDownloadId: Long = -1L

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id == activeDownloadId && activeDownloadId != -1L) {
                _updateState.value = _updateState.value.copy(
                    isDownloading = false,
                    downloadProgressPercent = 100
                )
                triggerApkInstallation()
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(downloadReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks remote server / GitHub releases for new version updates.
     */
    fun checkForUpdates(remoteVersionUrl: String = DEFAULT_VERSION_CHECK_URL) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(remoteVersionUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"

                if (connection.responseCode == 200) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonText)

                    val remoteCode = json.optInt("versionCode", BuildConfig.VERSION_CODE)
                    val remoteName = json.optString("versionName", BuildConfig.VERSION_NAME)
                    val apkUrl = json.optString("apkUrl", "")
                    val isMandatory = json.optBoolean("isMandatory", false)
                    val releaseNotes = json.optString("releaseNotes", "Performance improvements & neural voice updates.")

                    val currentCode = BuildConfig.VERSION_CODE
                    val hasUpdate = remoteCode > currentCode

                    _updateState.value = AppUpdateInfo(
                        hasUpdate = hasUpdate,
                        isMandatory = isMandatory,
                        latestVersionCode = remoteCode,
                        latestVersionName = remoteName,
                        apkUrl = apkUrl,
                        releaseNotes = releaseNotes
                    )

                    TtsDiagnosticLogger.log(
                        eventType = LogEventType.TTS_EVENT,
                        message = "Remote update check: current=$currentCode, latest=$remoteCode, hasUpdate=$hasUpdate"
                    )
                }
            } catch (e: Exception) {
                TtsDiagnosticLogger.log(
                    eventType = LogEventType.TTS_EVENT,
                    message = "Remote update check exception: ${e.message}",
                    isError = true
                )
            }
        }
    }

    /**
     * Downloads latest APK via Android System DownloadManager.
     */
    fun startApkDownload() {
        val info = _updateState.value
        if (info.apkUrl.isBlank()) return

        try {
            val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MaskedD_update_${info.latestVersionCode}.apk")
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
                setTitle("MaskedD AI Reader Update v${info.latestVersionName}")
                setDescription("Downloading mandatory application update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            activeDownloadId = manager.enqueue(request)

            _updateState.value = _updateState.value.copy(
                isDownloading = true,
                downloadProgressPercent = 10
            )

        } catch (e: Exception) {
            _updateState.value = _updateState.value.copy(
                isDownloading = false,
                errorMessage = "Download failed: ${e.message}"
            )
        }
    }

    /**
     * Launches Android PackageInstaller Intent via FileProvider.
     */
    fun triggerApkInstallation() {
        val info = _updateState.value
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MaskedD_update_${info.latestVersionCode}.apk")

        if (!apkFile.exists()) {
            _updateState.value = _updateState.value.copy(errorMessage = "Downloaded APK missing.")
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)

        } catch (e: Exception) {
            _updateState.value = _updateState.value.copy(errorMessage = "Install trigger failed: ${e.message}")
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
    }

    companion object {
        const val DEFAULT_VERSION_CHECK_URL = "https://raw.githubusercontent.com/mdtbmw/maskedd/main/version.json"
    }
}
