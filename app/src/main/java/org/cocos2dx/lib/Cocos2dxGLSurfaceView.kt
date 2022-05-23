package org.cocos2dx.lib

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.MotionEvent
import com.customRobTop.BaseRobTopActivity


class Cocos2dxGLSurfaceView(context: Context) : GLSurfaceView(context) {
    companion object {
        lateinit var mCocos2dxGLSurfaceView: Cocos2dxGLSurfaceView
        private var sHandler: Handler? = null
    }

    private lateinit var mCocos2dxRenderer: Cocos2dxRenderer

    fun initView() {
        setEGLContextClientVersion(2)
        isFocusableInTouchMode = true
//        if (Build.VERSION.SDK_INT >= 11) {
        preserveEGLContextOnPause = true
//        }
        mCocos2dxGLSurfaceView = this
//        sCocos2dxTextInputWraper = Cocos2dxTextInputWraper(this)
        sHandler = @SuppressLint("HandlerLeak")
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
/*                when (msg.what) {
                    2 -> {
                        if (this@Cocos2dxGLSurfaceView.mCocos2dxEditText != null && this@Cocos2dxGLSurfaceView.mCocos2dxEditText.requestFocus()) {
                            this@Cocos2dxGLSurfaceView.mCocos2dxEditText.removeTextChangedListener(
                                Cocos2dxGLSurfaceView.sCocos2dxTextInputWraper
                            )
                            this@Cocos2dxGLSurfaceView.mCocos2dxEditText.setText("")
                            val text = msg.obj as String
                            this@Cocos2dxGLSurfaceView.mCocos2dxEditText.append(text)
                            Cocos2dxGLSurfaceView.sCocos2dxTextInputWraper.setOriginText(text)
                            this@Cocos2dxGLSurfaceView.mCocos2dxEditText.addTextChangedListener(
                                Cocos2dxGLSurfaceView.sCocos2dxTextInputWraper
                            )
                            (Cocos2dxGLSurfaceView.mCocos2dxGLSurfaceView.getContext()
                                .getSystemService("input_method") as InputMethodManager).showSoftInput(
                                this@Cocos2dxGLSurfaceView.mCocos2dxEditText,
                                0
                            )
                            Log.d("GLSurfaceView", "showSoftInput")
                            return
                        }
                        return
                    }
                    3 -> {
                        if (this@Cocos2dxGLSurfaceView.mCocos2dxEditText != null) {
                            this@Cocos2dxGLSurfaceView.mCocos2dxEditText.removeTextChangedListener(
                                Cocos2dxGLSurfaceView.sCocos2dxTextInputWraper
                            )
                            (Cocos2dxGLSurfaceView.mCocos2dxGLSurfaceView.getContext()
                                .getSystemService("input_method") as InputMethodManager).hideSoftInputFromWindow(
                                this@Cocos2dxGLSurfaceView.mCocos2dxEditText.getWindowToken(),
                                0
                            )
                            this@Cocos2dxGLSurfaceView.requestFocus()
                            Log.d("GLSurfaceView", "HideSoftInput")
                            this@Cocos2dxGLSurfaceView.mCocos2dxRenderer.handleTextClosed()
                            return
                        }
                        return
                    }
                    else -> return
                }
 */
            }
        }
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
                if (pKeyEvent.repeatCount != 0 || BaseRobTopActivity.blockBackButton_) {
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
}