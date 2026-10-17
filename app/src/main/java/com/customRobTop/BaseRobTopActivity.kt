package com.customRobTop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.customRobTop.contracts.ImportFileActivityContract
import com.customRobTop.contracts.ShareFileActivityContract
import com.geode.launcher.R
import com.geode.launcher.utils.DownloadUtils
import com.geode.launcher.utils.LaunchUtils
import org.cocos2dx.lib.Cocos2dxGLSurfaceView
import org.cocos2dx.lib.Cocos2dxGLSurfaceView.Companion.closeIMEKeyboard
import org.cocos2dx.lib.Cocos2dxGLSurfaceView.Companion.openIMEKeyboard
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.UUID

@Suppress("unused", "UNUSED_PARAMETER")
object BaseRobTopActivity {
    const val KEYBOARD_INPUT_MULTILINE = 1
    const val KEYBOARD_INPUT_NUMERIC = 4
    const val KEYBOARD_INPUT_PASSWORD = 2
    const val LEVEL_EXTENSION: String = ".gdlvl"

    private var isLoaded = false
    var blockBackButton = false
    private var keyboardActive = false
    var curKeyboardInputFlags = 0

    var isPaused = false

    lateinit var me: WeakReference<Activity>
    private var shouldResumeSound = true

    private var pendingExportPaths = emptyList<String>()

    private lateinit var importFileActivityContract: ActivityResultLauncher<Unit>
    private lateinit var shareFileActivityContract: ActivityResultLauncher<String>
    private lateinit var shareFolderActivityContract: ActivityResultLauncher<Uri?>

