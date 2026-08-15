package com.geode.launcher

import android.app.Activity
import android.view.Surface
import androidx.annotation.Keep
import com.geode.launcher.utils.Constants

@Keep
object LauncherFix {
    fun loadLibrary() {
        System.loadLibrary(Constants.LAUNCHER_FIX_LIB_NAME)
    }

    external fun setDataPath(dataPath: String)

    external fun setOriginalDataPath(dataPath: String)

    external fun enableExceptionsRenaming()

    external fun performPatches()

    external fun initFramePacing(activity: Activity)
    external fun destroyFramePacing()
    external fun swapFrame(display: Long, surface: Long): Boolean
    external fun setSurface(surface: Surface?)
    external fun setSwapInterval(intervalNs: Long)
}