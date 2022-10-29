package com.geode.geodelauncher

import com.geode.geodelauncher.utils.GJConstants

object LauncherFix {
    fun loadLibrary() {
        System.loadLibrary(GJConstants.LAUNCHER_FIX_LIB_NAME)
    }

    external fun setDataPath(dataPath: String)

    external fun setOriginalDataPath(dataPath: String)
}