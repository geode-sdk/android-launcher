package com.geode.launcher.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.geode.launcher.api.ReleaseViewModel
import java.io.File

interface DownloadReceiver {
    fun onProgress(progress: Long, outOf: Long)
    fun onFinished(downloadPath: String)
}

fun downloadFile(context: Context, url: String, filename: String, receiver: DownloadReceiver) {
    val assetUri = Uri.parse(url)

    // this should not fail. it would be odd
    val filesPath = context.getExternalFilesDir("") ?: return
    val downloadPath = File(filesPath, filename)
    val downloadUri = Uri.fromFile(downloadPath)

    val request = DownloadManager.Request(assetUri)
        .setTitle(filename)
        .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        .setDestinationUri(downloadUri)

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val queuedRequest = downloadManager.enqueue(request)

    // register progress and finished handlers
    val finishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != queuedRequest) {
                return
            }

            receiver.onFinished(downloadPath.path)
        }
    }

    ContextCompat.registerReceiver(
        context,
        finishedReceiver,
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        ContextCompat.RECEIVER_EXPORTED
    )

    val progressReceiver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)

            val query = DownloadManager.Query().setFilterById(queuedRequest)
            val cursor = downloadManager.query(query)

            if (!cursor.moveToFirst()) {
                cursor.close()
                return
            }

            val progressColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            if (progressColumn < 0) {
                cursor.close()
                return
            }

            val totalSizeColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            if (totalSizeColumn < 0) {
                cursor.close()
                return
            }

            val progress = cursor.getLong(progressColumn)
            val outOf = cursor.getLong(totalSizeColumn)

            if (progress < outOf) {
                receiver.onProgress(progress, outOf)
            }

            cursor.close()
        }
    }

    val downloadsUri = Uri.parse("content://downloads/my_downloads")
    context.contentResolver.registerContentObserver(downloadsUri, true, progressReceiver)
}

/*
class DownloadGeode constructor(context: Context) : AsyncTask<String, Int, Boolean>() {
    private val context: Context = context

    override fun doInBackground(vararg params: String?): Boolean {
        val url = URL(params[0])
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()
        // downloads a zip file
        ZipInputStream(connection.inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = entry.name
                context.getExternalFilesDir(null)?.let { dir->
                    val destFile = File(dir, file)
                    destFile.outputStream().use { output ->
                        zip.copyTo(output)
                        Log.d("Geode", "Downloading to $destFile")
                    }
                }
                entry = zip.nextEntry
            }
        }

        return true
    }

    override fun onPostExecute(result: Boolean) {
        super.onPostExecute(result)
        Log.d("Geode", "Downloading Result: $result")

        // update the screen
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
*/
