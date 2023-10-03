package com.geode.launcher.utils

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.core.content.FileProvider
import android.provider.DocumentsContract
import java.io.File
import android.net.Uri
import java.lang.ref.WeakReference
import kotlin.system.exitProcess

@Suppress("unused")
object GeodeUtils {
    private lateinit var context: WeakReference<Context>

    fun setContext(context: Context) {
        this.context = WeakReference(context)
    }

    @JvmStatic
    fun writeClipboard(text: String) {
        context.get()?.run {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Geode", text)
            manager.setPrimaryClip(clip)
        }
    }

    @JvmStatic
    fun readClipboard(): String {
        context.get()?.run {
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
        context.get()?.run {
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
        context.get()?.run {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            val dir = FileProvider.getUriForFile(this, "com.geode.launcher.fileprovider", File(path))
            intent.setDataAndType(dir, "application/*")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return true
            }
        }
        return false
    }
}