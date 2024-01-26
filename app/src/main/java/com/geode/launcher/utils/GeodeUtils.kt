package com.geode.launcher.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.geode.launcher.GeometryDashActivity
import com.geode.launcher.activityresult.GeodeOpenFileActivityResult
import com.geode.launcher.activityresult.GeodeOpenFilesActivityResult
import com.geode.launcher.activityresult.GeodeSaveFileActivityResult
import java.io.File
import java.lang.ref.WeakReference
import kotlin.system.exitProcess

@Suppress("unused")
object GeodeUtils {
    private lateinit var activity: WeakReference<AppCompatActivity>
    private lateinit var openFileResultLauncher: ActivityResultLauncher<GeodeOpenFileActivityResult.OpenFileParams>
    private lateinit var openDirectoryResultLauncher: ActivityResultLauncher<Uri?>
    private lateinit var openFilesResultLauncher: ActivityResultLauncher<GeodeOpenFilesActivityResult.OpenFileParams>
    private lateinit var saveFileResultLauncher: ActivityResultLauncher<GeodeSaveFileActivityResult.SaveFileParams>

    fun setContext(activity: AppCompatActivity) {
        this.activity = WeakReference(activity)
        openFileResultLauncher = activity.registerForActivityResult(GeodeOpenFileActivityResult()) { uri ->
            if (uri != null) {
                val path = FileUtils.getRealPathFromURI(activity, uri)
                if (path != null) {
                    selectFileCallback(path)
                    return@registerForActivityResult
                }
            }
            failedCallback()
        }
        openDirectoryResultLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it != null) {
                val path = FileUtils.getRealPathFromURI(activity, it)
                if (path != null)
                    selectFileCallback(path)
                return@registerForActivityResult
            }
            failedCallback()
        }
        openFilesResultLauncher = activity.registerForActivityResult(GeodeOpenFilesActivityResult()) { result ->
            if (result.isEmpty()) {
                failedCallback()
                return@registerForActivityResult
            }
            val paths: Array<String> = Array(result.size) {"n = $it"}
            for (i in result.indices) {
                val path = FileUtils.getRealPathFromURI(activity, result[i])
                if (path != null) {
                    paths[i] = path
                }
            }
            selectFilesCallback(paths)
            return@registerForActivityResult
        }
        saveFileResultLauncher = activity.registerForActivityResult(GeodeSaveFileActivityResult()) { uri ->
            if (uri != null) {
                val path = FileUtils.getRealPathFromURI(activity, uri)
                if (path != null) {
                    selectFileCallback(path)
                    return@registerForActivityResult
                }
            }
            failedCallback()
        }
    }

    @JvmStatic
    fun getLogcatCrashBuffer(): String {
        return try {
            val logcatProcess = Runtime.getRuntime().exec("logcat -v brief -b crash -d")

            logcatProcess.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.e("Geode", "Failed to get logcat crash buffer", e)
            ""
        }
    }

    @JvmStatic
    fun writeClipboard(text: String) {
        activity.get()?.run {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Geode", text)
            manager.setPrimaryClip(clip)
        }
    }

    @JvmStatic
    fun readClipboard(): String {
        activity.get()?.run {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                return clip.getItemAt(0).text.toString()
            }
        }
        return ""
    }

    @JvmStatic
    fun restartGame() {
        activity.get()?.run {
            packageManager.getLaunchIntentForPackage(packageName)?.also {
                val mainIntent = Intent.makeRestartActivityTask(it.component)
                mainIntent.putExtra("restarted", true)
                startActivity(mainIntent)
                exitProcess(0)
            }
        }
    }

    // TODO As of now this is unused
    @JvmStatic
    fun openFolder(path: String): Boolean {
        activity.get()?.run {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            DocumentFile.fromFile(File(path)).also {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it.uri)
            }
            intent.setType("*/*")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return true
            }
        }
        return false
    }

    @Suppress("KotlinJniMissingFunction")
    external fun selectFileCallback(path: String)

    @Suppress("KotlinJniMissingFunction")
    external fun selectFilesCallback(paths: Array<String>)

    @Suppress("KotlinJniMissingFunction")
    external fun failedCallback()

    @JvmStatic
    fun selectFile(path: String): Boolean {
        activity.get()?.run {
            var uri: Uri?
            DocumentFile.fromFile(File(path)).also {
                uri = it.uri
            }
            try {
                openFileResultLauncher.launch(GeodeOpenFileActivityResult.OpenFileParams(arrayOf("*/*"), uri))
                return true
            } catch (e: ActivityNotFoundException) {
                return false
            }
        }
        return false
    }

    @JvmStatic
    fun selectFiles(path: String): Boolean {
        activity.get()?.run {
            var uri: Uri?
            DocumentFile.fromFile(File(path)).also {
                uri = it.uri
            }
            try {
                openFilesResultLauncher.launch(GeodeOpenFilesActivityResult.OpenFileParams(arrayOf("*/*"), uri))
                return true
            } catch (e: ActivityNotFoundException) {
                return false
            }
        }
        return false
    }

    @JvmStatic
    fun selectFolder(path: String): Boolean {
        activity.get()?.run {
            var uri: Uri?
            DocumentFile.fromFile(File(path)).also {
                uri = it.uri
            }
            try {
                openDirectoryResultLauncher.launch(uri)
                return true
            } catch (e: ActivityNotFoundException) {
                return false
            }
        }
        return false
    }

    @JvmStatic
    fun createFile(path: String): Boolean {
        activity.get()?.run {
            var uri: Uri?
            DocumentFile.fromFile(File(path)).also {
                uri = it.uri
            }

            try {
                saveFileResultLauncher.launch(GeodeSaveFileActivityResult.SaveFileParams(null, uri))
                return true
            } catch (e: ActivityNotFoundException) {
                return false
            }
        }
        return false
    }

    @JvmStatic
    fun getBaseDirectory(): String {
        val activity = activity.get()!!
        return LaunchUtils.getBaseDirectory(activity).canonicalPath
    }

    @JvmStatic
    fun getInternalDirectory(): String {
        val activity = activity.get()!!
        return activity.filesDir.canonicalPath
    }

    private val gameVersionMap = mapOf(
        37L to "2.200",
        38L to "2.205"
    )

    @JvmStatic
    fun getGameVersion(): String {
        // these versions should be aligned to windows releases, not what android says
        activity.get()?.run {
            val versionCode = LaunchUtils.getGeometryDashVersionCode(packageManager)
            return gameVersionMap[versionCode] ?: LaunchUtils.getGeometryDashVersionString(packageManager)
        }

        return ""
    }

    fun isGeodeUri(uri: Uri): Boolean {
        return "com.geode.launcher.user" == uri.authority
    }

    @JvmStatic
    fun getPermissionStatus(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            GeometryDashActivity.instance?.applicationContext ?: return false,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun requestPermission(permission: String) {
        if (!getPermissionStatus(permission)) {
            ActivityCompat.requestPermissions(
                GeometryDashActivity.instance!!,
                arrayOf(permission),
                12345
            )
        }
    }
}