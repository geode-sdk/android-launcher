package com.geode.launcher.log

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

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

@Serializable
data class ProcessInformation(val processId: Int, val threadId: Int, val processUid: Int)

@Serializable
enum class LogPriority {
    @SerialName("unknown")
    UNKNOWN,
    @SerialName("default")
    DEFAULT,
    @SerialName("verbose")
    VERBOSE,
    @SerialName("debug")
    DEBUG,
    @SerialName("info")
    INFO,
    @SerialName("warn")
    WARN,
    @SerialName("error")
    ERROR,
    @SerialName("fatal")
    FATAL,
    @SerialName("silent")
    SILENT;

    class LogPriorityIterator(
        start: LogPriority,
        private val end: LogPriority,
        private val step: Int
    ) : Iterator<LogPriority> {
        private var hasNext: Boolean = if (step > 0) start <= end else start >= end
        private var next = if (hasNext) start else end

        override fun hasNext() = hasNext

        override fun next(): LogPriority {
            val current = next
            if (current == end) {
                if (!hasNext) {
                    throw NoSuchElementException()
                }

                hasNext = false
            } else {
                next = LogPriority.fromInt(next.toInt() + step)
            }

            return current
        }
    }

    class LogPriorityProgression(
        override val start: LogPriority,
        override val endInclusive: LogPriority,
        private val step: Int = 1
    ) : Iterable<LogPriority>, ClosedRange<LogPriority> {
        override fun iterator(): Iterator<LogPriority> {
            return LogPriorityIterator(start, endInclusive, step)
        }
    }

    infix fun downTo(to: LogPriority) = LogPriorityProgression(this, to, -1)

    operator fun rangeTo(to: LogPriority) = LogPriorityProgression(this, to)

    companion object {
        fun fromByte(byte: Byte): LogPriority = fromInt(byte.toInt())

        fun fromInt(int: Int) = when (int) {
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

    fun toInt() = when (this) {
        DEFAULT -> 0x1
        VERBOSE -> 0x2
        DEBUG -> 0x3
        INFO -> 0x4
        WARN -> 0x5
        ERROR -> 0x6
        FATAL -> 0x7
        SILENT -> 0x8
        else -> 0x0
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
@Serializable
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

        @OptIn(ExperimentalTime::class)
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

            val payloadLength = source.readShortLe().toLong()
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

            // the payload is split into three parts
            // initial priority byte -> null terminated tag -> non null terminated message

            val packetBuffer = Buffer()
            source.readFully(packetBuffer, payloadLength)

            val priorityByte = packetBuffer.readByte()
            val priority = LogPriority.fromByte(priorityByte)

            val tag = packetBuffer.readCString()
            val message = packetBuffer.readUtf8()

            return LogLine(
                process = processInformation,
                priority = priority,
                time = time,
                logId = lid,
                tag = tag,
                message = message.trimEnd('\u0000')
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

    @Transient
    val identifier = time.toJavaInstant()

    val messageTrimmed by lazy {
        this.message.trim { it <= ' ' }
    }

    val formattedTime by lazy { this.time.toLocalDateTime(TimeZone.currentSystemDefault()) }

    val asSimpleString by lazy {
        "$formattedTime [${this.priority.toChar()}/${this.tag}]: ${this.messageTrimmed}"
    }
}