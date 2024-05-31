package org.cocos2dx.lib

import android.opengl.GLSurfaceView
import android.os.Build
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Suppress("unused", "KotlinJniMissingFunction")
class Cocos2dxRenderer(private var handler: Cocos2dxGLSurfaceView) : GLSurfaceView.Renderer {
    companion object {
        @JvmStatic
        fun setAnimationInterval(@Suppress("UNUSED_PARAMETER") animationInterval: Double) {
            // this function is useless for gd
        }

        @JvmStatic
        private external fun nativeDeleteBackward()

        @JvmStatic
        private external fun nativeGetContentText(): String

        @JvmStatic
        private external fun nativeInsertText(text: String)

        @JvmStatic
        private external fun nativeKeyDown(keyCode: Int): Boolean

        @JvmStatic
        private external fun nativeOnPause()

        @JvmStatic
        private external fun nativeOnResume()

        @JvmStatic
        private external fun nativeRender()

        @JvmStatic
        private external fun nativeTextClosed()

        @JvmStatic
        private external fun nativeTouchesBegin(id: Int, x: Float, y: Float)

        @JvmStatic
        private external fun nativeTouchesCancel(
            ids: IntArray,
            xs: FloatArray,
            ys: FloatArray
        )

        @JvmStatic
        private external fun nativeTouchesEnd(id: Int, x: Float, y: Float)

        @JvmStatic
        private external fun nativeTouchesMove(ids: IntArray, xs: FloatArray, ys: FloatArray)

        @JvmStatic
        private external fun nativeInit(width: Int, height: Int)
    }

    private var lastTickInNanoSeconds: Long = 0
    private var screenWidth = 0
    private var screenHeight = 0
    var sendResizeEvents = false
    var setFrameRate = false

    fun setScreenWidthAndHeight(surfaceWidth: Int, surfaceHeight: Int) {
        screenWidth = surfaceWidth
        screenHeight = surfaceHeight
    }

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        nativeInit(screenWidth, screenHeight)
        lastTickInNanoSeconds = System.nanoTime()
    }

    override fun onSurfaceChanged(gl10: GL10?, width: Int, height: Int) {
        if (setFrameRate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            handler.updateRefreshRate()
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        nativeRender()
    }

    fun handleActionDown(id: Int, x: Float, y: Float) {
        nativeTouchesBegin(id, x, y)
    }

    fun handleActionUp(id: Int, x: Float, y: Float) {
        nativeTouchesEnd(id, x, y)
    }

    fun handleActionCancel(ids: IntArray, xs: FloatArray, ys: FloatArray) {
        nativeTouchesCancel(ids, xs, ys)
    }

    fun handleActionMove(ids: IntArray, xs: FloatArray, ys: FloatArray) {
        nativeTouchesMove(ids, xs, ys)
    }

    fun handleKeyDown(keyCode: Int) {
        nativeKeyDown(keyCode)
    }

    fun handleOnPause() {
        nativeOnPause()
    }

    fun handleOnResume() {
        nativeOnResume()
    }

    fun handleInsertText(text: String) {
        nativeInsertText(text)
    }

    fun handleDeleteBackward() {
        nativeDeleteBackward()
    }

    fun handleTextClosed() {
        nativeTextClosed()
    }

    fun getContentText(): String {
        return nativeGetContentText()
    }
}