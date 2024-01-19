package org.fmod

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.FileNotFoundException

// i never agreed to your licenses! take that
@SuppressLint("StaticFieldLeak")
object FMOD {
    private var gContext: Context? = null
    private val gPluginBroadcastReceiver = PluginBroadcastReceiver()

    external fun OutputAAudioHeadphonesChanged()

    @JvmStatic
    fun init(context: Context?) {
        gContext = context
        gContext?.registerReceiver(
            gPluginBroadcastReceiver,
            IntentFilter(Intent.ACTION_HEADSET_PLUG)
        )
    }

    @JvmStatic
    fun close() {
        val context = gContext
        context?.unregisterReceiver(gPluginBroadcastReceiver)
        gContext = null
    }

    @JvmStatic
    fun checkInit(): Boolean {
        return gContext != null
    }

    @JvmStatic
    fun getAssetManager(): AssetManager? {
        val context = gContext
        return context?.assets
    }

    @JvmStatic
    fun supportsLowLatency(): Boolean {
        val outputBlockSize = getOutputBlockSize()
        val lowLatencyFlag = lowLatencyFlag()
        val proAudioFlag = proAudioFlag()
        val z = outputBlockSize in 1..1024
        val isBluetoothOn = isBluetoothOn()
        Log.i(
            "fmod",
            "FMOD::supportsLowLatency : Low latency = $lowLatencyFlag, Pro Audio = $proAudioFlag, Bluetooth On = $isBluetoothOn, Acceptable Block Size = $z ($outputBlockSize)"
        )
        return z && lowLatencyFlag && !isBluetoothOn
    }

    @JvmStatic
    fun lowLatencyFlag(): Boolean {
        val context = gContext ?: return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
    }

    @JvmStatic
    fun proAudioFlag(): Boolean {
        val context = gContext ?: return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
    }

    @JvmStatic
    fun supportsAAudio(): Boolean {
        return Build.VERSION.SDK_INT >= 27
    }

    @JvmStatic
    fun getOutputSampleRate(): Int {
        val context = gContext ?: return 0
        val audioService = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val property = audioService.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)

        return property?.toInt() ?: 0
    }

    @JvmStatic
    fun getOutputBlockSize(): Int {
        val context = gContext ?: return 0
        val audioService = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val property = audioService.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)

        return property?.toInt() ?: 0
    }

    @JvmStatic
    fun isBluetoothOn(): Boolean {
        val context = gContext ?: return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    @JvmStatic
    fun fileDescriptorFromUri(str: String?): Int {
        gContext?.apply {
            try {
                contentResolver.openFileDescriptor(Uri.parse(str), "r")?.apply {
                    return detachFd()
                }
            } catch (_: FileNotFoundException) { }
        }

        return -1
    }

    internal class PluginBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            OutputAAudioHeadphonesChanged()
        }
    }
}
