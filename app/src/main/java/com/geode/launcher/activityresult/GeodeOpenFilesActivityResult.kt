package com.geode.launcher.activityresult

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

open class GeodeOpenFilesActivityResult : ActivityResultContract<GeodeOpenFilesActivityResult.OpenFileParams, List<@JvmSuppressWildcards Uri>>() {
    class OpenFileParams(val extraMimes: Array<String>, val defaultPath: Uri?) {}
    override fun createIntent(context: Context, input: OpenFileParams): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .putExtra(Intent.EXTRA_MIME_TYPES, input.extraMimes)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .setType("*/*")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (input.defaultPath != null) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.defaultPath)
            }
        }
        return intent
    }

    final override fun getSynchronousResult(
        context: Context,
        input: OpenFileParams
    ): SynchronousResult<List<@JvmSuppressWildcards Uri>>? = null

    final override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        // Gracefully stolen from ActivityResultContracts.Intent.getclipDataUris
        val clipData = intent.takeIf {
            resultCode == Activity.RESULT_OK
        }?.clipData
        val resultSet = LinkedHashSet<Uri>()
        intent?.data?.let { data ->
            resultSet.add(data)
        }
        if (clipData == null && resultSet.isEmpty()) {
            return emptyList()
        } else if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                if (uri != null) {
                    resultSet.add(uri)
                }
            }
        }
        return ArrayList(resultSet)
    }
}

