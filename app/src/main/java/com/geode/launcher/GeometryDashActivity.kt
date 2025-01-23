package com.geode.launcher

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.customRobTop.BaseRobTopActivity
import com.customRobTop.JniToCpp
import com.geode.launcher.main.LaunchNotification
import com.geode.launcher.main.determineDisplayedCards
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.ConstrainedFrameLayout
import com.geode.launcher.utils.DownloadUtils
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.GeodeUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import org.cocos2dx.lib.Cocos2dxEditText
import org.cocos2dx.lib.Cocos2dxGLSurfaceView
import org.cocos2dx.lib.Cocos2dxHelper
import org.cocos2dx.lib.Cocos2dxRenderer
import org.fmod.FMOD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

enum class DisplayMode {
    DEFAULT, LIMITED, FULLSCREEN;

    companion object {
        fun fromInt(i: Int) = when (i) {
            1 -> LIMITED
            2 -> FULLSCREEN
            else -> DEFAULT
        }
    }
}

fun ratioForPreference(value: String) = when (value) {
    "4_3" -> 4/3.0f
    "1_1" -> 1.0f
    "16_10" -> 16/10.0f
    else -> 1.77f
}

class GeometryDashActivity : AppCompatActivity(), Cocos2dxHelper.Cocos2dxHelperListener, GeodeUtils.CapabilityListener {
    private var mGLSurfaceView: Cocos2dxGLSurfaceView? = null
    private var mIsRunning = false
    private var mIsOnPause = false
    private var mHasWindowFocus = false

    private var displayMode = DisplayMode.DEFAULT
    private var mChosenDisplayRate: Float? = null
    private var mLimitedRefreshRate: Int? = null
    private var mAspectRatio = 0.0f
    private var mScreenZoom = 1.0f
    private var mScreenZoomFit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setupUIState()
        FMOD.init(this)

        super.onCreate(savedInstanceState)

        // return back to main if Geometry Dash isn't found
        if (!GamePackageUtils.isGameInstalled(packageManager)) {
            returnToMain()
            return
        }

        try {
            createVersionFile()
            tryLoadGame()
        } catch (e: UnsatisfiedLinkError) {
            Log.e("GeodeLauncher", "Library linkage failure", e)

            // generates helpful information for use in debugging library load failures
            val gdPackageInfo = packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)
            val abiMismatch = GamePackageUtils.detectAbiMismatch(this, gdPackageInfo, e)

            val is64bit = LaunchUtils.is64bit
            val errorMessage = when {
                abiMismatch && is64bit -> LaunchUtils.LauncherError.LINKER_NEEDS_32BIT
                abiMismatch -> LaunchUtils.LauncherError.LINKER_NEEDS_64BIT
                e.message?.contains("__gxx_personality_v0") == true ->
                    LaunchUtils.LauncherError.LINKER_FAILED_STL
                else -> LaunchUtils.LauncherError.LINKER_FAILED
            }

            returnToMain(errorMessage, e.message, e.stackTraceToString())

