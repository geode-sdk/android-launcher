package com.geode.launcher

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.util.Log
import com.geode.launcher.MainActivity
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

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