package com.geode.launcher.utils

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.Context
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

    fun getInstalledGeodePath(context: Context): File? {
        val internalGeodePath = File(context.filesDir.path, "launcher/Geode.so")
        if (internalGeodePath.exists()) {
            return internalGeodePath
        }
        context.getExternalFilesDir(null)?.let { dir->
            val updateGeodePath = File(dir.path, "game/geode/update/Geode.so")
            if (updateGeodePath.exists()) {
                return updateGeodePath
            }

            val externalGeodePath = File(dir.path, "Geode.so")
            if (externalGeodePath.exists()) {
                return externalGeodePath
            }
        }
        return null
    }

    fun isGeodeInstalled(context: Context): Boolean {
        return getInstalledGeodePath(context) != null
    }

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