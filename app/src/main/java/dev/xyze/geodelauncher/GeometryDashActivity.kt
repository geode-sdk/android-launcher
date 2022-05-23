package dev.xyze.geodelauncher

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
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
import com.robtopx.geometryjump.GJConstants
import com.robtopx.geometryjump.LaunchUtils
import dev.xyze.geodelauncher.ui.theme.GeodeLauncherTheme
import org.cocos2dx.lib.Cocos2dxGLSurfaceView
import org.cocos2dx.lib.Cocos2dxHelper
import org.cocos2dx.lib.Cocos2dxRenderer
import org.fmod.FMOD

class GeometryDashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setupUIState()

        val gdPackageInfo = packageManager.getPackageInfo(GJConstants.PACKAGE_NAME, 0)
        val gdNativeLibraryPath = "${gdPackageInfo.applicationInfo.nativeLibraryDir}/"

        LaunchUtils.addDirectoryToClassPath(gdNativeLibraryPath, classLoader)

        System.loadLibrary(GJConstants.FMOD_LIB_NAME)
        System.loadLibrary(GJConstants.COCOS_LIB_NAME)

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

            GeodeLauncherTheme {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                    Cocos2dxGLSurfaceView(this).apply {
                            setEGLConfigChooser(5, 6, 5, 0, 16, 8)
                            initView()
                            setCocos2dxRenderer(Cocos2dxRenderer())
                        }
                    }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!loadSuccess) {
                LaunchedEffect(snackbarHostState) {
                    snackbarHostState.showSnackbar("Failed to load mod core.")
                }
            }
        }


        JniToCpp.setupHSSAssets(
            applicationInfo.sourceDir,
            Environment.getExternalStorageDirectory().absolutePath
        )
        Cocos2dxHelper.nativeSetApkPath(gdPackageInfo.applicationInfo.sourceDir)

        BaseRobTopActivity.setCurrentActivity(this)
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
}