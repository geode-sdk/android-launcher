package com.geode.launcher.utils

import android.os.Build
import android.os.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.coroutines.executeAsync
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.ForwardingSink
import okio.ForwardingSource
import okio.Path.Companion.toOkioPath
import okio.Sink
import okio.Source
import okio.buffer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream


typealias ProgressCallback = (progress: Long, outOf: Long) -> Unit

object DownloadUtils {
    suspend fun downloadStream(
        httpClient: OkHttpClient,
        url: String,
        onProgress: ProgressCallback? = null,
        onResponse: suspend (ResponseBody) -> Unit,
    ) {
        val request = Request.Builder()
            .url(url)
            .build()

        // build a new client using the same pool as the old client
        // (more efficient)
        val progressClientBuilder = httpClient.newBuilder()
            .cache(null)
            // disable timeout
            .readTimeout(0, TimeUnit.SECONDS)

        // add progress listener
        if (onProgress != null) {
            progressClientBuilder.addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(ProgressResponseBody(
                        originalResponse.body, onProgress
                    ))
                    .build()
            }
        }

        val progressClient = progressClientBuilder.build()

        val call = progressClient.newCall(request)
        call.executeAsync().use { response ->
            when (response.code) {
                200 -> onResponse(response.body)
                else -> throw IOException("unexpected response ${response.code}")
            }
        }
    }

    /**
     * Extracts a file named by zipPath from inputStream and copies it to outputStream
     *
     * inputStream and outputStream are both closed after this function executes
     */
    suspend fun extractFileFromZipStream(inputStream: InputStream, output: File, zipPath: String) {
        // note to self: ZipInputStreams are a little silly
        // (runInterruptible allows it to cancel, otherwise it waits for the stream to finish)
        runInterruptible {
            inputStream.use { input ->
                output.outputStream().use { out ->
                    // nice indentation
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == zipPath) {
                                zip.copyTo(out)
                                return@runInterruptible
                            }

                            entry = zip.nextEntry
                        }

                        // no matching entries found, throw exception
                        throw IOException("no file found for $zipPath")
                    }
                }
            }
        }
    }

    suspend fun copyFile(from: File, to: File) {
        val fs = FileSystem.SYSTEM

        val fromPath = from.toOkioPath()
        val toPath = to.toOkioPath()

        withContext(Dispatchers.IO) {
            fs.copy(fromPath, toPath)
        }
    }

    /**
     * Copies data from inputStream to outputStream
     *
     * Both streams are closed upon the conclusion of this function.
     */
    fun copyFile(inputStream: InputStream, outputStream: OutputStream) {
        // gotta love copying
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            inputStream.use { input -> outputStream.use { output ->
                FileUtils.copy(input, output)
            }}
        } else {
            inputStream.use { input -> outputStream.use { output ->
                input.copyTo(output)
            }}
        }
    }
}

// based on <https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/Progress.java>
// lazily ported to kotlin
private class ProgressResponseBody (
    private val responseBody: ResponseBody,
    private val progressCallback: ProgressCallback
) : ResponseBody() {
    private val bufferedSource: BufferedSource by lazy {
        source(responseBody.source()).buffer()
    }

    override fun contentType(): MediaType? {
        return responseBody.contentType()
    }

    override fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override fun source(): BufferedSource {
        return bufferedSource
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0

                progressCallback(
                    totalBytesRead,
                    responseBody.contentLength()
                )

                return bytesRead
            }
        }
    }
}

// now based on <https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/UploadProgress.java#L84>
class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val progressCallback: ProgressCallback
) : RequestBody() {
    override fun contentType(): MediaType? {
        return requestBody.contentType()
    }

    override fun contentLength(): Long {
        return requestBody.contentLength()
    }

    override fun writeTo(sink: BufferedSink) {
        val bufferedSink = this.sink(sink).buffer()
        requestBody.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    private fun sink(sink: Sink): Sink {
        return object : ForwardingSink(sink) {
            var totalBytesWritten = 0L

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                totalBytesWritten += byteCount
                progressCallback(
                    totalBytesWritten,
                    contentLength()
                )
            }
        }
    }
}