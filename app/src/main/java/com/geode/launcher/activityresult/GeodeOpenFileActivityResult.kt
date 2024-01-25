package com.geode.launcher.activityresult

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

open class GeodeOpenFileActivityResult : ActivityResultContract<GeodeOpenFileActivityResult.OpenFileParams, Uri?>() {
    class OpenFileParams(val extraMimes: Array<String>, val defaultPath: Uri?) {}
    override fun createIntent(context: Context, input: OpenFileParams): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_MIME_TYPES, input.extraMimes)
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
    ): SynchronousResult<Uri?>? = null

    final override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }
}

