package com.geode.launcher.activityresult

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

open class GeodeSaveFileActivityResult : ActivityResultContract<GeodeSaveFileActivityResult.SaveFileParams, Uri?>() {
    class SaveFileParams(val mimeType: String?, val defaultPath: Uri?) {}
    override fun createIntent(context: Context, input: SaveFileParams): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .setType(input.mimeType ?: "*/*")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (input.defaultPath != null) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.defaultPath)
            }
        }
        return intent
    }

    final override fun getSynchronousResult(
        context: Context,
        input: SaveFileParams
    ): SynchronousResult<Uri?>? = null

    final override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }
}

