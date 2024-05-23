package org.cocos2dx.lib

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import com.customRobTop.BaseRobTopActivity
import com.geode.launcher.utils.GeodeUtils

private const val HANDLER_OPEN_IME_KEYBOARD = 2
private const val HANDLER_CLOSE_IME_KEYBOARD = 3
private const val MS_TO_NS = 1_000_000

class Cocos2dxGLSurfaceView(context: Context) : GLSurfaceView(context) {
    companion object {
        lateinit var cocos2dxGLSurfaceView: Cocos2dxGLSurfaceView
        private lateinit var handler: Handler
        private lateinit var cocos2dxTextInputWrapper: Cocos2dxTextInputWrapper

        @JvmStatic
        fun openIMEKeyboard() {
            val msg = Message()
            msg.what = HANDLER_OPEN_IME_KEYBOARD
            msg.obj = cocos2dxGLSurfaceView.getContentText()
            handler.sendMessage(msg)
        }

        @JvmStatic
        fun closeIMEKeyboard() {
            val msg = Message()
            msg.what = HANDLER_CLOSE_IME_KEYBOARD
            handler.sendMessage(msg)
        }
    }

    private lateinit var cocos2dxRenderer: Cocos2dxRenderer
    var useKeyboardEvents = false
        set(value) {
            field = value
            cocos2dxRenderer.sendResizeEvents = value
        }

    var sendTimestampEvents = false

    var cocos2dxEditText: Cocos2dxEditText? = null
        set(value) {
            field = value

            field?.setOnEditorActionListener(cocos2dxTextInputWrapper)
            field?.cocos2dxGLSurfaceView = this
            requestFocus()
        }