            return
        } catch (e: Exception) {
            Log.e("GeodeLauncher", "Uncaught error during game load", e)

            returnToMain(
                LaunchUtils.LauncherError.GENERIC,
                e.message ?: "Unknown Exception",
                e.stackTraceToString()
            )

            return
        }
    }

    private fun createVersionFile() {
        val versionPath = File(filesDir, "game_version.txt")
        val gameVersion = GamePackageUtils.getGameVersionCode(packageManager)

        versionPath.writeText("$gameVersion")
    }

    private fun returnToMain(
        error: LaunchUtils.LauncherError? = null,
        returnMessage: String? = null,
        returnExtendedMessage: String? = null
    ) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

            if (error != null && !returnMessage.isNullOrEmpty()) {
                putExtra(LaunchUtils.LAUNCHER_KEY_RETURN_ERROR, error)
                putExtra(LaunchUtils.LAUNCHER_KEY_RETURN_MESSAGE, returnMessage)
                putExtra(LaunchUtils.LAUNCHER_KEY_RETURN_EXTENDED_MESSAGE, returnExtendedMessage)
            }
        }

        startActivity(launchIntent)
    }

    private fun tryLoadGame() {
        val gdPackageInfo = packageManager.getPackageInfo(Constants.PACKAGE_NAME, 0)

        setupRedirection(gdPackageInfo)

        Cocos2dxHelper.init(this, this)

        GeodeUtils.setContext(this)
        GeodeUtils.setCapabilityListener(this)

        tryLoadLibrary(gdPackageInfo, Constants.FMOD_LIB_NAME)
        tryLoadLibrary(gdPackageInfo, Constants.COCOS_LIB_NAME)

        if (GamePackageUtils.getGameVersionCode(packageManager) >= 39L) {
            /*
            val customSymbols = PreferenceUtils.get(this).getBoolean(PreferenceUtils.Key.CUSTOM_SYMBOL_LIST)
            if (customSymbols) {
                val symbolFile = File(LaunchUtils.getBaseDirectory(this), "exception_symbols.txt")
                LauncherFix.enableCustomSymbolList(symbolFile.path)
            }
            */

            // this fix requires geode v3, which is 2.206+
            // there is a short period in which 2.206 users will still have geode v2, but whatever. ig
            LauncherFix.performExceptionsRenaming()
        }

        loadInternalMods()

        setContentView(createView())

        setupPostLibraryLoad(gdPackageInfo)

        try {
            loadGeodeLibrary()
        } catch (e: UnsatisfiedLinkError) {
            handleGeodeException(e)
        } catch (e: Exception) {
            handleGeodeException(e)
        }
    }

    private fun handleGeodeException(e: Throwable) {
        e.printStackTrace()

        // ignore load failures if the game is newer than what's supported
        // so people in the future can use their save data
        if (GamePackageUtils.getGameVersionCode(packageManager) <= Constants.SUPPORTED_VERSION_CODE) {
            throw e
        }
    }

    private fun setupRedirection(packageInfo: PackageInfo) {
        try {
            GamePackageUtils.addAssetsFromPackage(assets, packageInfo)
        } catch (e: NoSuchMethodException) {
            Log.e("GeodeLauncher", "Failed to add asset redirection", e)
        }

        try {
            // fixes bugs specific to the new app directory, such as package name
            val saveDir = LaunchUtils.getSaveDirectory(this)
            saveDir.mkdir()

            // make sure it's loaded, just in case
            System.loadLibrary("c++_shared")

            LauncherFix.loadLibrary()
            LauncherFix.setOriginalDataPath(Constants.GJ_DATA_DIR)
            LauncherFix.setDataPath(saveDir.path)
        } catch (e: UnsatisfiedLinkError) {
            Log.e("GeodeLauncher", "Failed to load LauncherFix", e)
        }
    }

    private fun setupPostLibraryLoad(packageInfo: PackageInfo) {
        // call native functions after native libraries init
        JniToCpp.setupHSSAssets(
            packageInfo.applicationInfo!!.sourceDir,
            Environment.getExternalStorageDirectory().absolutePath
        )
        Cocos2dxHelper.nativeSetApkPath(packageInfo.applicationInfo!!.sourceDir)

        BaseRobTopActivity.setCurrentActivity(this)
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun tryLoadLibrary(packageInfo: PackageInfo, libraryName: String) {
        val nativeDir = getNativeLibraryDirectory(packageInfo.applicationInfo!!)
        val libraryPath = if (nativeDir.endsWith('/')) "${nativeDir}lib$libraryName.so" else "$nativeDir/lib$libraryName.so"

        try {
            System.load(libraryPath)
        } catch (ule: UnsatisfiedLinkError) {
            // some devices (samsung a series, cough) have an overly restrictive application classloader
            // this is a workaround for that. hopefully
            if (ule.message?.contains("not accessible for the namespace") != true) {
                throw ule
            }

            println("Using copy for library $libraryName")
            loadLibraryCopy(libraryName, libraryPath)
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadLibraryCopy(libraryName: String, libraryPath: String) {
        if (libraryPath.contains("!/")) {
            // library in apk can't be loaded directly
            loadLibraryFromAssetsCopy(libraryName)
            return
        }

        val library = File(libraryPath)
        val libraryCopy = File(cacheDir, "lib$libraryName.so")

        libraryCopy.outputStream().use { libraryOutput ->
            library.inputStream().use { inputStream ->
                DownloadUtils.copyFile(inputStream, libraryOutput)
            }
        }

        System.load(libraryCopy.path)

        return
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadLibraryFromAssetsCopy(libraryName: String) {
        // loads a library loaded in assets
        // this copies the library to a non-compressed directory

        val arch = LaunchUtils.applicationArchitecture
        val libraryFd = try {
            assets.openNonAssetFd("lib/$arch/lib$libraryName.so")
        } catch (_: Exception) {
            throw UnsatisfiedLinkError("Could not find library lib$libraryName.so for abi $arch")
        }

        // copy the library to a path we can access
        // there doesn't seem to be a way to load a library from a file descriptor
        val libraryCopy = File(cacheDir, "lib$libraryName.so")

        libraryCopy.outputStream().use { libraryOutput ->
            libraryFd.createInputStream().use { inputStream ->
                DownloadUtils.copyFile(inputStream, libraryOutput)
            }
        }

        System.load(libraryCopy.path)

        return
    }

    private fun getNativeLibraryDirectory(applicationInfo: ApplicationInfo): String {
        // native libraries have been extracted, so the path is same as usual
        if (applicationInfo.flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS == ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) {
            return applicationInfo.nativeLibraryDir
        }

        // might be split apks, select the best path for the library
        val sourceDirs = applicationInfo.splitSourceDirs
        val architecture = LaunchUtils.applicationArchitecture

        if (!sourceDirs.isNullOrEmpty()) {
            val configAbi = architecture.replace("-", "_")
            val abiDir = sourceDirs.find { it.contains(configAbi) }

            if (abiDir != null) {
                return "$abiDir!/lib/$architecture/"
            }
        }

        // this configuration should never happen!
        // non split apk, but libraries are stored in apk
        val sourceDir = applicationInfo.sourceDir
        return "$sourceDir!/lib/$architecture/"
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadGeodeLibrary() {
        // Load Geode if exists
        // bundling the object with the application allows for nicer backtraces
        try {
            // put libgeode.so in jniLibs/armeabi-v7a to get this
            System.loadLibrary("geode")
            return
        } catch (e: UnsatisfiedLinkError) {
            // but users may prefer it stored with data
            val geodeFilename = LaunchUtils.geodeFilename
            val geodePath = File(filesDir.path, "launcher/$geodeFilename")
            if (geodePath.exists()) {
                System.load(geodePath.path)
                return
            }

            // you know zmx i have 0 clue what this does so im
            // just gonna like copy the binary from external
            // also i get 20 million permission denied errors
            val externalGeodePath = LaunchUtils.getInstalledGeodePath(this)!!

            val copiedPath = File(filesDir.path, "copied")
            if (copiedPath.exists()) {
                copiedPath.deleteRecursively()
            }
            copiedPath.mkdir()

            val copiedGeodePath = File(copiedPath.path, "Geode.so")

            if (externalGeodePath.exists()) {
                DownloadUtils.copyFile(
                    FileInputStream(externalGeodePath),
                    FileOutputStream(copiedGeodePath)
                )

                if (copiedGeodePath.exists()) {
                    println("Loading Geode from ${externalGeodePath.name}")
                    System.load(copiedGeodePath.path)
                    return
                }
            }

            throw e
        }
    }

    private fun createView(): FrameLayout {
        val frameLayoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val frameLayout = ConstrainedFrameLayout(this)
        frameLayout.layoutParams = frameLayoutParams

        if (displayMode == DisplayMode.LIMITED) {
            frameLayout.aspectRatio = mAspectRatio
        }

        if (mScreenZoomFit && mScreenZoom != 1.0f) {
            frameLayout.fitZoom = true
        }

        if (mScreenZoom != 1.0f) {
            frameLayout.zoom = mScreenZoom
        }

        val editTextLayoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val editText = Cocos2dxEditText(this)
        editText.layoutParams = editTextLayoutParams
        frameLayout.addView(editText)

        val glSurfaceView = Cocos2dxGLSurfaceView(this)

        this.mGLSurfaceView = glSurfaceView
        frameLayout.addView(this.mGLSurfaceView)

        val showNotification = PreferenceUtils.get(this).getBoolean(PreferenceUtils.Key.ENABLE_REDESIGN)
        if (showNotification) {
            val hasCards = determineDisplayedCards(this)
            if (hasCards.isNotEmpty()) {
                val notificationView = ComposeView(this)
                frameLayout.addView(notificationView)

                notificationView.setContent {
                    LaunchNotification()
                }
            }
        }

        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setEGLConfigChooser(EGLConfigChooser())

        glSurfaceView.initView()

        val renderer = Cocos2dxRenderer(glSurfaceView)
        glSurfaceView.setCocos2dxRenderer(renderer)

        renderer.setFrameRate = mChosenDisplayRate

        val frameRate = mLimitedRefreshRate
        if (frameRate != null) {
            renderer.limitFrameRate(frameRate)
        }

        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        glSurfaceView.cocos2dxEditText = editText

        return frameLayout
    }

    private fun setupUIState() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        val preferenceUtils = PreferenceUtils.get(this)

        displayMode = DisplayMode.fromInt(
            preferenceUtils.getInt(PreferenceUtils.Key.DISPLAY_MODE)
        )

        mAspectRatio = ratioForPreference(preferenceUtils.getString(PreferenceUtils.Key.CUSTOM_ASPECT_RATIO) ?: "16_9")
        mScreenZoom = preferenceUtils.getInt(PreferenceUtils.Key.SCREEN_ZOOM) / 100.0f
        mScreenZoomFit = preferenceUtils.getBoolean(PreferenceUtils.Key.SCREEN_ZOOM_FIT)

        val limitedRefreshRate = preferenceUtils.getInt(PreferenceUtils.Key.LIMIT_FRAME_RATE).takeIf { it != 0 }
        mLimitedRefreshRate = limitedRefreshRate

        val forceRefreshRate = preferenceUtils.getBoolean(PreferenceUtils.Key.FORCE_HRR)
        if (forceRefreshRate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var chosenDisplay = display.supportedModes?.maxByOrNull { it.refreshRate }
            if (chosenDisplay != null) {
                var chosenRefreshRate = chosenDisplay.refreshRate
                println("Forcing a refresh rate of $chosenRefreshRate (display ${chosenDisplay.modeId})")

                window.attributes.preferredRefreshRate = chosenRefreshRate
                mChosenDisplayRate = chosenRefreshRate
            }
        }

        if (displayMode == DisplayMode.FULLSCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        hideSystemUi()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        mHasWindowFocus = hasWindowFocus
        if (hasWindowFocus && !mIsOnPause) {
            resumeGame()
        }

        hideSystemUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        FMOD.close()
    }

    private fun resumeGame() {
        mIsRunning = true
        Cocos2dxHelper.onResume()
        mGLSurfaceView?.onResume()
    }

    private fun pauseGame() {
        mIsRunning = false
        Cocos2dxHelper.onPause()
        mGLSurfaceView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mIsOnPause = false
        BaseRobTopActivity.isPaused = false
        if (mHasWindowFocus && !this.mIsRunning) {
            resumeGame()
        }
    }

    override fun onRestart() {
        super.onRestart()
        mIsOnPause = false
        BaseRobTopActivity.isPaused = false
        if (!this.mIsRunning) {
            resumeGame()
        }
    }

    override fun onPause() {
        super.onPause()
        mIsOnPause = true
        BaseRobTopActivity.isPaused = true
        if (mIsRunning) {
            pauseGame()
        }
    }

    override fun runOnGLThread(runnable: Runnable) {
        mGLSurfaceView?.queueEvent(runnable)
    }

    override fun showDialog(title: String, message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                // the button shouldn't do anything but close
                .setPositiveButton(R.string.message_box_accept) { _, _ -> }
                .show()
        }
    }

    override fun showEditTextDialog(
        title: String,
        message: String,
        inputMode: Int,
        inputFlag: Int,
        returnType: Int,
        maxLength: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun onCapabilityAdded(capability: String): Boolean {
        return when (capability) {
            GeodeUtils.CAPABILITY_EXTENDED_INPUT -> {
                mGLSurfaceView?.useKeyboardEvents = true
                true
            }
            GeodeUtils.CAPABILITY_TIMESTAMP_INPUT -> {
                mGLSurfaceView?.sendTimestampEvents = true
                true
            }
            else -> false
        }
    }

    /**
     * Copies a mod from the launcher's assets to the Geode mods directory.
     * This method is not recommended for casual use, the new mod will not be automatically removed.
     */
    private fun loadInternalMods() {
        val internalModBase = "mods"

        val modListing = try {
            assets.list(internalModBase)
        } catch (_: IOException) {
            emptyArray<String>()
        }

        val modDirectory = File(
            LaunchUtils.getBaseDirectory(this),
            "game/geode/mods"
        )

        modDirectory.mkdirs()

        modListing?.forEach { fileName ->
            if (fileName.endsWith(".geode")) {
                val modOutput = File(modDirectory, fileName)

                val mod = assets.open("$internalModBase/$fileName")
                DownloadUtils.copyFile(mod, modOutput.outputStream())

                println("Copied internal mod $fileName")
            }
        }
    }

    class EGLConfigChooser : GLSurfaceView.EGLConfigChooser {
        // this comes from EGL14, but is unavailable on EGL10
        // also EGL14 is incompatible with EGL10. so whatever
        private var EGL_OPENGL_ES2_BIT: Int = 0x04

        private data class ConfigValues(
            val redSize: Int,
            val greenSize: Int,
            val blueSize: Int,
            val alphaSize: Int,
            val depthSize: Int,
            val stencilSize: Int,
        )

        private fun getAttribValue(egl: EGL10, display: EGLDisplay, config: EGLConfig, attrib: Int): Int {
            val value = IntArray(1)
            egl.eglGetConfigAttrib(display, config, attrib, value)

            return value[0]
        }

        private fun testConfig(egl: EGL10, display: EGLDisplay, attrib: ConfigValues): EGLConfig? {
            val attribList = intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, attrib.redSize,
                EGL10.EGL_GREEN_SIZE, attrib.greenSize,
                EGL10.EGL_BLUE_SIZE, attrib.blueSize,
                EGL10.EGL_ALPHA_SIZE, attrib.alphaSize,
                EGL10.EGL_DEPTH_SIZE, attrib.depthSize,
                EGL10.EGL_STENCIL_SIZE, attrib.stencilSize,
                EGL10.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)

            val res = egl.eglChooseConfig(display, attribList, configs, configs.size, numConfigs)
            if (res && numConfigs[0] > 0 && configs[0] != null) {
                return configs[0]
            }

            return null
        }

        private fun printConfig(egl: EGL10, display: EGLDisplay, config: EGLConfig) {
            val id = getAttribValue(egl, display, config, EGL10.EGL_CONFIG_ID)
            val red = getAttribValue(egl, display, config, EGL10.EGL_RED_SIZE)
            val green = getAttribValue(egl, display, config, EGL10.EGL_GREEN_SIZE)
            val blue = getAttribValue(egl, display, config, EGL10.EGL_BLUE_SIZE)
            val alpha = getAttribValue(egl, display, config, EGL10.EGL_ALPHA_SIZE)
            val depth = getAttribValue(egl, display, config, EGL10.EGL_DEPTH_SIZE)
            val stencil = getAttribValue(egl, display, config, EGL10.EGL_STENCIL_SIZE)
            println("EGLConfig $id: (red = $red, green = $green, blue = $blue, alpha = $alpha, depth = $depth, stencil = $stencil)")
        }

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val configs = listOf(
                ConfigValues(8, 8, 8, 8, 16, 8),
                ConfigValues(8, 8, 8, 0, 16, 8),
                ConfigValues(5, 6, 5, 0, 16, 8),
                ConfigValues(0, 0, 0, 0, 0, 0),
            )

            return configs.firstNotNullOf {
                val config = testConfig(egl, display, it)
                if (config != null) {
                    printConfig(egl, display, config)
                }

                config
            }
        }
    }
}
