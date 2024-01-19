package org.fmod

import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaCodec.BufferInfo
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer

class MediaCodec {
    private var channelCount = 0

    var mCodecPtr: Long = 0
    private var mCurrentOutputBufferIndex = -1
    private var mDecoder: MediaCodec? = null
    private var mExtractor: MediaExtractor? = null
    private var mInputBuffers: Array<ByteBuffer>? = null
    private var mInputFinished = false
    private var length: Long = 0
    private var mOutputBuffers: Array<ByteBuffer>? = null
    private var mOutputFinished = false
    private var sampleRate = 0

    fun init(j: Long): Boolean {
        mCodecPtr = j
        var i = 0

        try {
            val mediaExtractor = MediaExtractor()
            mExtractor = mediaExtractor
            mediaExtractor.setDataSource(object : MediaDataSource() {
                override fun close() {}
                override fun readAt(j: Long, bArr: ByteArray, i: Int, i2: Int): Int {
                    return fmodReadAt(mCodecPtr, j, bArr, i, i2)
                }

                override fun getSize(): Long {
                    return fmodGetSize(mCodecPtr)
                }
            })
        } catch (e5: IOException) {
            Log.w("fmod", "MediaCodec::init : $e5")
            return false
        }

        val trackCount = mExtractor!!.trackCount
        var i2 = 0
        while (i2 < trackCount) {
            val trackFormat = mExtractor!!.getTrackFormat(i2)
            val string = trackFormat.getString("mime")
            Log.d(
                "fmod",
                "MediaCodec::init : Format $i2 / $trackCount -- $trackFormat"
            )
            if (string == "audio/mp4a-latm") {
                return try {
                    mDecoder = MediaCodec.createDecoderByType(string)
                    mExtractor!!.selectTrack(i2)
                    mDecoder!!.configure(trackFormat, null as Surface?, null as MediaCrypto?, 0)
                    mDecoder!!.start()
                    mInputBuffers = mDecoder!!.inputBuffers
                    mOutputBuffers = mDecoder!!.outputBuffers
                    val integer =
                        if (trackFormat.containsKey("encoder-delay")) trackFormat.getInteger("encoder-delay") else 0
                    if (trackFormat.containsKey("encoder-padding")) {
                        i = trackFormat.getInteger("encoder-padding")
                    }
                    val j2 = trackFormat.getLong("durationUs")
                    channelCount = trackFormat.getInteger("channel-count")
                    val integer2 = trackFormat.getInteger("sample-rate")
                    sampleRate = integer2
                    length =
                        (((j2 * integer2.toLong() + 999999) / 1000000).toInt() - integer - i).toLong()
                    true
                } catch (e6: IOException) {
                    Log.e("fmod", "MediaCodec::init : $e6")
                    false
                }
            } else {
                i2++
            }
        }
        return false
    }

    fun release() {
        val mediaCodec = mDecoder
        if (mediaCodec != null) {
            mediaCodec.stop()
            mDecoder?.release()
            mDecoder = null
        }
        val mediaExtractor = mExtractor
        if (mediaExtractor != null) {
            mediaExtractor.release()
            mExtractor = null
        }
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
        external fun fmodGetSize(j: Long): Long

        @JvmStatic
        external fun fmodReadAt(j: Long, j2: Long, bArr: ByteArray?, i: Int, i2: Int): Int
    }
}
