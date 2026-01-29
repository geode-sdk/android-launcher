package org.cocos2dx.lib

import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import com.customRobTop.BaseRobTopActivity
import com.geode.launcher.utils.GeodeUtils
import kotlin.math.abs

private const val HANDLER_OPEN_IME_KEYBOARD = 2
private const val HANDLER_CLOSE_IME_KEYBOARD = 3
private const val MS_TO_NS = 1_000_000
private const val SEC_TO_NS = 1_000_000_000.0

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

        fun queueAccelerometer(x: Float, y: Float, z: Float, timestamp: Long) =
            cocos2dxGLSurfaceView.queueEvent {
                Cocos2dxAccelerometer.onSensorChanged(x, y, z, timestamp)
            }
    }

    private lateinit var cocos2dxRenderer: Cocos2dxRenderer
    var useKeyboardEvents = false
        set(value) {
            field = value
            cocos2dxRenderer.sendResizeEvents = value
        }

    var sendTimestampEvents = false
    var manualBackEvents = false
    var controllerEventsEnabled = false
        private set

    var cocos2dxEditText: AppCompatEditText? = null
        set(value) {
            field = value

            field?.setOnEditorActionListener(cocos2dxTextInputWrapper)
            requestFocus()
        }

    fun initView() {
        setEGLContextClientVersion(2)
        isFocusableInTouchMode = true
        preserveEGLContextOnPause = true
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
        val chosenDisplay = display.supportedModes?.maxByOrNull { it.refreshRate }
        if (chosenDisplay == null) {
            println("updateRefreshRate failed to find a display to maximize refresh rate...")
            return
        }

        val chosenRefreshRate = chosenDisplay.refreshRate

        println("updateRefreshRate: selecting refresh rate of ${chosenDisplay.refreshRate} (display ${chosenDisplay.modeId})")

        holder.surface.setFrameRate(chosenRefreshRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        if (isAttachedToWindow) {
            (context as Activity).window.attributes.preferredRefreshRate = chosenRefreshRate
        }
    }

    override fun onPause() {
        queueEvent { cocos2dxRenderer.handleOnPause() }
        renderMode = RENDERMODE_WHEN_DIRTY
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        renderMode = RENDERMODE_CONTINUOUSLY
        queueEvent { cocos2dxRenderer.handleOnResume() }
    }

    private fun sendNextEventTimestamp(timestamp: Long) {
        if (sendTimestampEvents)
            GeodeUtils.setNextInputTimestamp(timestamp)
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        val pointerNumber = motionEvent.pointerCount
        val ids = IntArray(pointerNumber)
        val xs = FloatArray(pointerNumber)
        val ys = FloatArray(pointerNumber)
        val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            motionEvent.eventTimeNanos else motionEvent.eventTime * MS_TO_NS
        val timestampSec = timestamp / SEC_TO_NS

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
                    sendNextEventTimestamp(timestamp)

                    if (controllerEventsEnabled)
                        GeodeUtils.internalTouchEvent(
                            timestamp, motionEvent.deviceId, motionEvent.source,
                            // slice here to convert to single element array
                            GeodeUtils.TOUCH_TYPE_BEGIN, ids.sliceArray(0..0), xs.sliceArray(0..0), ys.sliceArray(0..0)
                        )
                    else
                        cocos2dxRenderer.handleActionDown(idDown, xDown, f, timestampSec)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val idUp = motionEvent.getPointerId(0)
                val f2 = xs[0]
                val f3 = ys[0]
                queueEvent {
                    sendNextEventTimestamp(timestamp)

                    if (controllerEventsEnabled)
                        GeodeUtils.internalTouchEvent(
                            timestamp, motionEvent.deviceId, motionEvent.source,
                            GeodeUtils.TOUCH_TYPE_END, ids.sliceArray(0..0), xs.sliceArray(0..0), ys.sliceArray(0..0)
                        )
                    else
                        cocos2dxRenderer.handleActionUp(idUp, f2, f3, timestampSec)
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                queueEvent {
                    sendNextEventTimestamp(timestamp)

                    if (controllerEventsEnabled)
                        GeodeUtils.internalTouchEvent(
                            timestamp, motionEvent.deviceId, motionEvent.source,
                            GeodeUtils.TOUCH_TYPE_MOVE, ids, xs, ys
                        )
                    else
                        cocos2dxRenderer.handleActionMove(ids, xs, ys, timestampSec)
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                queueEvent {
                    sendNextEventTimestamp(timestamp)

                    if (controllerEventsEnabled)
                        GeodeUtils.internalTouchEvent(
                            timestamp, motionEvent.deviceId, motionEvent.source,
                            GeodeUtils.TOUCH_TYPE_CANCEL, ids, xs, ys
                        )
                    else
                        cocos2dxRenderer.handleActionCancel(ids, xs, ys, timestampSec)
                }
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val indexPointerDown = motionEvent.action shr 8
                val idPointerDown = motionEvent.getPointerId(indexPointerDown)
                val xPointerDown = motionEvent.getX(indexPointerDown)
                val y = motionEvent.getY(indexPointerDown)
                queueEvent {
                    sendNextEventTimestamp(timestamp)

                    if (controllerEventsEnabled)
                        GeodeUtils.internalTouchEvent(
                            timestamp, motionEvent.deviceId, motionEvent.source,
                            GeodeUtils.TOUCH_TYPE_BEGIN, intArrayOf(idPointerDown), floatArrayOf(xPointerDown), floatArrayOf(y)
                        )
                    else
                        cocos2dxRenderer.handleActionDown(idPointerDown, xPointerDown, y, timestampSec)
                }
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val indexPointUp = motionEvent.action shr 8
                val idPointerUp = motionEvent.getPointerId(indexPointUp)
                val xPointerUp = motionEvent.getX(indexPointUp)
                val y2 = motionEvent.getY(indexPointUp)
                queueEvent {
                    sendNextEventTimestamp(timestamp)

                    if (controllerEventsEnabled)
                        GeodeUtils.internalTouchEvent(
                            timestamp, motionEvent.deviceId, motionEvent.source,
                            GeodeUtils.TOUCH_TYPE_END, intArrayOf(idPointerUp), floatArrayOf(xPointerUp), floatArrayOf(y2)
                        )
                    else
                        cocos2dxRenderer.handleActionUp(idPointerUp, xPointerUp, y2, timestampSec)
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

    fun sendKeyBack() {
        if (cocos2dxEditText?.isFocused == true) {
            requestFocus()
        }

        if (BaseRobTopActivity.blockBackButton) {
            return
        }

        val currentTime = System.nanoTime()
        queueEvent {
            sendNextEventTimestamp(currentTime)

            if (controllerEventsEnabled) {
                GeodeUtils.internalKeyEvent(
                    currentTime,
                    -1,
                    InputDevice.SOURCE_ANY,
                    KeyEvent.KEYCODE_BACK,
                    0,
                    true,
                    0
                )
                GeodeUtils.internalKeyEvent(
                    currentTime,
                    -1,
                    InputDevice.SOURCE_ANY,
                    KeyEvent.KEYCODE_BACK,
                    0,
                    false,
                    0
                )
            }

            if (useKeyboardEvents) {
                GeodeUtils.nativeKeyDown(KeyEvent.KEYCODE_BACK, 0, false)
                GeodeUtils.nativeKeyUp(KeyEvent.KEYCODE_BACK, 0)
            } else if (!controllerEventsEnabled) {
                cocos2dxRenderer.handleKeyDown(KeyEvent.KEYCODE_BACK)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!useKeyboardEvents && !controllerEventsEnabled) {
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
                    sendNextEventTimestamp(event.eventTime * MS_TO_NS)

                    if (useKeyboardEvents) {
                        GeodeUtils.nativeKeyDown(keyCode, event.modifiers, event.repeatCount != 0)
                    }

                    if (controllerEventsEnabled) {
                        GeodeUtils.internalKeyEvent(event.eventTime * MS_TO_NS, event.deviceId, event.source, event.keyCode, event.modifiers, true, event.repeatCount)
                    }
                }
                true
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!useKeyboardEvents && !controllerEventsEnabled) {
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
                    sendNextEventTimestamp(event.eventTime * MS_TO_NS)

                    if (useKeyboardEvents) {
                        GeodeUtils.nativeKeyUp(keyCode, event.modifiers)
                    }

                    if (controllerEventsEnabled) {
                        GeodeUtils.internalKeyEvent(event.eventTime * MS_TO_NS, event.deviceId, event.source, event.keyCode, event.modifiers, false, event.repeatCount)
                    }
                }

                true
            }
        }
    }

    // i'm lazy.
    // https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input
    private fun getCenteredAxis(
        event: MotionEvent,
        device: InputDevice,
        axis: Int,
        historyPos: Int
    ): Float {
        device.getMotionRange(axis, event.source)?.apply {
            val value: Float = if (historyPos < 0) {
                event.getAxisValue(axis)
            } else {
                event.getHistoricalAxisValue(axis, historyPos)
            }

            if (abs(value) > flat) {
                return value
            }
        }
        return 0.0f
    }

    private fun getJoystickMotion(event: MotionEvent, axisHorizontal: Int, axisVertical: Int): Pair<FloatArray, FloatArray> {
        val positionChangesX = ArrayList<Float>()
        val positionChangesY = ArrayList<Float>()

        val inputDevice = event.device

        (0 until event.historySize).forEach { i ->
            val x = getCenteredAxis(event, inputDevice, axisHorizontal, i)
            val y = getCenteredAxis(event, inputDevice, axisVertical, i)
            positionChangesX.add(x)
            positionChangesY.add(y)
        }

        val x = getCenteredAxis(event, inputDevice, axisHorizontal, -1)
        val y = getCenteredAxis(event, inputDevice, axisVertical, -1)
        positionChangesX.add(x)
        positionChangesY.add(y)

        return Pair(positionChangesX.toFloatArray(), positionChangesY.toFloatArray())
    }

    private fun getJoystickMotion(event: MotionEvent, axis: Int): FloatArray {
        val positionChanges = ArrayList<Float>()

        val inputDevice = event.device

        (0 until event.historySize).forEach { i ->
            val x = getCenteredAxis(event, inputDevice, axis, i)
            positionChanges.add(x)
        }

        val x = getCenteredAxis(event, inputDevice, axis, -1)
        positionChanges.add(x)

        return positionChanges.toFloatArray()
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (!useKeyboardEvents && !controllerEventsEnabled) {
            return super.onGenericMotionEvent(event)
        }

        if (event?.action == MotionEvent.ACTION_SCROLL) {
            val scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                event.eventTimeNanos else event.eventTime * MS_TO_NS

            queueEvent {
                sendNextEventTimestamp(timestamp)

                if (useKeyboardEvents) {
                    GeodeUtils.nativeActionScroll(scrollX, scrollY)
                }

                if (controllerEventsEnabled) {
                    GeodeUtils.internalScrollEvent(timestamp, event.deviceId, event.source, scrollX, scrollY)
                }
            }

            return true
        }

        if (controllerEventsEnabled && event?.action == MotionEvent.ACTION_MOVE && event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            val (motionLeftX, motionLeftY) = getJoystickMotion(event, MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
            val (motionRightX, motionRightY) = getJoystickMotion(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
            val (motionDPadX, motionDPadY) = getJoystickMotion(event, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y)

            val motionLTrigger = getJoystickMotion(event, MotionEvent.AXIS_LTRIGGER)
            val motionRTrigger = getJoystickMotion(event, MotionEvent.AXIS_RTRIGGER)

            // i'm sure this is great for performance
            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                event.eventTimeNanos else event.eventTime * MS_TO_NS

            queueEvent {
                GeodeUtils.internalJoystickEvent(
                    timestamp, event.deviceId, event.source,
                    motionLeftX, motionLeftY,
                    motionRightX, motionRightY,
                    motionDPadX, motionDPadY,
                    motionLTrigger, motionRTrigger
                )
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

    fun enableControllerEvents() {
        if (controllerEventsEnabled) {
            return
        }

        controllerEventsEnabled = true

        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(InputListener, null)
    }

    fun disableControllerEvents() {
        if (!controllerEventsEnabled) {
            return
        }

        controllerEventsEnabled = true

        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.unregisterInputDeviceListener(InputListener)
    }

    object InputListener : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            GeodeUtils.inputDeviceAdded(deviceId)
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            GeodeUtils.inputDeviceChanged(deviceId)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            GeodeUtils.inputDeviceRemoved(deviceId)
        }
    }
}