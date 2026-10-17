package com.customRobTop.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class ImportFileActivityContract : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(
        context: Context,
        input: Unit
    ): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return emptyList()
        }

        val clipData = intent.clipData
        return if (clipData != null) {
            List(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
        } else {
            val intentData = intent.data
            if (intentData != null)
                listOf(intentData)
            else
                emptyList()
        }
    }
}