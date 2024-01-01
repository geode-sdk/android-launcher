package com.geode.launcher.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileUtils
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile


object DownloadUtils {
    suspend fun downloadFile(
        context: Context,
        url: String,
        filename: String,
        progressHandler: ((progress: Long, outOf: Long) -> Unit)?
    ): File{
        val assetUri = Uri.parse(url)

        // fallback shouldn't be used
        val fallbackPath = File(Environment.getExternalStorageDirectory(), "Geode")
        val filesPath = context.getExternalFilesDir("") ?: fallbackPath
        val downloadPath = File(filesPath, filename)
        val downloadUri = Uri.fromFile(downloadPath)

        val request = DownloadManager.Request(assetUri)
            .setTitle(filename)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setDestinationUri(downloadUri)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val queuedRequest = downloadManager.enqueue(request)
        val query = DownloadManager.Query().setFilterById(queuedRequest)

        // register progress and finished handlers
        return suspendCancellableCoroutine { continuation ->
            val finishedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id != queuedRequest) {
                        return
                    }

                    val cursor = downloadManager.query(query)
                    cursor.use { c ->
                        if (!c.moveToFirst()) {
                            return
                        }

                        val statusColumn = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = c.getInt(statusColumn)

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> continuation.resumeWith(Result.success(downloadPath))
                            DownloadManager.STATUS_FAILED -> {
                                val errorColumn = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val error = c.getInt(errorColumn)
                                val exception = IOException("download failed, status code $error")

                                continuation.resumeWith(Result.failure(exception))
                            }
                            else -> {
                                // could be reported as progress
                            }
                        }
                    }
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

                    val cursor = downloadManager.query(query)
                    cursor.use { c ->
                        if (!c.moveToFirst()) {
                            return
                        }

                        val progressColumn = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        if (progressColumn < 0) {
                            return
                        }

                        val totalSizeColumn = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        if (totalSizeColumn < 0) {
                            return
                        }

                        val progress = c.getLong(progressColumn)
                        val outOf = c.getLong(totalSizeColumn)

                        if (progress < outOf) {
                            progressHandler?.invoke(progress, outOf)
                        }
                    }
                }
            }

            val downloadsUri = Uri.parse("content://downloads/my_downloads")
            context.contentResolver.registerContentObserver(downloadsUri, true, progressReceiver)

            continuation.invokeOnCancellation {
                downloadManager.remove(queuedRequest)
            }
        }
    }

    suspend fun extractFileFromZip(input: File, output: File, zipPath: String) {
        return coroutineScope {
            val zip = ZipFile(input)
            val entry = zip.getEntry(zipPath) ?:
                throw FileNotFoundException("could not find $zipPath in archive")

            val inputStream = zip.getInputStream(entry)
            val outputStream = output.outputStream()

            copyFile(inputStream, outputStream)
        }
    }

    suspend fun copyFile(inputStream: InputStream, outputStream: OutputStream) {
        coroutineScope {
            // gotta love copying
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                FileUtils.copy(inputStream, outputStream)
            } else {
                inputStream.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(4 * 1024)
                        while (true) {
                            val byteCount = input.read(buffer)
                            if (byteCount < 0) break
                            output.write(buffer, 0, byteCount)
                        }
                        output.flush()
                    }
                }
            }
        }
    }
}
