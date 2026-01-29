package org.fmod

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioDeviceCallback
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
    private var gPluginAudioDeviceCallback: PluginAudioDeviceCallback? = null
    private val gPluginBroadcastReceiver = PluginBroadcastReceiver()

    external fun OutputAAudioHeadphonesChanged()
    external fun SetInputEnumerationChanged()
    external fun SetOutputEnumerationChanged()

    @JvmStatic
    fun init(context: Context?) {
        gContext = context
        if (context == null) {
            return
        }

        val intentFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        if (Build.VERSION.SDK_INT >= 34) {
            context.registerReceiver(gPluginBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(gPluginBroadcastReceiver, intentFilter)
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pluginAudioDeviceCallback = PluginAudioDeviceCallback(audioManager.getDevices(3))
        gPluginAudioDeviceCallback = pluginAudioDeviceCallback
        audioManager.registerAudioDeviceCallback(pluginAudioDeviceCallback, null)
    }

    @JvmStatic
    fun close() {
        val context = gContext
        if (context != null) {
            context.unregisterReceiver(gPluginBroadcastReceiver)
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .unregisterAudioDeviceCallback(gPluginAudioDeviceCallback)
        }
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
    fun getAudioDevices(i: Int): Array<AudioDeviceInfo?>? {
        val context = gContext ?: return emptyArray()
        return (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(i)
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

    internal class PluginAudioDeviceCallback : AudioDeviceCallback {
        val deviceSet: HashSet<Int> = HashSet()

        constructor(audioDeviceInfoArr: Array<out AudioDeviceInfo?>?) {
            if (audioDeviceInfoArr != null) {
                deviceSet.addAll(audioDeviceInfoArr.mapNotNull { it?.id })
            }
        }

        override fun onAudioDevicesAdded(audioDeviceInfoArr: Array<out AudioDeviceInfo?>?) {
            var z: Boolean
            var i = 0
            if (audioDeviceInfoArr != null) {
                var i2 = 0
                z = false
                while (i < audioDeviceInfoArr.size) {
                    if (!deviceSet.contains(audioDeviceInfoArr[i]!!.id)) {
                        if (audioDeviceInfoArr[i]!!.isSource) {
                            i2 = 1
                        }
                        if (audioDeviceInfoArr[i]!!.isSink) {
                            z = true
                        }
                        deviceSet.add(audioDeviceInfoArr[i]!!.id)
                    }
                    i++
                }
                i = i2
            } else {
                z = false
            }
            if (i != 0) {
                SetInputEnumerationChanged()
            }
            if (z) {
                SetOutputEnumerationChanged()
            }
        }

        override fun onAudioDevicesRemoved(audioDeviceInfoArr: Array<out AudioDeviceInfo?>?) {
            var z: Boolean
            var i = 0
            if (audioDeviceInfoArr != null) {
                var i2 = 0
                z = false
                while (i < audioDeviceInfoArr.size) {
                    if (deviceSet.contains(audioDeviceInfoArr[i]!!.id)) {
                        if (audioDeviceInfoArr[i]!!.isSource) {
                            i2 = 1
                        }
                        if (audioDeviceInfoArr[i]!!.isSink) {
                            z = true
                        }
                        deviceSet.remove(audioDeviceInfoArr[i]!!.id)
                    }
                    i++
                }
                i = i2
            } else {
                z = false
            }
            if (i != 0) {
                SetInputEnumerationChanged()
            }
            if (z) {
                SetOutputEnumerationChanged()
            }
        }
    }
}
