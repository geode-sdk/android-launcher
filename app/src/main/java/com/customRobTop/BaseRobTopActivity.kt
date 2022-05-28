package com.customRobTop

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.*
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.customRobTop.JniToCpp.resumeSound
import org.cocos2dx.lib.Cocos2dxGLSurfaceView.Companion.closeIMEKeyboard
import org.cocos2dx.lib.Cocos2dxGLSurfaceView.Companion.openIMEKeyboard
import java.lang.ref.WeakReference
import java.util.*


@Suppress("unused", "UNUSED_PARAMETER")
open class BaseRobTopActivity : DefaultRobTopActivity() {
    companion object {
        private var isLoaded_ = false
        var blockBackButton_ = false
        private var keyboardActive_ = false

        private var isPaused_ = false

        lateinit var me: WeakReference<Activity>
        private var receiver_: BroadcastReceiver? = null
        private var shouldResumeSound_ = true

        fun setCurrentActivity(currentActivity: Activity) {
            me = WeakReference(currentActivity)
        }

        @JvmStatic
        fun onNativePause() {}

        @JvmStatic
        fun onNativeResume() {}

        @SuppressLint("HardwareIds")
        @JvmStatic
        fun getUserID(): String {
            // this is how RobTop does it in 2.2, based on the meltdown leaks
            val androidId = Settings.Secure.getString(me.get()?.contentResolver, Settings.Secure.ANDROID_ID)
            return if ("9774d56d682e549c" != androidId) {
                UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
            } else return UUID.randomUUID().toString()
        }

        @JvmStatic
        fun isNetworkAvailable(): Boolean {
            val connectivityManager = me.get()?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true
            }

            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true
            }

            return false
        }

        @JvmStatic
        fun openURL(url: String) {
            Log.d("MAIN", "Open URL")
            me.get()?.runOnUiThread {
                me.get()?.startActivity(
                    Intent(
                        "android.intent.action.VIEW",
                        Uri.parse(url)
                    )
                )
            }
        }

        @JvmStatic
        fun sendMail(subject: String, body: String, to: String) {
            me.get()?.runOnUiThread {
                val i = Intent("android.intent.action.SEND")
                i.type = "message/rfc822"
                i.putExtra("android.intent.extra.EMAIL", arrayOf(to))
                i.putExtra("android.intent.extra.SUBJECT", subject)
                i.putExtra("android.intent.extra.TEXT", body)
                try {
                    me.get()?.startActivity(Intent.createChooser(i, "Send mail..."))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        me.get(),
                        "There are no email clients installed.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        @JvmStatic
        fun shouldResumeSound(): Boolean {
            return shouldResumeSound_
        }

        @JvmStatic
        fun setKeyboardState(value: Boolean) {
            keyboardActive_ = value
        }

        @JvmStatic
        fun setBlockBackButton(value: Boolean) {
            blockBackButton_ = value
        }

        @JvmStatic
        fun onToggleKeyboard() {
            me.get()?.runOnUiThread {
                if (keyboardActive_) {
                    openIMEKeyboard()
                } else {
                    closeIMEKeyboard()
                }
            }
        }

        @JvmStatic
        fun loadingFinished() {
            isLoaded_ = true
        }

        @JvmStatic
        fun gameServicesIsSignedIn(): Boolean {
            return false
        }

        // everyplay doesn't even exist anymore lol
        @JvmStatic
        fun isEveryplaySupported(): Boolean {
            return false
        }

        @JvmStatic
        fun isRecordingSupported(): Boolean {
            return false
        }

        @JvmStatic
        fun stopRecording() { }

        @JvmStatic
        fun unlockAchievement(id: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        me = WeakReference(this)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        hideSystemUi()
    }

    open fun registerReceiver() {
        if (receiver_ != null) {
            me.get()?.unregisterReceiver(receiver_)
            receiver_ = null
        }
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_USER_PRESENT)
            receiver_ = ReceiverScreen()
            registerReceiver(receiver_, filter)
        } catch (e: Exception) {
        }
    }

    private fun hideSystemUi() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return

        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    class ReceiverScreen : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("TAG", "ACTION_SCREEN_ON")
                    if (!(me.get()?.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
                        shouldResumeSound_ = true
                    }
                    if (!isPaused_ && shouldResumeSound_) {
                        resumeSound()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    shouldResumeSound_ = false
                }
                Intent.ACTION_USER_PRESENT -> {
                    shouldResumeSound_ = true
                    if (!isPaused_) {
                        resumeSound()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isPaused_ = false
    }

    /* access modifiers changed from: protected */
    override fun onPause() {
        super.onPause()
        isPaused_ = true
    }
}