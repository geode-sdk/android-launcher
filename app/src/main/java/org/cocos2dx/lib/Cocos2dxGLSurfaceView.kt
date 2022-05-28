package org.cocos2dx.lib

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.customRobTop.BaseRobTopActivity

private const val HANDLER_OPEN_IME_KEYBOARD = 2
private const val HANDLER_CLOSE_IME_KEYBOARD = 3

class Cocos2dxGLSurfaceView(context: Context) : GLSurfaceView(context) {
    companion object {
        lateinit var mCocos2dxGLSurfaceView: Cocos2dxGLSurfaceView
        private lateinit var sHandler: Handler
        private lateinit var sCocos2dxTextInputWrapper: Cocos2dxTextInputWrapper

        @JvmStatic
        fun openIMEKeyboard() {
            val msg = Message()
            msg.what = HANDLER_OPEN_IME_KEYBOARD
            msg.obj = mCocos2dxGLSurfaceView.getContentText()
            sHandler.sendMessage(msg)
        }

        @JvmStatic
        fun closeIMEKeyboard() {
            val msg = Message()
            msg.what = HANDLER_CLOSE_IME_KEYBOARD
            sHandler.sendMessage(msg)
        }
    }

    private lateinit var mCocos2dxRenderer: Cocos2dxRenderer

    var cocos2dxEditText: Cocos2dxEditText? = null
        set(value) {
            field = value

            field?.setOnEditorActionListener(sCocos2dxTextInputWrapper)
            field?.cocos2dxGLSurfaceView = this
            requestFocus()
        }

    fun initView() {
        setEGLContextClientVersion(2)
        isFocusableInTouchMode = true
//        if (Build.VERSION.SDK_INT >= 11) {
        preserveEGLContextOnPause = true
//        }
        mCocos2dxGLSurfaceView = this
        sCocos2dxTextInputWrapper = Cocos2dxTextInputWrapper(this)
        sHandler = @SuppressLint("HandlerLeak")
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLER_OPEN_IME_KEYBOARD -> {
                        if (cocos2dxEditText?.requestFocus() == true) {
                            cocos2dxEditText?.apply {
                                removeTextChangedListener(
                                    sCocos2dxTextInputWrapper
                                )
                                setText("")
                                val text = msg.obj as String
                                append(text)
                                sCocos2dxTextInputWrapper.setOriginText(text)
                                addTextChangedListener(
                                    sCocos2dxTextInputWrapper
                                )
                            }
                            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                                cocos2dxEditText,
                                0
                            )
                            Log.d("GLSurfaceView", "showSoftInput")
                            return
                        }
                        return
                    }
                    HANDLER_CLOSE_IME_KEYBOARD -> {
                        cocos2dxEditText?.apply {
                            removeTextChangedListener(
                                sCocos2dxTextInputWrapper
                            )
                            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                                windowToken,
                                0
                            )
                        }

                        requestFocus()
                        Log.d("GLSurfaceView", "HideSoftInput")
                        mCocos2dxRenderer.handleTextClosed()
                        return
                    }
                    else -> return
                }
            }
        }
    }

    override fun onPause() {
        queueEvent { mCocos2dxRenderer.handleOnPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        queueEvent { mCocos2dxRenderer.handleOnResume() }
    }

    override fun onTouchEvent(pMotionEvent: MotionEvent): Boolean {
        val pointerNumber = pMotionEvent.pointerCount
        val ids = IntArray(pointerNumber)
        val xs = FloatArray(pointerNumber)
        val ys = FloatArray(pointerNumber)
        for (i in 0 until pointerNumber) {
            ids[i] = pMotionEvent.getPointerId(i)
            xs[i] = pMotionEvent.getX(i)
            ys[i] = pMotionEvent.getY(i)
        }
        return when (pMotionEvent.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val idDown = pMotionEvent.getPointerId(0)
                val xDown = xs[0]
                val f = ys[0]
                queueEvent {
                    mCocos2dxRenderer.handleActionDown(
                        idDown,
                        xDown,
                        f
                    )
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val idUp = pMotionEvent.getPointerId(0)
                val f2 = xs[0]
                val f3 = ys[0]
                queueEvent { mCocos2dxRenderer.handleActionUp(idUp, f2, f3) }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                queueEvent {
                    mCocos2dxRenderer.handleActionMove(
                        ids,
                        xs,
                        ys
                    )
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                queueEvent {
                    mCocos2dxRenderer.handleActionCancel(
                        ids,
                        xs,
                        ys
                    )
                }
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val indexPointerDown = pMotionEvent.action shr 8
                val idPointerDown = pMotionEvent.getPointerId(indexPointerDown)
                val xPointerDown = pMotionEvent.getX(indexPointerDown)
                val y = pMotionEvent.getY(indexPointerDown)
                queueEvent {
                    mCocos2dxRenderer.handleActionDown(
                        idPointerDown,
                        xPointerDown,
                        y
                    )
                }
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val indexPointUp = pMotionEvent.action shr 8
                val idPointerUp = pMotionEvent.getPointerId(indexPointUp)
                val xPointerUp = pMotionEvent.getX(indexPointUp)
                val y2 = pMotionEvent.getY(indexPointUp)
                queueEvent {
                    mCocos2dxRenderer.handleActionUp(
                        idPointerUp,
                        xPointerUp,
                        y2
                    )
                }
                true
            }
            else -> true
        }
    }

    override fun onSizeChanged(
        pNewSurfaceWidth: Int,
        pNewSurfaceHeight: Int,
        pOldSurfaceWidth: Int,
        pOldSurfaceHeight: Int
    ) {
        if (!isInEditMode) {
            mCocos2dxRenderer.setScreenWidthAndHeight(pNewSurfaceWidth, pNewSurfaceHeight)
        }
    }

    override fun onKeyDown(pKeyCode: Int, pKeyEvent: KeyEvent): Boolean {
        return when (pKeyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                if (pKeyEvent.repeatCount != 0 || BaseRobTopActivity.blockBackButton) {
                    return true
                }
                queueEvent { mCocos2dxRenderer.handleKeyDown(pKeyCode) }
                true
            }
            else -> super.onKeyDown(pKeyCode, pKeyEvent)
        }
    }

    fun setCocos2dxRenderer(renderer: Cocos2dxRenderer) {
        this.mCocos2dxRenderer = renderer
        setRenderer(this.mCocos2dxRenderer)
    }

    private fun getContentText(): String {
        return mCocos2dxRenderer.getContentText()
    }

    fun insertText(pText: String) {
        queueEvent { mCocos2dxRenderer.handleInsertText(pText) }
    }

    fun deleteBackward() {
        queueEvent { mCocos2dxRenderer.handleDeleteBackward() }
    }
}