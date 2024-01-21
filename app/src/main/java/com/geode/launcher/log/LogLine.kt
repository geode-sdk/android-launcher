package com.geode.launcher.log

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import okio.BufferedSource
import java.io.IOException

fun BufferedSource.readCChar(): Char {
    return this.readUtf8CodePoint().toChar()
}

fun BufferedSource.readCString(): String {
    val buffer = StringBuilder()

    var lastByte = this.readCChar()
    while (lastByte != '\u0000') {
        buffer.append(lastByte)
        lastByte = this.readCChar()
    }

    return buffer.toString()
}

data class ProcessInformation(val processId: Int, val threadId: Int, val processUid: Int)

enum class LogPriority {
    UNKNOWN, DEFAULT, VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL, SILENT;

    companion object {
        fun fromByte(byte: Byte): LogPriority {
            return when (byte.toInt()) {
                0x1 -> DEFAULT
                0x2 -> VERBOSE
                0x3 -> DEBUG
                0x4 -> INFO
                0x5 -> WARN
                0x6 -> ERROR
                0x7 -> FATAL
                0x8 -> SILENT
                else -> UNKNOWN
            }
        }
    }

    fun toChar(): Char {
        return when (this) {
            VERBOSE -> 'V'
            DEBUG -> 'D'
            INFO -> 'I'
            WARN -> 'W'
            ERROR -> 'E'
            FATAL -> 'F'
            SILENT -> 'S'
            else -> '?'
        }
    }
}

/**
 * Represents a log entry from logcat.
 */
data class LogLine(
    val process: ProcessInformation,
    val time: Instant,
    val logId: Int,
    val priority: LogPriority,
    val tag: String,
    val message: String
) {
    companion object {
        private enum class EntryVersion {
            V3, V4
        }

        private fun headerSizeToVersion(size: UShort) = when (size.toInt()) {
            0x18 -> EntryVersion.V3
            0x1c -> EntryVersion.V4
            else -> throw IOException("LogLine::fromInputStream: unknown format for (headerSize = $size)")
        }

        fun fromBufferedSource(source: BufferedSource): LogLine {
            /*
                // from android <liblog/include/log/log_read.h>
                // there are multiple logger entry formats
                // use the header_size to determine the one you have

                struct logger_entry_v3 {
                  uint16_t len;      // length of the payload
                  uint16_t hdr_size; // sizeof(struct logger_entry_v3)
                  int32_t pid;       // generating process's pid
                  int32_t tid;       // generating process's tid
                  int32_t sec;       // seconds since Epoch
                  int32_t nsec;      // nanoseconds
                  uint32_t lid;      // log id of the payload
                }

                struct logger_entry_v4 {
                  uint16_t len;      // length of the payload
                  uint16_t hdr_size; // sizeof(struct logger_entry_v4 = 28)
                  int32_t pid;       // generating process's pid
                  uint32_t tid;      // generating process's tid
                  uint32_t sec;      // seconds since Epoch
                  uint32_t nsec;     // nanoseconds
                  uint32_t lid;      // log id of the payload, bottom 4 bits currently
                  uint32_t uid;      // generating process's uid
                };
            */

            /* val payloadLength = */ source.readShortLe()
            val headerSize = source.readShortLe().toUShort()

            val entryVersion = headerSizeToVersion(headerSize)

            val pid = source.readIntLe()
            val tid = source.readIntLe()
            val sec = source.readIntLe().toLong()
            val nSec = source.readIntLe()
            val lid = source.readIntLe()
            val uid = if (entryVersion == EntryVersion.V4)
                source.readIntLe() else 0

            val processInformation = ProcessInformation(pid, tid, uid)
            val time = Instant.fromEpochSeconds(sec, nSec)

            val priorityByte = source.readByte()
            val priority = LogPriority.fromByte(priorityByte)

            val tag = source.readCString()
            val message = source.readCString()

            return LogLine(
                process = processInformation,
                priority = priority,
                time = time,
                logId = lid,
                tag = tag,
                message = message
            )
        }

        fun showException(exception: Exception) = LogLine(
            process = ProcessInformation(0, 0, 0),
            time = Clock.System.now(),
            logId = 0,
            priority = LogPriority.FATAL,
            tag = "GeodeLauncher",
            message = "Failed to parse log entry with ${exception.stackTraceToString()}"
        )
    }

    val identifier = time.toJavaInstant()

    val formattedTime by lazy { this.time.toLocalDateTime(TimeZone.currentSystemDefault()) }
    val asSimpleString by lazy {
        "$formattedTime [${this.priority.toChar()}/${this.tag}]: ${this.message}"
    }
}