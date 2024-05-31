package com.geode.launcher.utils

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.geode.launcher.BuildConfig
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.activityresult.GeodeOpenFileActivityResult
import com.geode.launcher.activityresult.GeodeOpenFilesActivityResult
import com.geode.launcher.activityresult.GeodeSaveFileActivityResult
import java.io.File
import java.lang.ref.WeakReference
import kotlin.system.exitProcess

@Keep
@Suppress("unused", "KotlinJniMissingFunction")
object GeodeUtils {
    private lateinit var activity: WeakReference<AppCompatActivity>
    private lateinit var openFileResultLauncher: ActivityResultLauncher<GeodeOpenFileActivityResult.OpenFileParams>
    private lateinit var openDirectoryResultLauncher: ActivityResultLauncher<Uri?>
    private lateinit var openFilesResultLauncher: ActivityResultLauncher<GeodeOpenFilesActivityResult.OpenFileParams>
    private lateinit var saveFileResultLauncher: ActivityResultLauncher<GeodeSaveFileActivityResult.SaveFileParams>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var internalRequestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var internalRequestAllFilesLauncher: ActivityResultLauncher<Intent>

    private var afterRequestPermissions: (() -> Unit)? = null
    private var afterRequestPermissionsFailure: (() -> Unit)? = null

