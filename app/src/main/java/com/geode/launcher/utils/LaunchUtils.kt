package com.geode.launcher.utils

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.Context
import android.os.Build
import java.io.File

object LaunchUtils {
    fun isGeometryDashInstalled(packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getGeometryDashVersionCode(packageManager: PackageManager): Long {
        val game = packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            game.longVersionCode
        } else {
            game.versionCode.toLong()
        }
    }

    fun getGeometryDashVersionString(packageManager: PackageManager): String {
        val game = packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)
        return game.versionName
    }

    // supposedly CPU_ABI returns the current arch for the running application
    // despite being deprecated, this is also one of the few ways to get this information
    @Suppress("DEPRECATION")
    val applicationArchitecture: String = Build.CPU_ABI

    val platformName: String = if (applicationArchitecture == "arm64-v8a")
        "android64" else "android32"

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

    @SuppressLint("DiscouragedPrivateApi")
    fun addAssetsFromPackage(assetManager: AssetManager, packageInfo: PackageInfo) {
        // this method is officially marked as deprecated but it is the only method we are allowed to reflect
        // (the source recommends replacing with AssetManager.setApkAssets(ApkAssets[], boolean) lol)
        val clazz = assetManager.javaClass
        val aspMethod = clazz.getDeclaredMethod("addAssetPath", String::class.java)

        aspMethod.invoke(assetManager, packageInfo.applicationInfo.sourceDir)
        packageInfo.applicationInfo.splitSourceDirs?.forEach {
            aspMethod.invoke(assetManager, it)
        }
    }
}