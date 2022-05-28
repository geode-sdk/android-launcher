package org.cocos2dx.lib

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Suppress("unused", "KotlinJniMissingFunction")
class Cocos2dxRenderer : GLSurfaceView.Renderer {
    companion object {
        @JvmStatic
        fun setAnimationInterval(@Suppress("UNUSED_PARAMETER") pAnimationInterval: Double) {
            // this function is useless for gd
        }

        @JvmStatic
        private external fun nativeDeleteBackward()

        @JvmStatic
        private external fun nativeGetContentText(): String

        @JvmStatic
        private external fun nativeInsertText(pText: String)

        @JvmStatic
        private external fun nativeKeyDown(pKeyCode: Int): Boolean

        @JvmStatic
        private external fun nativeOnPause()

        @JvmStatic
        private external fun nativeOnResume()

        @JvmStatic
        private external fun nativeRender()

        @JvmStatic
        private external fun nativeTextClosed()

        @JvmStatic
        private external fun nativeTouchesBegin(pID: Int, pX: Float, pY: Float)

        @JvmStatic
        private external fun nativeTouchesCancel(
            pIDs: IntArray,
            pXs: FloatArray,
            pYs: FloatArray
        )

        @JvmStatic
        private external fun nativeTouchesEnd(pID: Int, pX: Float, pY: Float)

        @JvmStatic
        private external fun nativeTouchesMove(pIDs: IntArray, pXs: FloatArray, pYs: FloatArray)

        @JvmStatic
        private external fun nativeInit(pWidth: Int, pHeight: Int)
    }

    private var mLastTickInNanoSeconds: Long = 0
    private var mScreenWidth = 0
    private var mScreenHeight = 0

    fun setScreenWidthAndHeight(pSurfaceWidth: Int, pSurfaceHeight: Int) {
        mScreenWidth = pSurfaceWidth
        mScreenHeight = pSurfaceHeight
    }

    override fun onSurfaceCreated(pGL10: GL10?, pEGLConfig: EGLConfig?) {
        nativeInit(this.mScreenWidth, this.mScreenHeight)
        this.mLastTickInNanoSeconds = System.nanoTime()
    }

    override fun onSurfaceChanged(pGL10: GL10?, pWidth: Int, pHeight: Int) {}

    override fun onDrawFrame(gl: GL10?) {
        nativeRender()
    }

    fun handleActionDown(pID: Int, pX: Float, pY: Float) {
        nativeTouchesBegin(pID, pX, pY)
    }

    fun handleActionUp(pID: Int, pX: Float, pY: Float) {
        nativeTouchesEnd(pID, pX, pY)
    }

    fun handleActionCancel(pIDs: IntArray, pXs: FloatArray, pYs: FloatArray) {
        nativeTouchesCancel(pIDs, pXs, pYs)
    }

    fun handleActionMove(pIDs: IntArray, pXs: FloatArray, pYs: FloatArray) {
        nativeTouchesMove(pIDs, pXs, pYs)
    }

    fun handleKeyDown(pKeyCode: Int) {
        nativeKeyDown(pKeyCode)
    }

    fun handleOnPause() {
        nativeOnPause()
    }

    fun handleOnResume() {
        nativeOnResume()
    }

    fun handleInsertText(pText: String) {
        nativeInsertText(pText)
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