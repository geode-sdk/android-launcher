package com.geode.launcher.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import kotlin.system.exitProcess

@Suppress("unused")
object GeodeUtils {
    private lateinit var activity: WeakReference<AppCompatActivity>
    private lateinit var fileResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var filesResultLauncher: ActivityResultLauncher<Intent>

    fun setContext(activity: AppCompatActivity) {
        this.activity = WeakReference(activity)
        fileResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val uri = data.data
                    if (uri != null) {
                        val path = getRealPathFromURI(activity, uri)
                        if (path != null) {
                            selectFileCallback(path)
                            return@registerForActivityResult
                        }
                    }
                }
            }
            failedCallback()
        }
        filesResultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val clipData = data.clipData
                    if (clipData != null) {
                        val paths = Array(clipData.itemCount) { "" }
                        for (i in 0 until clipData.itemCount) {
                            val path = getRealPathFromURI(activity, clipData.getItemAt(i).uri)
                            if (path != null) {
                                paths[i] = path
                            }
                        }
                        selectFilesCallback(paths)
                        return@registerForActivityResult
                    }
                }
            }
            failedCallback()
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
                mainIntent.putExtra("restarted", true);
                startActivity(mainIntent)
                exitProcess(0)
            }
        }
    }

    @JvmStatic
    fun openFolder(path: String): Boolean {
        activity.get()?.run {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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

    external fun selectFileCallback(path: String)

    external fun selectFilesCallback(paths: Array<String>)

    external fun failedCallback()

    @JvmStatic
    fun selectFile(path: String): Boolean {
        activity.get()?.run {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            DocumentFile.fromFile(File(path)).also {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it.uri)
            }
            intent.setType("*/*")

            if (intent.resolveActivity(packageManager) != null) {
                print("Geode Selectfile")
                fileResultLauncher.launch(intent)
                print("Geode launched ")
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun selectFiles(path: String): Boolean {
        activity.get()?.run {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            DocumentFile.fromFile(File(path)).also {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it.uri)
            }
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.setType("*/*")

            if (intent.resolveActivity(packageManager) != null) {
                fileResultLauncher.launch(intent)
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun selectFolder(path: String): Boolean {
        activity.get()?.run {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            DocumentFile.fromFile(File(path)).also {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it.uri)
            }
            intent.setType("*/*")

            if (intent.resolveActivity(packageManager) != null) {
                fileResultLauncher.launch(intent)
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun createFile(path: String): Boolean {
        activity.get()?.run {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            DocumentFile.fromFile(File(path)).also {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it.uri)
            }
            intent.setType("*/*")

            if (intent.resolveActivity(packageManager) != null) {
                filesResultLauncher.launch(intent)
                return true
            }
        }
        return false
    }

    // copied from https://stackoverflow.com/questions/17546101/get-real-path-for-uri-android
    // i am actually very lazy to move this to a separate class

    fun getRealPathFromURI(context: Context, uri: Uri): String? {
        when {
            // DocumentProvider
            DocumentsContract.isDocumentUri(context, uri) -> {
                when {
                    // ExternalStorageProvider
                    isExternalStorageDocument(uri) -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val type = split[0]
                        // This is for checking Main Memory
                        return if ("primary".equals(type, ignoreCase = true)) {
                            if (split.size > 1) {
                                Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                            } else {
                                Environment.getExternalStorageDirectory().toString() + "/"
                            }
                            // This is for checking SD Card
                        } else {
                            "storage" + "/" + docId.replace(":", "/")
                        }
                    }
                    isDownloadsDocument(uri) -> {
                        val fileName = getFilePath(context, uri)
                        if (fileName != null) {
                            return Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
                        }
                        var id = DocumentsContract.getDocumentId(uri)
                        if (id.startsWith("raw:")) {
                            id = id.replaceFirst("raw:".toRegex(), "")
                            val file = File(id)
                            if (file.exists()) return id
                        }
                        val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                        return getDataColumn(context, contentUri, null, null)
                    }
                    isMediaDocument(uri) -> {
                        val docId = DocumentsContract.getDocumentId(uri)
                        val split = docId.split(":").toTypedArray()
                        val type = split[0]
                        var contentUri: Uri? = null
                        when (type) {
                            "image" -> {
                                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            }
                            "video" -> {
                                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            }
                            "audio" -> {
                                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            }
                        }
                        val selection = "_id=?"
                        val selectionArgs = arrayOf(split[1])
                        return getDataColumn(context, contentUri, selection, selectionArgs)
                    }

                    // TODO: add the geode provider
                }
            }
            "content".equals(uri.scheme, ignoreCase = true) -> {
                // Return the remote address
                if (isGooglePhotosUri(uri)) return uri.lastPathSegment
                return getDataColumn(context, uri, null, null)
            }
            "file".equals(uri.scheme, ignoreCase = true) -> {
                return uri.path
            }
        }
        return null
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?,
                    selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
                column
        )
        try {
            if (uri == null) return null
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs,
                    null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }


    fun getFilePath(context: Context, uri: Uri?): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME
        )
        try {
            if (uri == null) return null
            cursor = context.contentResolver.query(uri, projection, null, null,
                    null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                return cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
    * @param uri The Uri to check.
    * @return Whether the Uri authority is ExternalStorageProvider.
    */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
    * @param uri The Uri to check.
    * @return Whether the Uri authority is DownloadsProvider.
    */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
    * @param uri The Uri to check.
    * @return Whether the Uri authority is MediaProvider.
    */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
    * @param uri The Uri to check.
    * @return Whether the Uri authority is Google Photos.
    */
    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    fun isGeodeUri(uri: Uri): Boolean {
        return "com.geode.launcher.user" == uri.authority
    }
}