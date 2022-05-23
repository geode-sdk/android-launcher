package com.robtopx.geometryjump

import android.content.pm.PackageManager
import dalvik.system.BaseDexClassLoader
import java.io.File

object LaunchUtils {
    @Suppress("UNCHECKED_CAST")
    fun addDirectoryToClassPath(directory: String, classLoader: ClassLoader) {
        // uses code from zhuowei/MCPELauncher
        try {
            val clazz = classLoader.javaClass
            val plField = clazz.superclass.getDeclaredField("pathList")
            plField.isAccessible = true

            val pathListInst = plField.get(classLoader)
            val pathListClazz = pathListInst.javaClass
            val nldField = pathListClazz.getDeclaredField("nativeLibraryDirectories")
            nldField.isAccessible = true

            val pathList = nldField.get(pathListInst)

            println(pathList)

            if (pathList is ArrayList<*>) {
                // no safe way to check this type afaik, so casting is needed
                (pathList as ArrayList<File>).add(
                    File(directory)
                )
            }

            if (classLoader is BaseDexClassLoader && classLoader.findLibrary(GJConstants.COCOS_LIB_NAME) == null) {
                val anpMethod = pathListClazz.getDeclaredMethod("addNativePath", Collection::class.java)

                anpMethod.invoke(
                    pathListInst,
                    listOf(directory)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isGeometryDashInstalled(packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(GJConstants.PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}