    fun initView() {
        setEGLContextClientVersion(2)
        isFocusableInTouchMode = true
//        if (Build.VERSION.SDK_INT >= 11) {
        preserveEGLContextOnPause = true
//        }
        cocos2dxGLSurfaceView = this
        cocos2dxTextInputWrapper = Cocos2dxTextInputWrapper(this)
        Cocos2dxGLSurfaceView.handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLER_OPEN_IME_KEYBOARD -> {
                        if (cocos2dxEditText?.requestFocus() == true) {
                            cocos2dxEditText?.apply {
                                removeTextChangedListener(
                                    cocos2dxTextInputWrapper
                                )
                                setText("")
                                val text = msg.obj as String
                                append(text)
                                cocos2dxTextInputWrapper.setOriginText(text)
                                addTextChangedListener(
                                    cocos2dxTextInputWrapper
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
                                cocos2dxTextInputWrapper
                            )
                            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                                windowToken,
                                0
                            )
                        }

                        requestFocus()
                        Log.d("GLSurfaceView", "HideSoftInput")
                        cocos2dxRenderer.handleTextClosed()
                        return
                    }
                    else -> return
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun updateRefreshRate() {
        val maxRefreshRate = display.supportedModes.maxBy { it.refreshRate }
        holder.surface.setFrameRate(
            maxRefreshRate.refreshRate,
            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
        )
    }

    override fun onPause() {
        queueEvent { cocos2dxRenderer.handleOnPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        queueEvent { cocos2dxRenderer.handleOnResume() }
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        val pointerNumber = motionEvent.pointerCount
        val ids = IntArray(pointerNumber)
        val xs = FloatArray(pointerNumber)
        val ys = FloatArray(pointerNumber)
        val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            motionEvent.eventTimeNanos else motionEvent.eventTime * MS_TO_NS

        for (i in 0 until pointerNumber) {
            ids[i] = motionEvent.getPointerId(i)
            xs[i] = motionEvent.getX(i)
            ys[i] = motionEvent.getY(i)
        }
        return when (motionEvent.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val idDown = motionEvent.getPointerId(0)
                val xDown = xs[0]
                val f = ys[0]
                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(timestamp)

                    cocos2dxRenderer.handleActionDown(idDown, xDown, f)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val idUp = motionEvent.getPointerId(0)
                val f2 = xs[0]
                val f3 = ys[0]
                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(timestamp)

                    cocos2dxRenderer.handleActionUp(idUp, f2, f3)
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(timestamp)

                    cocos2dxRenderer.handleActionMove(ids, xs, ys)
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(timestamp)

                    cocos2dxRenderer.handleActionCancel(ids, xs, ys)
                }
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val indexPointerDown = motionEvent.action shr 8
                val idPointerDown = motionEvent.getPointerId(indexPointerDown)
                val xPointerDown = motionEvent.getX(indexPointerDown)
                val y = motionEvent.getY(indexPointerDown)
                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(timestamp)

                    cocos2dxRenderer.handleActionDown(idPointerDown, xPointerDown, y)
                }
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val indexPointUp = motionEvent.action shr 8
                val idPointerUp = motionEvent.getPointerId(indexPointUp)
                val xPointerUp = motionEvent.getX(indexPointUp)
                val y2 = motionEvent.getY(indexPointUp)
                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(timestamp)

                    cocos2dxRenderer.handleActionUp(idPointerUp, xPointerUp, y2)
                }
                true
            }
            else -> true
        }
    }

    override fun onSizeChanged(
        newSurfaceWidth: Int,
        newSurfaceHeight: Int,
        oldSurfaceWidth: Int,
        oldSurfaceHeight: Int
    ) {
        if (!isInEditMode) {
            cocos2dxRenderer.setScreenWidthAndHeight(newSurfaceWidth, newSurfaceHeight)
        }
    }

    private fun legacyKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> {
                if (keyEvent.repeatCount != 0 || BaseRobTopActivity.blockBackButton) {
                    return true
                }
                queueEvent { cocos2dxRenderer.handleKeyDown(keyCode) }
                true
            }
            else -> super.onKeyDown(keyCode, keyEvent)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!useKeyboardEvents) {
            return legacyKeyDown(keyCode, event)
        }

        return when (keyCode) {
            // ignore system keys
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MUTE -> {
                super.onKeyDown(keyCode, event)
            }
            else -> {
                if (BaseRobTopActivity.blockBackButton) {
                    return true
                }

                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(event.eventTime * MS_TO_NS)

                    GeodeUtils.nativeKeyDown(keyCode, event.modifiers, event.repeatCount != 0)
                }
                true
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!useKeyboardEvents) {
            return super.onKeyUp(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MUTE -> {
                super.onKeyUp(keyCode, event)
            }
            else -> {
                if (event.repeatCount != 0 || BaseRobTopActivity.blockBackButton) {
                    return true
                }

                queueEvent {
                    if (sendTimestampEvents)
                        GeodeUtils.setNextInputTimestamp(event.eventTime * MS_TO_NS)

                    GeodeUtils.nativeKeyUp(keyCode, event.modifiers)
                }

                true
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (!useKeyboardEvents) {
            return super.onGenericMotionEvent(event)
        }

        if (event?.action == MotionEvent.ACTION_SCROLL) {
            val scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                event.eventTimeNanos else event.eventTime * MS_TO_NS

            queueEvent {
                if (sendTimestampEvents)
                    GeodeUtils.setNextInputTimestamp(timestamp)

                GeodeUtils.nativeActionScroll(scrollX, scrollY)
            }

            return true
        }

        return super.onGenericMotionEvent(event)
    }

    fun setCocos2dxRenderer(renderer: Cocos2dxRenderer) {
        this.cocos2dxRenderer = renderer
        setRenderer(this.cocos2dxRenderer)
    }

    private fun getContentText(): String {
        return cocos2dxRenderer.getContentText()
    }

    fun insertText(text: String) {
        queueEvent { cocos2dxRenderer.handleInsertText(text) }
    }

    fun deleteBackward() {
        queueEvent { cocos2dxRenderer.handleDeleteBackward() }
    }
}