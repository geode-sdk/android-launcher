package org.cocos2dx.lib

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import com.customRobTop.BaseRobTopActivity


@Suppress("unused", "KotlinJniMissingFunction")
object Cocos2dxHelper {
//    private val sCocos2dMusic: Cocos2dxMusic? = null
//    private val sCocos2dSound: Cocos2dxSound? = null
//    private val sAssetManager: AssetManager? = null
//    private val sCocos2dxAccelerometer: Cocos2dxAccelerometer? = null
//    private val sAccelerometerEnabled = false
    private var packageName: String? = null
    private var fileDirectory: String? = null

//    private var sContext: Context? = null
    private var cocos2dxHelperListener: Cocos2dxHelperListener? = null

    @JvmStatic
    external fun nativeSetApkPath(apkPath: String)

    @JvmStatic
    private external fun nativeSetEditTextDialogResult(bytes: ByteArray)

    @JvmStatic
    fun getCocos2dxWritablePath(): String? {
        return fileDirectory
    }

    @JvmStatic
    fun terminateProcess() {
        Process.killProcess(Process.myPid())
    }

    @JvmStatic
    fun getDPI(): Int {
        return BaseRobTopActivity.me.get()?.resources?.configuration?.densityDpi ?: -1
    }

    fun init(context: Context, cocos2dxHelperListener: Cocos2dxHelperListener) {
        val applicationInfo: ApplicationInfo = context.applicationInfo
//        sContext = pContext
        this.cocos2dxHelperListener = cocos2dxHelperListener
        packageName = applicationInfo.packageName
        fileDirectory = context.filesDir.absolutePath

        nativeSetApkPath(applicationInfo.sourceDir)
//        Cocos2dxHelper.sCocos2dxAccelerometer = Cocos2dxAccelerometer(pContext)
//        Cocos2dxHelper.sCocos2dMusic = Cocos2dxMusic(pContext)
//        var simultaneousStreams: Int = Cocos2dxSound.MAX_SIMULTANEOUS_STREAMS_DEFAULT
//        if (Cocos2dxHelper.getDeviceModel().indexOf("GT-I9100") !== -1) {
//            simultaneousStreams = Cocos2dxSound.MAX_SIMULTANEOUS_STREAMS_I9100
//        }
//        Cocos2dxHelper.sCocos2dSound = Cocos2dxSound(pContext, simultaneousStreams)
//        Cocos2dxHelper.sAssetManager = pContext.getAssets()
        Cocos2dxBitmap.setContext(context)
//            Cocos2dxETCLoader.setContext(pContext)
    }

    interface Cocos2dxHelperListener {
        fun runOnGLThread(runnable: Runnable)

        fun showDialog(title: String, message: String)

        fun showEditTextDialog(
            title: String,
            message: String,
            inputMode: Int,
            inputFlag: Int,
            returnType: Int,
            maxLength: Int
        )
    }
}