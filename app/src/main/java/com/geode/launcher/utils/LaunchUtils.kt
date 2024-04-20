package com.geode.launcher.utils

import android.content.Context
import android.os.Build
import java.io.File

object LaunchUtils {
    // supposedly CPU_ABI returns the current arch for the running application
    // despite being deprecated, this is also one of the few ways to get this information
    @Suppress("DEPRECATION")
    val applicationArchitecture: String = Build.CPU_ABI

    val is64bit = applicationArchitecture == "arm64-v8a"

    val platformName: String = if (is64bit) "android64" else "android32"

    val geodeFilename: String = "Geode.$platformName.so"

    fun getInstalledGeodePath(context: Context): File? {
        val geodeName = geodeFilename

        val internalGeodePath = File(context.filesDir, "launcher/$geodeName")
        if (internalGeodePath.exists()) {
            return internalGeodePath
        }

        val externalGeodeDir = getBaseDirectory(context)

        val updateGeodePath = File(externalGeodeDir, "launcher/$geodeName")
        if (updateGeodePath.exists()) {
            return updateGeodePath
        }

        val externalGeodePath = File(externalGeodeDir, geodeName)
        if (externalGeodePath.exists()) {
            return externalGeodePath
        }

        return null
    }

    fun isGeodeInstalled(context: Context): Boolean {
        return getInstalledGeodePath(context) != null
    }

    /**
     * Returns the directory that Geode/the game should base itself off of.
     */
    fun getBaseDirectory(context: Context): File {
        // deprecated, but seems to be the best choice of directory (i forced mat to test it)
        // also, is getting the first item the correct choice here?? what do they mean
        @Suppress("DEPRECATION")
        val dir = context.externalMediaDirs.first()

        // prevent having resources added to system gallery
        // accessing this file every time the directory is read may be a little wasteful...
        val noMediaPath = File(dir, ".nomedia")
        noMediaPath.createNewFile()

        return dir
    }

    fun getSaveDirectory(context: Context): File {
        return File(getBaseDirectory(context), "save")
    }

    private const val CRASH_INDICATOR_NAME = "lastSessionDidCrash"

    private fun getCrashDirectory(context: Context): File {
        val base = getBaseDirectory(context)
        return File(base, "game/geode/crashlogs/")
    }

    fun getLastCrash(context: Context): File? {
        val crashDirectory = getCrashDirectory(context)
        if (!crashDirectory.exists()) {
            return null
        }

        val children = crashDirectory.listFiles {
            // ignore indicator files (including old file)
            _, name -> name != CRASH_INDICATOR_NAME && name != "last-pid"
        }

        return children?.maxByOrNull { it.lastModified() }
    }

    fun lastSessionCrashed(context: Context): Boolean {
        val base = getCrashDirectory(context)
        val crashIndicatorFile = File(base, CRASH_INDICATOR_NAME)

        return crashIndicatorFile.exists()
    }

    enum class LauncherError {
        LINKER_NEEDS_64BIT,
        LINKER_NEEDS_32BIT,
        LINKER_FAILED,
        GENERIC,
        CRASHED;

        fun isAbiFailure(): Boolean {
            return this == LINKER_NEEDS_32BIT || this == LINKER_NEEDS_64BIT
        }
    }

    const val LAUNCHER_KEY_RETURN_ERROR = "return_error"
    const val LAUNCHER_KEY_RETURN_MESSAGE = "return_message"
    const val LAUNCHER_KEY_RETURN_EXTENDED_MESSAGE = "return_extended_message"
}