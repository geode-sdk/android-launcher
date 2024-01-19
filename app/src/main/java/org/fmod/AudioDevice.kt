package org.fmod

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

class AudioDevice {
    private var mTrack: AudioTrack? = null
    private fun fetchChannelConfigFromCount(speakerCount: Int): Int {
        return when (speakerCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> AudioFormat.CHANNEL_INVALID
        }
    }

    fun init(channelCount: Int, sampleRateInHz: Int, sampleSize: Int, sampleCount: Int): Boolean {
        val channelConfig = fetchChannelConfigFromCount(channelCount)
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRateInHz,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize < 0) {
            Log.w(
                "fmod",
                "AudioDevice::init : Couldn't query minimum buffer size, possibly unsupported sample rate or channel count"
            )
        } else {
            Log.i("fmod", "AudioDevice::init : Min buffer size: $minBufferSize bytes")
        }

        val realBufferSize = sampleSize * sampleCount * channelCount * 2
        val bufferSize = minBufferSize.coerceAtLeast(realBufferSize)
        Log.i("fmod", "AudioDevice::init : Actual buffer size: $bufferSize bytes")
        return try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .build()

            val format = AudioFormat.Builder()
                .setChannelMask(channelConfig)
                .setSampleRate(sampleRateInHz)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val audioTrackBuilder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioTrackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val audioTrack = audioTrackBuilder.build()

            mTrack = audioTrack
            try {
                audioTrack.play()
                true
            } catch (unused: IllegalStateException) {
                Log.e("fmod", "AudioDevice::init : AudioTrack play caused IllegalStateException")
                mTrack?.release()
                mTrack = null
                false
            }
        } catch (_: IllegalArgumentException) {
            Log.e("fmod", "AudioDevice::init : AudioTrack creation caused IllegalArgumentException")
            false
        }
    }

    fun close() {
        try {
            mTrack?.stop()
        } catch (unused: IllegalStateException) {
            Log.e("fmod", "AudioDevice::init : AudioTrack stop caused IllegalStateException")
        }
        mTrack?.release()
        mTrack = null
    }

    fun write(sArr: ShortArray, size: Int) {
        mTrack?.write(sArr, 0, size)
    }
}
