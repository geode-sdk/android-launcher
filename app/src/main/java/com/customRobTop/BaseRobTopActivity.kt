package com.customRobTop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.*


@Suppress("unused", "UNUSED_PARAMETER")
open class BaseRobTopActivity : DefaultRobTopActivity() {
    companion object {
        private var isLoaded_ = false
        lateinit var me: Activity
        var blockBackButton_ = false
        private var keyboardActive_ = false

        fun setCurrentActivity(currentActivity: Activity) {
            me = currentActivity
        }

        @SuppressLint("HardwareIds")
        @JvmStatic
        fun getUserID(): String {
            // this is how RobTop does it in 2.2, based on the meltdown leaks
            val androidId = Settings.Secure.getString(me.contentResolver, Settings.Secure.ANDROID_ID)
            return if ("9774d56d682e549c" != androidId) {
                UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
            } else return UUID.randomUUID().toString()
        }

        @JvmStatic
        fun isNetworkAvailable(): Boolean {
            val connectivityManager = me.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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
        fun setKeyboardState(value: Boolean) {
            keyboardActive_ = value
        }

        @JvmStatic
        fun setBlockBackButton(value: Boolean) {
            blockBackButton_ = value
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
        me = this

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        hideSystemUi()
    }

    private fun hideSystemUi() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return

        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}