package org.cocos2dx.lib

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.customRobTop.BaseRobTopActivity


open class Cocos2dxActivity : BaseRobTopActivity(), Cocos2dxHelper.Cocos2dxHelperListener {
    private val sTag = Cocos2dxActivity::class.java.simpleName
    private lateinit var mGLSurfaceView: Cocos2dxGLSurfaceView
    private var hasWindowFocus = false
    private var mIsOnPause = false
    private var mIsRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
        Cocos2dxHelper.init(this, this)
    }

    private fun resumeGame() {
        this.mIsRunning = true
        mGLSurfaceView.onResume()
    }

    private fun pauseGame() {
        this.mIsRunning = false
        mGLSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        this.mIsOnPause = false
        if (this.hasWindowFocus && !this.mIsRunning) {
            resumeGame()
        }
    }

    override fun onPause() {
        super.onPause()
        this.mIsOnPause = true
        if (this.mIsRunning) {
            pauseGame()
        }
    }

    override fun showDialog(pTitle: String, pMessage: String) {
/*
        val msg = Message()
        msg.what = 1
        msg.obj = DialogMessage(pTitle, pMessage)
        this.mHandler.sendMessage(msg)
 */
    }

    override fun showEditTextDialog(
        pTitle: String,
        pMessage: String,
        pInputMode: Int,
        pInputFlag: Int,
        pReturnType: Int,
        pMaxLength: Int
    ) {
/*
        val msg = Message()
        msg.what = 2
        msg.obj = EditBoxMessage(pTitle, pContent, pInputMode, pInputFlag, pReturnType, pMaxLength)
        this.mHandler.sendMessage(msg)
 */
    }

    override fun runOnGLThread(pRunnable: Runnable) {
        mGLSurfaceView.queueEvent(pRunnable)
    }

    private fun init() {
        val frameLayoutParams = ViewGroup.LayoutParams(-1, -1)
        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = frameLayoutParams
//        val edittextLayoutParams = ViewGroup.LayoutParams(-1, -2)
//        val edittext = Cocos2dxEditText(this)
//        edittext.setLayoutParams(edittextLayoutParams)
//        frameLayout.addView(edittext)
        this.mGLSurfaceView = onCreateView()
        frameLayout.addView(this.mGLSurfaceView)
        if (isAndroidEmulator()) {
            this.mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        }
        this.mGLSurfaceView.setCocos2dxRenderer(Cocos2dxRenderer())
//        edittext.setInputType(145)
//        this.mGLSurfaceView.setCocos2dxEditText(edittext)
        setContentView(frameLayout)
    }


    open fun onCreateView(): Cocos2dxGLSurfaceView {
        val glSurfaceView = Cocos2dxGLSurfaceView(this)
        glSurfaceView.initView()
        return glSurfaceView
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