    fun setCurrentActivity(currentActivity: AppCompatActivity) {
        me = WeakReference(currentActivity)

        importFileActivityContract = currentActivity.registerForActivityResult(ImportFileActivityContract()) { uris ->
            val filePaths = copyPickedFiles(uris)

            Cocos2dxGLSurfaceView.cocos2dxGLSurfaceView.queueEvent {
                JniToCpp.filesPicked(filePaths.toTypedArray())
            }
        }

        shareFileActivityContract = currentActivity.registerForActivityResult(ShareFileActivityContract()) { uri ->
            val currentlyPending = pendingExportPaths
            pendingExportPaths = emptyList()

            val exportPath = currentlyPending.firstOrNull()
            if (exportPath == null || uri == null) {
                Cocos2dxGLSurfaceView.cocos2dxGLSurfaceView.queueEvent {
                    JniToCpp.fileShareFinished(false)
                }
                return@registerForActivityResult
            }

            val result = saveToDocument(exportPath, uri)
            Cocos2dxGLSurfaceView.cocos2dxGLSurfaceView.queueEvent {
                JniToCpp.fileShareFinished(result)
            }
        }

        shareFolderActivityContract = currentActivity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val currentlyPending = pendingExportPaths
            pendingExportPaths = emptyList()

            if (uri == null) {
                Cocos2dxGLSurfaceView.cocos2dxGLSurfaceView.queueEvent {
                    JniToCpp.fileShareFinished(false)
                }
                return@registerForActivityResult
            }

            val result = saveToFolder(currentlyPending, uri) > 0
            Cocos2dxGLSurfaceView.cocos2dxGLSurfaceView.queueEvent {
                JniToCpp.fileShareFinished(result)
            }
        }
    }

    @SuppressLint("HardwareIds")
    @JvmStatic
    fun getUserID(): String {
        // this is how RobTop does it in 2.2, based on the meltdown leaks
        val androidId = Settings.Secure.getString(me.get()?.contentResolver, Settings.Secure.ANDROID_ID)
        return if ("9774d56d682e549c" != androidId) {
            UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
        } else return UUID.randomUUID().toString()
    }

    @JvmStatic
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = me.get()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return true
        }

        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return true
        }

        return false
    }

    @JvmStatic
    fun openURL(url: String) {
        me.get()?.runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                me.get()?.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    me.get(),
                    R.string.no_activity_found,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @JvmStatic
    fun copyToClipboard(str: String) {
        val context = me.get() ?: return
        context.runOnUiThread {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText("text", str)
            )
        }
    }

    @JvmStatic
    fun getClipboardString(): String {
        me.get()?.run {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                return clip.getItemAt(0).coerceToText(this).toString()
            }
        }
        return ""
    }

    @JvmStatic
    fun sendMail(subject: String, body: String, to: String) {
        me.get()?.runOnUiThread {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "message/rfc822"
            i.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            i.putExtra(Intent.EXTRA_SUBJECT, subject)
            i.putExtra(Intent.EXTRA_TEXT, body)
            try {
                me.get()?.startActivity(Intent.createChooser(i, "Send mail..."))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    me.get(),
                    "There are no email clients installed.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @JvmStatic
    fun shouldResumeSound(): Boolean {
        return shouldResumeSound
    }

    @JvmStatic
    fun setKeyboardState(value: Boolean) {
        keyboardActive = value
    }

    @JvmStatic
    fun onToggleKeyboard() {
        me.get()?.runOnUiThread {
            if (keyboardActive) {
                openIMEKeyboard()
            } else {
                closeIMEKeyboard()
            }
        }
    }

    @JvmStatic
    fun loadingFinished() {
        isLoaded = true
    }

    @JvmStatic
    fun getDeviceRefreshRate(): Float {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            me.get()?.display
        } else {
            @Suppress("DEPRECATION")
            me.get()?.windowManager?.defaultDisplay
        }

        return display!!.refreshRate
    }

    // Everyplay doesn't even exist anymore lol
    @JvmStatic
    fun setupEveryplay() {}

    @JvmStatic
    fun isEveryplaySupported(): Boolean {
        return false
    }

    @JvmStatic
    fun isRecordingSupported(): Boolean {
        return false
    }

    @JvmStatic
    fun isRecordingPaused() : Boolean {
        return false
    }

    @JvmStatic
    fun isRecording() : Boolean {
        return false
    }

    @JvmStatic
    fun startRecording() {}

    @JvmStatic
    fun stopRecording() {}

    @JvmStatic
    fun resumeRecording() {}

    @JvmStatic
    fun pauseRecording() {}

    @JvmStatic
    fun playLastRecording() {}

    @JvmStatic
    fun showEveryplay() {}

    @JvmStatic
    fun setEveryplayMetadata(levelID: String, levelName: String) {}

    @JvmStatic
    fun onNativePause() {}

    @JvmStatic
    fun onNativeResume() {}

    // Google Play Games methods
    // for some reason the button is hidden in game
    @JvmStatic
    fun gameServicesIsSignedIn(): Boolean {
        return false
    }

    @JvmStatic
    fun gameServicesSignIn() { }

    @JvmStatic
    fun gameServicesSignOut() { }

    @JvmStatic
    fun unlockAchievement(id: String) {}

    @JvmStatic
    fun showAchievements() {}

    // advertisements stuff, useless for full version
    @JvmStatic
    fun enableBanner() {}

    @JvmStatic
    fun disableBanner() {}

    @JvmStatic
    fun showInterstitial() {}

    @JvmStatic
    fun cacheInterstitial() {}

    @JvmStatic
    fun hasCachedInterstitial(): Boolean {
        return false
    }

    @JvmStatic
    fun showRewardedVideo() {}

    @JvmStatic
    fun cacheRewardedVideo() {}

    @JvmStatic
    fun hasCachedRewardedVideo(): Boolean {
        return false
    }

    @JvmStatic
    fun queueRefreshBanner() {}

    @JvmStatic
    fun enableBannerNoRefresh() {}

    // for in game rate buttons, may implement in the future depending on how lazy I am
    // (requires Play Store library)
    @JvmStatic
    fun tryShowRateDialog(appName: String) {}

    @JvmStatic
    fun openAppPage() {}

    @JvmStatic
    @Deprecated(
        message = "This method is not found on newer versions of the game.",
        level = DeprecationLevel.HIDDEN
    )
    fun logEvent(event: String) {}

    @JvmStatic
    fun setKeyboardInputFlags(flags: Int) {
        curKeyboardInputFlags = flags
    }

    @JvmStatic
    fun pickImportFiles() {
        try {
            importFileActivityContract.launch(Unit)
        } catch (_: ActivityNotFoundException) {
            me.get()?.apply {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.no_activity_found,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun copyPickedFiles(uris: List<Uri>): List<String> {
        val me = me.get() ?: return emptyList()

        val importsDir = File(LaunchUtils.getSaveDirectory(me), "imports")
        importsDir.mkdirs()

        return uris.mapIndexedNotNull { index, uri ->
            val displayName = displayName(uri)
                ?: "import_$index$LEVEL_EXTENSION"
            if (!displayName.endsWith(LEVEL_EXTENSION)) {
                return@mapIndexedNotNull null
            }

            var outputFile = File(importsDir, displayName)
            var counter = 2
            while (outputFile.exists()) {
                outputFile = File(importsDir, "${counter}_$displayName")
                counter++
            }

            try {
                me.contentResolver.openInputStream(uri)?.use { input ->
                    outputFile.outputStream().use { output ->
                        DownloadUtils.copyFile(input, output)
                    }

                    outputFile.absolutePath
                }
            } catch (_: Exception) {
                outputFile.delete()
                null
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        me.get()?.contentResolver?.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursorQuery ->
            if (cursorQuery.moveToFirst()) {
                val displayName = cursorQuery.getString(0)
                if (displayName.isNotEmpty()) {
                    return File(displayName).name
                }
            }
        }

        return null
    }

    @JvmStatic
    fun shareFiles(files: Array<String>) {
        pendingExportPaths = files.toList()

        try {
            if (files.size == 1) {
                shareFileActivityContract.launch(files.first())
                return
            } else if (files.size > 1) {
                shareFolderActivityContract.launch(null)
                return
            }
        } catch (_: ActivityNotFoundException) {
            me.get()?.apply {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.no_activity_found,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            Cocos2dxGLSurfaceView.cocos2dxGLSurfaceView.queueEvent {
                JniToCpp.fileShareFinished(false)
            }
        }

    }

    private fun saveToFolder(exportPaths: List<String>, treeUri: Uri): Int {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

        val me = me.get() ?: return 0

        return exportPaths.count { path ->
            val filename = File(path).name
            val documentUri = DocumentsContract.createDocument(me.contentResolver, uri, "application/octet-stream", filename)
                ?: return@count false

            saveToDocument(path, documentUri)
        }
    }

    private fun saveToDocument(exportPath: String, uri: Uri): Boolean {
        try {
            FileInputStream(exportPath).use { inputStream ->
                openOutput(uri).use { outputStream ->
                    if (outputStream != null) {
                        DownloadUtils.copyFile(inputStream, outputStream)
                    }
                }
            }

            ensureLevelExtension(uri)
            return true
        } catch (_: IOException) {
            return false
        }
    }

    private fun openOutput(uri: Uri): OutputStream? {
        val me = me.get() ?: return null
        return try {
            me.contentResolver.openOutputStream(uri, "wt")
        } catch (_: IOException) {
            me.contentResolver.openOutputStream(uri)
        }
    }

    private fun ensureLevelExtension(uri: Uri) {
        val displayName = displayName(uri)
        if (displayName == null || displayName.endsWith(LEVEL_EXTENSION)) {
            return
        }
        val me = me.get() ?: return

        val extensionIdx = displayName.lastIndexOf(".gdlvl.")
        val substring = if (extensionIdx < 0) {
            displayName + LEVEL_EXTENSION
        } else {
            displayName.substring(0, extensionIdx + 6)
        }

        try {
            DocumentsContract.renameDocument(me.contentResolver, uri, substring)
        } catch (_: IOException) {
        }
    }
}