package com.geode.launcher.utils

import android.annotation.SuppressLint

object Constants {
    const val PACKAGE_NAME = "com.robtopx.geometryjump"

    const val COCOS_LIB_NAME = "cocos2dcpp"
    const val FMOD_LIB_NAME = "fmod"
    const val LAUNCHER_FIX_LIB_NAME = "launcherfix"

    // this value is hardcoded into GD
    @SuppressLint("SdCardPath")
    const val GJ_DATA_DIR = "/data/data/${PACKAGE_NAME}"

    // anything below this version code is blocked from opening the game
    const val SUPPORTED_VERSION_CODE_MIN = 37L

    // anything below this version code shows a warning on the main menu
    const val SUPPORTED_VERSION_CODE_MIN_WARNING = 40L

    // anything above this version code will show a warning when starting the game
    const val SUPPORTED_VERSION_CODE = 41L

    const val SUPPORTED_VERSION_STRING = "2.2.144"
}
