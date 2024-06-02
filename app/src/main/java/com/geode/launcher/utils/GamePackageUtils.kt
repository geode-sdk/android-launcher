package com.geode.launcher.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import okio.ByteString.Companion.toByteString

object GamePackageUtils {
    fun isGameInstalled(packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getGameVersionCode(packageManager: PackageManager): Long {
        val game = packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            game.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            game.versionCode.toLong()
        }
    }

    fun getGameVersionString(packageManager: PackageManager): String {
        val game = packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)
        return game.versionName
    }

    private val gameVersionMap = mapOf(
        37L to "2.200",
        38L to "2.205",
        39L to "2.206"
    )

    /**
     * Gets the version name that more closely aligns to the Windows/macOS releases
     */
    fun getUnifiedVersionName(packageManager: PackageManager): String {
        val versionCode = getGameVersionCode(packageManager)
        return gameVersionMap[versionCode] ?: getGameVersionString(packageManager)
    }

    fun detectAbiMismatch(context: Context, packageInfo: PackageInfo, loadException: Error): Boolean {
        val abi = LaunchUtils.applicationArchitecture
        val isExtracted =
            packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS == ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS
        val isSplit = (packageInfo.applicationInfo.splitSourceDirs?.size ?: 0) > 1

        val metadata =
            "Geometry Dash metadata:\nSplit sources: $isSplit\nExtracted libraries: $isExtracted\nLauncher architecture: $abi"
        Log.i("GeodeLauncher", metadata)

        // easiest check! these messages are hardcoded in bionic/linker/linker_phdr.cpp
        if (loadException.message?.contains("32-bit instead of 64-bit") == true) {
            return true
        }

        if (loadException.message?.contains("64-bit instead of 32-bit") == true) {
            return true
        }

        // determine if it can find gd libraries for the opposite architecture
        val gdBinaryName = "lib${Constants.COCOS_LIB_NAME}.so"
        val oppositeArchitecture = if (abi == "arm64-v8a") "armeabi-v7a" else "arm64-v8a"

        // detect issues using native library directory (only works on extracted libraries)
        val nativeLibraryDir = packageInfo.applicationInfo.nativeLibraryDir

        if (nativeLibraryDir.contains(oppositeArchitecture)) {
            return true
        }

        // android has some odd behavior where 32bit libraries get installed to the arm directory instead of armeabi-v7a
        // detect it, ig
        if (oppositeArchitecture == "armeabi-v7a" && nativeLibraryDir.contains("/lib/arm/")) {
            return true
        }

        if (!isExtracted) {
            try {
                context.assets.openNonAssetFd("lib/$oppositeArchitecture/$gdBinaryName")
                return true
            } catch (_: Exception) {
                // this is good, actually!
            }
        }

        return false
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

    private const val GAME_CERTIFICATE_HASH = "f5e7d8284d72c461a5f022d0cf755df101c3bb1e69cbe241bc1aef2cc5610a43"

    private fun validateCertificate(certificate: ByteArray): Boolean =
        certificate.toByteString().sha256().hex() == GAME_CERTIFICATE_HASH

    fun identifyGameLegitimacy(packageManager: PackageManager): Boolean {
        @Suppress("DEPRECATION")
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val game = packageManager.getPackageInfo(Constants.PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = game.signingInfo

            signingInfo.signingCertificateHistory
        } else {
            val game = packageManager.getPackageInfo(Constants.PACKAGE_NAME, PackageManager.GET_SIGNATURES)
            game.signatures
        }

        return certificates.any {
            validateCertificate(it.toByteArray())
        }
    }
}