package dev.xyze.geodelauncher

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.customRobTop.BaseRobTopActivity
import com.customRobTop.JniToCpp
import dev.xyze.geodelauncher.utils.GJConstants
import dev.xyze.geodelauncher.utils.LaunchUtils
import org.cocos2dx.lib.Cocos2dxEditText
import org.cocos2dx.lib.Cocos2dxGLSurfaceView
import org.cocos2dx.lib.Cocos2dxHelper
import org.cocos2dx.lib.Cocos2dxRenderer
import org.fmod.FMOD


class GeometryDashActivity : ComponentActivity(), Cocos2dxHelper.Cocos2dxHelperListener {
    private var mGLSurfaceView: Cocos2dxGLSurfaceView? = null
    private val sTag = GeometryDashActivity::class.simpleName
    private var mIsRunning = false
    private var mIsOnPause = false
    private var mHasWindowFocus = false

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        setupUIState()

        // return back to main if Geometry Dash isn't found
        if (!LaunchUtils.isGeometryDashInstalled(packageManager)) {
            val launchIntent = Intent(this, MainActivity::class.java)
            launchIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

            startActivity(launchIntent)
        }

        val gdPackageInfo = packageManager.getPackageInfo(GJConstants.PACKAGE_NAME, 0)
        val gdNativeLibraryPath = "${gdPackageInfo.applicationInfo.nativeLibraryDir}/"

        try {
            LaunchUtils.addAssetsFromPackage(assets, gdPackageInfo)
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        }

        System.load("$gdNativeLibraryPath/lib${GJConstants.FMOD_LIB_NAME}.so")
        System.load("$gdNativeLibraryPath/lib${GJConstants.COCOS_LIB_NAME}.so")

        val loadSuccess = try {
            System.loadLibrary(GJConstants.MOD_CORE_LIB_NAME)
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }

        FMOD.init(this)

        super.onCreate(savedInstanceState)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val frameLayoutParams = ViewGroup.LayoutParams(-1, -1)
                    val frameLayout = FrameLayout(this)
                    frameLayout.layoutParams = frameLayoutParams
                    val editTextLayoutParams = ViewGroup.LayoutParams(-1, -2)
                    val editText = Cocos2dxEditText(this)
                    editText.layoutParams = editTextLayoutParams
                    frameLayout.addView(editText)

                    val glSurfaceView = Cocos2dxGLSurfaceView(this)

                    this.mGLSurfaceView = glSurfaceView
                    frameLayout.addView(this.mGLSurfaceView)

                    glSurfaceView.setEGLConfigChooser(5, 6, 5, 0, 16, 8)

                    if (isAndroidEmulator()) {
                        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    }

                    glSurfaceView.initView()
                    glSurfaceView.setCocos2dxRenderer(Cocos2dxRenderer())

                    editText.inputType = 145
                    glSurfaceView.cocos2dxEditText = editText

                    frameLayout
                }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.fillMaxWidth()
            )

            if (!loadSuccess) {
                LaunchedEffect(snackbarHostState) {
                    snackbarHostState.showSnackbar(getString(R.string.failed_mod_core))
                }
            }
        }

        Cocos2dxHelper.init(this, this)

        JniToCpp.setupHSSAssets(
            gdPackageInfo.applicationInfo.sourceDir,
            Environment.getExternalStorageDirectory().absolutePath
        )
        Cocos2dxHelper.nativeSetApkPath(gdPackageInfo.applicationInfo.sourceDir)

        BaseRobTopActivity.setCurrentActivity(this)
        (BaseRobTopActivity.me as? BaseRobTopActivity)?.registerReceiver()
    }

    private fun setupUIState() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        hideSystemUi()
    }

    private fun hideSystemUi() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return

        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        super.onDestroy()
        FMOD.close()
    }

    private fun resumeGame() {
        mIsRunning = true
//        Cocos2dxHelper.onResume()
        mGLSurfaceView?.onResume()
    }

    private fun pauseGame() {
        mIsRunning = false
//        Cocos2dxHelper.onPause()
        mGLSurfaceView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mIsOnPause = false
        if (mHasWindowFocus && !this.mIsRunning) {
            resumeGame()
        }
    }

    override fun onPause() {
        super.onPause()
        mIsOnPause = true
        if (mIsRunning) {
            pauseGame()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        mHasWindowFocus = hasWindowFocus
        if (hasWindowFocus && !mIsOnPause) {
            resumeGame()
        }
    }

    override fun runOnGLThread(pRunnable: Runnable) {
        TODO("Not yet implemented")
    }

    override fun showDialog(pTitle: String, pMessage: String) {
        TODO("Not yet implemented")
    }

    override fun showEditTextDialog(
        pTitle: String,
        pMessage: String,
        pInputMode: Int,
        pInputFlag: Int,
        pReturnType: Int,
        pMaxLength: Int
    ) {
        TODO("Not yet implemented")
    }


    private fun isAndroidEmulator(): Boolean {
        Log.d(sTag, "model=" + Build.MODEL)
        val product = Build.PRODUCT
        Log.d(sTag, "product=$product")
        var isEmulator = false
        if (product != null) {
            isEmulator = product == "sdk" || product.contains("_sdk") || product.contains("sdk_")
        }
        Log.d(sTag, "isEmulator=$isEmulator")
        return isEmulator
    }
}