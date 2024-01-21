package com.geode.launcher.utils

import android.os.Build
import android.os.FileUtils
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


typealias ProgressCallback = (progress: Long, outOf: Long) -> Unit

object DownloadUtils {
    suspend fun downloadStream(
        httpClient: OkHttpClient,
        url: String,
        onProgress: ProgressCallback? = null
    ): InputStream {
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
                        originalResponse.body!!, onProgress
                    ))
                    .build()
            }
        }

        val progressClient = progressClientBuilder.build()

        val call = progressClient.newCall(request)
        val response = call.executeCoroutine()

        return response.body!!.byteStream()
    }

    suspend fun Call.executeCoroutine(): Response {
        return suspendCancellableCoroutine { continuation ->
            this.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) {
                        return
                    }

                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })

            continuation.invokeOnCancellation {
                this.cancel()
            }
        }
    }

    suspend fun extractFileFromZipStream(inputStream: InputStream, outputStream: OutputStream, zipPath: String) {
        // note to self: ZipInputStreams are a little silly
        // (runInterruptible allows it to cancel, otherwise it waits for the stream to finish)
        runInterruptible {
            inputStream.use {
                outputStream.use {
                    // nice indentation
                    val zip = ZipInputStream(inputStream)
                    zip.use {
                        var entry = it.nextEntry
                        while (entry != null) {
                            if (entry.name == zipPath) {
                                it.copyTo(outputStream)
                                return@runInterruptible
                            }

                            entry = it.nextEntry
                        }

                        // no matching entries found, throw exception
                        throw IOException("no file found for $zipPath")
                    }
                }
            }
        }
    }

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