    fun setContext(activity: AppCompatActivity) {
        this.activity = WeakReference(activity)
        openFileResultLauncher = activity.registerForActivityResult(GeodeOpenFileActivityResult()) { uri ->
            if (uri != null) {
                val path = FileUtils.getRealPathFromURI(activity, uri)
                if (path != null) {
                    selectFileCallback(path)
                    return@registerForActivityResult
                }

                Toast.makeText(activity, R.string.file_select_error, Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(activity, R.string.no_file_selected, Toast.LENGTH_SHORT)
                    .show()
            }

            failedCallback()
        }
        openDirectoryResultLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            if (it != null) {
                val path = FileUtils.getRealPathFromURI(activity, it)
                if (path != null) {
                    selectFileCallback(path)
                    return@registerForActivityResult
                }

                Toast.makeText(activity, R.string.file_select_error, Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(activity, R.string.no_file_selected, Toast.LENGTH_SHORT)
                    .show()
            }
            failedCallback()
        }
        openFilesResultLauncher = activity.registerForActivityResult(GeodeOpenFilesActivityResult()) { result ->
            if (result.isEmpty()) {
                Toast.makeText(activity, R.string.no_file_selected, Toast.LENGTH_SHORT)
                    .show()

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

                Toast.makeText(activity, R.string.file_select_error, Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(activity, R.string.no_file_selected, Toast.LENGTH_SHORT)
                    .show()
            }
            failedCallback()
        }
        requestPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            permissionCallback(isGranted)
        }

        internalRequestPermissionsLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val isGranted = permissions.values.all { it }
            if (isGranted) {
                afterRequestPermissions?.invoke()
            } else {
                Toast.makeText(activity, R.string.missing_permissions, Toast.LENGTH_SHORT)
                    .show()

                afterRequestPermissionsFailure?.invoke()
            }
        }

        // only necessary on newer android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            internalRequestAllFilesLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                if (Environment.isExternalStorageManager()) {
                    afterRequestPermissions?.invoke()
                } else {
                    Toast.makeText(activity, R.string.missing_permissions, Toast.LENGTH_SHORT)
                        .show()

                    afterRequestPermissionsFailure?.invoke()
                }
            }
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
        val context = activity.get()!!

        val pathFile = File(path)
        val baseDirectory = LaunchUtils.getBaseDirectory(context)
        val isInternalPath = pathFile.startsWith(baseDirectory)

        val intent = if (isInternalPath) {
            // TODO: figure out how to get this to point to the path it should be pointing at
            // (the best i got was pointing at a file)
            // val relativePath = pathFile.relativeTo(baseDirectory)

            Intent(Intent.ACTION_VIEW).apply {
                data = DocumentsContract.buildRootUri(
                    "${context.packageName}.user", UserDirectoryProvider.ROOT
                )

                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addCategory(Intent.CATEGORY_OPENABLE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val documentFile = DocumentFile.fromFile(File(path))
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentFile.uri)
                }

                type = "*/*"
            }
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private external fun selectFileCallback(path: String)

    private external fun selectFilesCallback(paths: Array<String>)

    private external fun failedCallback()

    private fun checkForFilePermissions(onSuccess: () -> Unit, onFailure: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                onSuccess()
            } else {
                val intent = Intent(
                    ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                )

                internalRequestAllFilesLauncher.launch(intent)
                afterRequestPermissions = onSuccess
                afterRequestPermissionsFailure = onFailure
            }
        } else {
            val permissions = listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            val context = activity.get()!!

            val needsPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

            if (needsPermissions.isNotEmpty()) {
                internalRequestPermissionsLauncher.launch(needsPermissions.toTypedArray())
                afterRequestPermissions = onSuccess
                afterRequestPermissionsFailure = onFailure
            } else {
                onSuccess()
            }
        }
    }

    @JvmStatic
    fun selectFile(path: String): Boolean {
        var uri: Uri?
        DocumentFile.fromFile(File(path)).also {
            uri = it.uri
        }

        return try {
            checkForFilePermissions(
                onSuccess = {
                    openFileResultLauncher.launch(GeodeOpenFileActivityResult.OpenFileParams(arrayOf("*/*"), uri))
                },
                onFailure = { failedCallback() }
            )

            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun selectFiles(path: String): Boolean {
        var uri: Uri?
        DocumentFile.fromFile(File(path)).also {
            uri = it.uri
        }

        return try {
            checkForFilePermissions(
                onSuccess = {
                    openFilesResultLauncher.launch(GeodeOpenFilesActivityResult.OpenFileParams(arrayOf("*/*"), uri))
                },
                onFailure = { failedCallback() }
            )

            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun selectFolder(path: String): Boolean {
        var uri: Uri?
        DocumentFile.fromFile(File(path)).also {
            uri = it.uri
        }

        return try {
            checkForFilePermissions(
                onSuccess = {
                    openDirectoryResultLauncher.launch(uri)
                },
                onFailure = { failedCallback() }
            )

            true
        } catch (e: ActivityNotFoundException) {
            false
        }

    }

    @JvmStatic
    fun createFile(path: String): Boolean {
        val initialPath = File(path)

        return try {
            checkForFilePermissions(
                onSuccess = {
                    saveFileResultLauncher.launch(GeodeSaveFileActivityResult.SaveFileParams(null, initialPath))
                },
                onFailure = { failedCallback() }
            )

            true
        } catch (e: ActivityNotFoundException) {
            false
        }
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
            return GamePackageUtils.getUnifiedVersionName(packageManager)
        }

        return ""
    }

    fun isGeodeUri(uri: Uri): Boolean {
        return "com.geode.launcher.user" == uri.authority
    }

    private const val INTERNAL_PERMISSION_PREFIX = "geode.permission_internal"
    private const val MANAGE_ALL_FILES = "${INTERNAL_PERMISSION_PREFIX}.MANAGE_ALL_FILES"

    @JvmStatic
    fun getPermissionStatus(permission: String): Boolean {
        val context = activity.get() ?: return false

        return when (permission) {
            MANAGE_ALL_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                val permissions = listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                return permissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
            }
            else -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @JvmStatic
    fun requestPermission(permission: String) {
        if (permission == MANAGE_ALL_FILES) {
            // this function handles already having perms for us
            checkForFilePermissions(onSuccess = {
                permissionCallback(true)
            }, onFailure = {
                permissionCallback(false)
            })

            return
        }

        if (getPermissionStatus(permission)) {
            permissionCallback(true)
            return
        }

        try {
            requestPermissionLauncher.launch(permission)
        } catch (e: ActivityNotFoundException) {
            permissionCallback(false)
        }
    }

    private external fun permissionCallback(granted: Boolean)

    const val ARGUMENT_SAFE_MODE = "--geode:safe-mode"

    private var additionalLaunchArguments = arrayListOf<String>()
    fun setAdditionalLaunchArguments(vararg args: String) {
        additionalLaunchArguments.addAll(args)
    }

    fun clearLaunchArguments() = setAdditionalLaunchArguments()

    @JvmStatic
    fun getLaunchArguments(): String? {
        activity.get()?.apply {
            val preferences = PreferenceUtils.get(this)

            val userArgs = preferences.getString(PreferenceUtils.Key.LAUNCH_ARGUMENTS)
            val args = if (!userArgs.isNullOrEmpty()) {
                listOf(userArgs) + additionalLaunchArguments
            } else additionalLaunchArguments

            return args.joinToString(" ")
        }

        return null
    }

    interface CapabilityListener {
        fun onCapabilityAdded(capability: String): Boolean
    }

    const val CAPABILITY_EXTENDED_INPUT = "extended_input"
    const val CAPABILITY_TIMESTAMP_INPUT = "timestamp_inputs"

    private var capabilityListener: WeakReference<CapabilityListener?> = WeakReference(null)

    fun setCapabilityListener(listener: CapabilityListener) {
        capabilityListener = WeakReference(listener)
    }

    @JvmStatic
    fun reportPlatformCapability(capability: String?): Boolean {
        if (capability.isNullOrEmpty()) {
            return false
        }

        return capabilityListener.get()?.onCapabilityAdded(capability) ?: false
    }

    external fun nativeKeyUp(keyCode: Int, modifiers: Int)
    external fun nativeKeyDown(keyCode: Int, modifiers: Int, isRepeating: Boolean)
    external fun nativeActionScroll(scrollX: Float, scrollY: Float)
    external fun resizeSurface(width: Int, height: Int)

    // represents the timestamp of the next input callback, in nanoseconds (most events don't send it, but it's there)
    external fun setNextInputTimestamp(timestamp: Long)
}