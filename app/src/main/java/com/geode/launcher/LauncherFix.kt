package com.geode.launcher

import com.geode.launcher.utils.Constants

object LauncherFix {
    fun loadLibrary() {
        System.loadLibrary(Constants.LAUNCHER_FIX_LIB_NAME)
    }

    external fun setDataPath(dataPath: String)

    external fun setOriginalDataPath(dataPath: String)

    external fun loadLibraryFromOffset(libraryName: String, fd: Int, offset: Long): Boolean
}