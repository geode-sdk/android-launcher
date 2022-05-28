package org.cocos2dx.lib

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process


@Suppress("unused", "KotlinJniMissingFunction")
object Cocos2dxHelper {
//    private val sCocos2dMusic: Cocos2dxMusic? = null
//    private val sCocos2dSound: Cocos2dxSound? = null
//    private val sAssetManager: AssetManager? = null
//    private val sCocos2dxAccelerometer: Cocos2dxAccelerometer? = null
//    private val sAccelerometerEnabled = false
    private var sPackageName: String? = null
    private var sFileDirectory: String? = null

//    private var sContext: Context? = null
    private var sCocos2dxHelperListener: Cocos2dxHelperListener? = null

    @JvmStatic
    external fun nativeSetApkPath(pApkPath: String)

    @JvmStatic
    private external fun nativeSetEditTextDialogResult(pBytes: ByteArray)

    @JvmStatic
    fun getCocos2dxWritablePath(): String? {
        return sFileDirectory
    }

    @JvmStatic
    fun terminateProcess() {
        Process.killProcess(Process.myPid())
    }

    fun init(pContext: Context, pCocos2dxHelperListener: Cocos2dxHelperListener) {
        val applicationInfo: ApplicationInfo = pContext.applicationInfo
//        sContext = pContext
        sCocos2dxHelperListener = pCocos2dxHelperListener
        sPackageName = applicationInfo.packageName
        sFileDirectory = pContext.filesDir.absolutePath

        nativeSetApkPath(applicationInfo.sourceDir)
//        Cocos2dxHelper.sCocos2dxAccelerometer = Cocos2dxAccelerometer(pContext)
//        Cocos2dxHelper.sCocos2dMusic = Cocos2dxMusic(pContext)
//        var simultaneousStreams: Int = Cocos2dxSound.MAX_SIMULTANEOUS_STREAMS_DEFAULT
//        if (Cocos2dxHelper.getDeviceModel().indexOf("GT-I9100") !== -1) {
//            simultaneousStreams = Cocos2dxSound.MAX_SIMULTANEOUS_STREAMS_I9100
//        }
//        Cocos2dxHelper.sCocos2dSound = Cocos2dxSound(pContext, simultaneousStreams)
//        Cocos2dxHelper.sAssetManager = pContext.getAssets()
        Cocos2dxBitmap.setContext(pContext)
//            Cocos2dxETCLoader.setContext(pContext)
    }

    interface Cocos2dxHelperListener {
        fun runOnGLThread(pRunnable: Runnable)

        fun showDialog(pTitle: String, pMessage: String)

        fun showEditTextDialog(
            pTitle: String,
            pMessage: String,
            pInputMode: Int,
            pInputFlag: Int,
            pReturnType: Int,
            pMaxLength: Int
        )
    }
}