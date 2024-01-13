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

    const val SUPPORTED_VERSION_CODE = 37L
    const val SUPPORTED_VERSION_STRING = "2.2.13"
}
