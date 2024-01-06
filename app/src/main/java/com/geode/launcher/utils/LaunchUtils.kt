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

    fun getApplicationArchitecture(): String {
        // supposedly CPU_ABI returns the current arch for the running application
        // despite being deprecated, this is also one of the few ways to get this information
        @Suppress("DEPRECATION")
        return Build.CPU_ABI
    }

    fun getGeodeFilename(): String {
        val abi = getApplicationArchitecture()
        return "Geode.$abi.so"
    }

    fun getInstalledGeodePath(context: Context): File? {
        val geodeName = getGeodeFilename()

        val internalGeodePath = File(context.filesDir.path, "launcher/$geodeName")
        if (internalGeodePath.exists()) {
            return internalGeodePath
        }
        context.getExternalFilesDir(null)?.let { dir->
            val updateGeodePath = File(dir.path, "game/geode/update/$geodeName")
            if (updateGeodePath.exists()) {
                return updateGeodePath
            }

            val externalGeodePath = File(dir.path, geodeName)
            if (externalGeodePath.exists()) {
                return externalGeodePath
            }
        }
        return null
    }

    fun isGeodeInstalled(context: Context): Boolean {
        return getInstalledGeodePath(context) != null
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