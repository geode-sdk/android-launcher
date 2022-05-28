package dev.xyze.geodelauncher

import dev.xyze.geodelauncher.utils.GJConstants

object LauncherFix {
    fun loadLibrary() {
        System.loadLibrary(GJConstants.LAUNCHER_FIX_LIB_NAME)
    }

    external fun setDataPath(dataPath: String)

    external fun setOriginalDataPath(dataPath: String)
}