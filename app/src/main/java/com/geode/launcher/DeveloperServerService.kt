package com.geode.launcher

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.geode.launcher.log.LogLine
import com.geode.launcher.utils.DownloadUtils
import com.geode.launcher.utils.LaunchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Buffer
import okio.buffer
import okio.source
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import kotlin.collections.plusAssign
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.sse.*
import io.ktor.sse.ServerSentEvent
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose

enum class FileType {
    @SerialName("file")
    FILE,
    @SerialName("directory")
    DIRECTORY
}

@Serializable
data class FileMeta(val filename: String, val type: FileType, val size: Long, val lastModified: Instant)

class DeveloperServerService : Service() {
    private val binder = LocalBinder()

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    inner class LocalBinder : Binder() {
        fun getService(): DeveloperServerService = this@DeveloperServerService
    }

    private fun startForeground() {
        try {
            val notification = NotificationCompat.Builder(this, "geode_developer_server")
                .setSmallIcon(R.drawable.geode_monochrome)
                .build()
            ServiceCompat.startForeground(
                this,
                100,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            /*
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                println("idk")
            }
            */
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        server?.stop()
        server = null
    }

    // not recursive copy
    private fun copyIndexToCache() {
        val folderName = "web_interface"
        val base = File(application.cacheDir, folderName)

        base.deleteRecursively()
        base.mkdirs()

        assets.list(folderName)?.forEach {
            val assetName = "$folderName/$it"
            val outputPath = File(base, it)
            assets.open(assetName).use { inputStream ->
                outputPath.outputStream().use { outputStream ->
                    DownloadUtils.copyFile(inputStream, outputStream)
                }
            }
        }
    }

    private fun createLogsFlow(): Flow<LogLine> = flow {
        val timestamp = System.currentTimeMillis()
        val seconds = timestamp / 1000
        val ms = timestamp % 1000

        // Time format is 'MM-DD hh:mm:ss.mmm...', 'YYYY-MM-DD hh:mm:ss.mmm...', or 'sssss.mmm...'.
        val timeString = "$seconds.$ms"

        val logCommand = "logcat -B -T $timeString"

        val logProcess = try {
            Runtime.getRuntime().exec(logCommand)
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            emit(LogLine.showException(ioe))

            return@flow
        }

        val logBuffer = logProcess.inputStream.source().buffer()

        try {
            // this runs until the stream is exhausted
            while (currentCoroutineContext().isActive) {
                val line = LogLine.fromBufferedSource(logBuffer)
                emit(line)

                yield()
            }
        } catch (_: EOFException) {
            // ignore, end of file reached
        } catch (e: Exception) {
            e.printStackTrace()
            emit(LogLine.showException(e))
        }
    }

    private suspend fun readLogs(): List<LogLine> {
        val logLines = mutableListOf<LogLine>()

        val logCommand = "logcat -B -d"
        val logProcess = try {
            withContext(Dispatchers.IO) {
                Runtime.getRuntime().exec(logCommand)
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
            logLines += LogLine.showException(ioe)

            return logLines
        }

        // read entire log into a buffer so no logs are added to the buffer during processing
        val logBuffer = Buffer()
        logProcess.inputStream.source().buffer().readAll(logBuffer)

        try {
            coroutineScope {
                // this runs until the stream is exhausted
                while (true) {
                    val line = LogLine.fromBufferedSource(logBuffer)
                    logLines += line

                    yield()
                }
            }
        } catch (_: EOFException) {
            // ignore, end of file reached
        } catch (e: Exception) {
            e.printStackTrace()
            logLines += LogLine.showException(e)
        }

        return logLines
    }

    override fun onCreate() {
        super.onCreate()

        val staticDir = File(application.cacheDir, "web_interface")
        val baseDir = LaunchUtils.getBaseDirectory(application, true)

        val logsFlow = createLogsFlow()

        server = embeddedServer(CIO, port = 9043) {
            routing {
                install(SSE)

                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
                    }
                }

                get("/logs") {
                    val logs = Json.encodeToString(readLogs())
                    call.respondText(logs, contentType = ContentType.Application.Json)
                }

                post("/stop") {
                    stopSelf()
                }

                sse("/logs_live") {
                    heartbeat {
                        period = 5000.milliseconds
                        event = ServerSentEvent(comments = "heartbeat")
                    }
                    logsFlow.collect {
                        val log = Json.encodeToString(it)
                        send(log, event = "log", id = it.logId.toString())
                    }
                }

                staticFiles("/", staticDir)

                staticFiles("/files", baseDir, index = null) {
                    fallback { requestedPath, call ->
                        val basePath = baseDir.toPath()
                        val builtPath = basePath.resolve(requestedPath).normalize()
                        if (!builtPath.startsWith(basePath) || !builtPath.exists()) {
                            // block path traversal issues
                            call.response.status(HttpStatusCode.NotFound)
                        } else {
                            val filenames = builtPath.toFile().listFiles()?.map {
                                FileMeta(
                                    filename = it.name,
                                    type = if (it.isDirectory) FileType.DIRECTORY else FileType.FILE,
                                    size = it.length(),
                                    lastModified = Instant.fromEpochMilliseconds(it.lastModified())
                                )
                            } ?: emptyList()

                            val data = Json.encodeToString(filenames)

                            call.respondText(data, contentType = ContentType.Application.Json)
                        }
                    }
                }

                delete("/files/{path...}") {
                    val pathSegments = call.parameters.getAll("path")
                    val requestedPath = pathSegments?.joinToString("/") ?: ""

                    val basePath = baseDir.toPath()
                    val builtPath = basePath.resolve(requestedPath).normalize()
                    if (!builtPath.startsWith(basePath) || !builtPath.exists()) {
                        call.response.status(HttpStatusCode.NotFound)
                    } else {
                        try {
                            builtPath.deleteExisting()
                            call.response.status(HttpStatusCode.NoContent)
                        } catch (e: DirectoryNotEmptyException) {
                            val message = Json.encodeToString(buildJsonObject {
                                put("message", "Provided directory $requestedPath was not empty")
                            })

                            call.respondText(message, status = HttpStatusCode.Conflict, contentType = ContentType.Application.Json)
                        }
                    }
                }

                put("/files/{path...}") {
                    val pathSegments = call.parameters.getAll("path")
                    val requestedPath = pathSegments?.joinToString("/") ?: ""

                    val basePath = baseDir.toPath()
                    val builtPath = basePath.resolve(requestedPath).normalize()
                    if (!builtPath.startsWith(basePath)) {
                        call.response.status(HttpStatusCode.NotFound)
                    } else {
                        if (builtPath.isDirectory()) {
                            val multipart = call.receiveMultipart(formFieldLimit = 1024 * 1024 * 100)
                            multipart.forEachPart { part ->
                                if (part is PartData.FileItem) {
                                    val fileName = part.originalFileName ?: "uploaded_file"

                                    val outputPath = builtPath.resolve(fileName).normalize()
                                    if (!outputPath.startsWith(basePath)) {
                                        val outputFile = outputPath.toFile()
                                        part.provider().copyAndClose(outputFile.writeChannel())
                                    }
                                }
                                part.dispose()
                            }
                        } else {
                            val outputFile = builtPath.toFile()
                            outputFile.parentFile?.mkdirs()

                            call.receiveChannel().copyAndClose(outputFile.writeChannel())
                        }

                        call.response.status(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        this.startForeground()

        copyIndexToCache()
        server?.start(wait = false)

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        stopSelf()
    }
}

