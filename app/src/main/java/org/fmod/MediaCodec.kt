package org.fmod

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer

class MediaCodec {
    // these variables require getX functions for ndk access
    var channelCount = 0
        private set

    var length = 0L
        private set

    var sampleRate = 0
        private set

    private var mCodecPtr = 0L
    private var mCurrentOutputBufferIndex = -1
    private var mDecoder: MediaCodec? = null
    private var mExtractor: MediaExtractor? = null
    private var mInputBuffers: Array<ByteBuffer>? = null
    private var mInputFinished = false
    private var mOutputBuffers: Array<ByteBuffer>? = null
    private var mOutputFinished = false

    fun init(codecPtr: Long): Boolean {
        mCodecPtr = codecPtr

        try {
            val mediaExtractor = MediaExtractor()
            mExtractor = mediaExtractor
            mediaExtractor.setDataSource(object : MediaDataSource() {
                override fun close() {}
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    return fmodReadAt(mCodecPtr, position, buffer, offset, size)
                }

                override fun getSize(): Long {
                    return fmodGetSize(mCodecPtr)
                }
            })
        } catch (e: IOException) {
            Log.w("fmod", "MediaCodec::init : $e")
            return false
        }

        val extractor = mExtractor!!

        val trackCount = extractor.trackCount
        var track = 0

        while (track < trackCount) {
            val trackFormat = extractor.getTrackFormat(track)
            val string = trackFormat.getString(MediaFormat.KEY_MIME)
            Log.d(
                "fmod",
                "MediaCodec::init : Format $track / $trackCount -- $trackFormat"
            )
            if (string == MediaFormat.MIMETYPE_AUDIO_AAC) {
                return try {
                    val decoder = MediaCodec.createDecoderByType(string)
                    extractor.selectTrack(track)
                    decoder.configure(trackFormat, null, null, 0)
                    decoder.start()
                    mInputBuffers = decoder.inputBuffers
                    mOutputBuffers = decoder.outputBuffers
                    mDecoder = decoder

                    val encoderDelay =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && trackFormat.containsKey(MediaFormat.KEY_ENCODER_DELAY))
                            trackFormat.getInteger(MediaFormat.KEY_ENCODER_DELAY) else 0

                    val encoderPadding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && trackFormat.containsKey(MediaFormat.KEY_ENCODER_PADDING))
                        trackFormat.getInteger(MediaFormat.KEY_ENCODER_PADDING) else 0

                    val trackDuration = trackFormat.getLong(MediaFormat.KEY_DURATION)
                    channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val trackSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    sampleRate = trackSampleRate
                    length =
                        (((trackDuration * trackSampleRate.toLong() + 999999) / 1000000).toInt() - encoderDelay - encoderPadding).toLong()

                    true
                } catch (e: IOException) {
                    Log.e("fmod", "MediaCodec::init : $e")
                    false
                }
            } else {
                track++
            }
        }
        return false
    }

    fun release() {
        mDecoder?.stop()
        mDecoder?.release()
        mDecoder = null

        mExtractor?.release()
        mExtractor = null
    }

    fun read(bArr: ByteArray, i: Int): Int {
        var dequeueInputBuffer = 0
        val i2 =
            if (!mInputFinished || !mOutputFinished || mCurrentOutputBufferIndex != -1) 0 else -1
        while (!mInputFinished && mDecoder!!.dequeueInputBuffer(0).also {
                dequeueInputBuffer = it
            } >= 0) {
            val readSampleData = mExtractor!!.readSampleData(mInputBuffers!![dequeueInputBuffer], 0)
            if (readSampleData >= 0) {
                mDecoder!!.queueInputBuffer(
                    dequeueInputBuffer,
                    0,
                    readSampleData,
                    mExtractor!!.sampleTime,
                    0
                )
                mExtractor!!.advance()
            } else {
                mDecoder!!.queueInputBuffer(dequeueInputBuffer, 0, 0, 0, 4)
                mInputFinished = true
            }
        }
        if (!mOutputFinished && mCurrentOutputBufferIndex == -1) {
            val bufferInfo = BufferInfo()
            val dequeueOutputBuffer = mDecoder!!.dequeueOutputBuffer(bufferInfo, 10000)
            if (dequeueOutputBuffer >= 0) {
                mCurrentOutputBufferIndex = dequeueOutputBuffer
                mOutputBuffers!![dequeueOutputBuffer].limit(bufferInfo.size)
                mOutputBuffers!![dequeueOutputBuffer].position(bufferInfo.offset)
            } else if (dequeueOutputBuffer == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                mOutputBuffers = mDecoder!!.outputBuffers
            } else if (dequeueOutputBuffer == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(
                    "fmod",
                    "MediaCodec::read : MediaCodec::dequeueOutputBuffer returned MediaCodec.INFO_OUTPUT_FORMAT_CHANGED " + mDecoder!!.outputFormat
                )
            } else if (dequeueOutputBuffer == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(
                    "fmod",
                    "MediaCodec::read : MediaCodec::dequeueOutputBuffer returned MediaCodec.INFO_TRY_AGAIN_LATER."
                )
            } else {
                Log.w(
                    "fmod",
                    "MediaCodec::read : MediaCodec::dequeueOutputBuffer returned $dequeueOutputBuffer"
                )
            }
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                mOutputFinished = true
            }
        }
        val i3 = mCurrentOutputBufferIndex
        if (i3 == -1) {
            return i2
        }
        val byteBuffer = mOutputBuffers!![i3]
        val min = byteBuffer.remaining().coerceAtMost(i)
        byteBuffer[bArr, 0, min]
        if (!byteBuffer.hasRemaining()) {
            byteBuffer.clear()
            mDecoder!!.releaseOutputBuffer(mCurrentOutputBufferIndex, false)
            mCurrentOutputBufferIndex = -1
        }
        return min
    }

    fun seek(i: Int) {
        val i2 = mCurrentOutputBufferIndex
        if (i2 != -1) {
            mOutputBuffers!![i2].clear()
            mCurrentOutputBufferIndex = -1
        }
        mInputFinished = false
        mOutputFinished = false
        mDecoder!!.flush()
        val j = i.toLong()
        mExtractor!!.seekTo(j * 1000000 / sampleRate.toLong(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val sampleTime = (mExtractor!!.sampleTime * sampleRate.toLong() + 999999) / 1000000
        var i3 = ((j - sampleTime) * channelCount.toLong() * 2).toInt()
        if (i3 < 0) {
            Log.w(
                "fmod",
                "MediaCodec::seek : Seek to $i resulted in position $sampleTime"
            )
            return
        }
        val bArr = ByteArray(1024)
        while (i3 > 0) {
            i3 -= read(bArr, 1024.coerceAtMost(i3))
        }
    }

    companion object {
        @JvmStatic
        external fun fmodGetSize(codecPtr: Long): Long

        @JvmStatic
        external fun fmodReadAt(codecPtr: Long, position: Long, buffer: ByteArray?, offset: Int, size: Int): Int
    }
}
