package org.fmod

import android.media.AudioTrack
import android.util.Log

class AudioDevice {
    private var mTrack: AudioTrack? = null
    private fun fetchChannelConfigFromCount(i: Int): Int {
        if (i == 1) {
            return 2
        }
        if (i == 2) {
            return 3
        }
        if (i == 6) {
            return 252
        }
        return if (i == 8) 6396 else 0
    }

    fun init(i: Int, i2: Int, i3: Int, i4: Int): Boolean {
        val fetchChannelConfigFromCount = fetchChannelConfigFromCount(i)
        val minBufferSize = AudioTrack.getMinBufferSize(i2, fetchChannelConfigFromCount, 2)
        if (minBufferSize < 0) {
            Log.w(
                "fmod",
                "AudioDevice::init : Couldn't query minimum buffer size, possibly unsupported sample rate or channel count"
            )
        } else {
            Log.i("fmod", "AudioDevice::init : Min buffer size: $minBufferSize bytes")
        }
        val i5 = i3 * i4 * i * 2
        val i6 = if (i5 > minBufferSize) i5 else minBufferSize
        Log.i("fmod", "AudioDevice::init : Actual buffer size: $i6 bytes")
        return try {
            val audioTrack = AudioTrack(3, i2, fetchChannelConfigFromCount, 2, i6, 1)
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
        } catch (unused2: IllegalArgumentException) {
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

    fun write(sArr: ShortArray?, i: Int) {
        mTrack!!.write(sArr!!, 0, i)
    }
}
