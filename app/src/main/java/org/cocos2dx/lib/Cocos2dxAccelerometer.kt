/****************************************************************************
Copyright (c) 2010-2011 cocos2d-x.org

http://www.cocos2d-x.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 ****************************************************************************/
package org.cocos2dx.lib

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class Cocos2dxAccelerometer : SensorEventListener {
    companion object {
        @JvmStatic
        external fun onSensorChanged(pX: Float, pY: Float, pZ: Float, pTimestamp: Long)
    }

    constructor(context: Context) {
        mContext = WeakReference(context)
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        this.mNaturalOrientation = display.rotation
    }

    private val mContext: WeakReference<Context>
    private val mSensorManager: SensorManager
    private val mAccelerometer: Sensor?
    private val mNaturalOrientation: Int

    fun enable() {
        this.mSensorManager.registerListener(this, this.mAccelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun setInterval(interval: Float) {
        this.mSensorManager.registerListener(this, this.mAccelerometer, (interval * 100000).roundToInt())
    }

    fun disable() {
        this.mSensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(pSensorEvent: SensorEvent) {
        if (pSensorEvent.sensor.type != Sensor.TYPE_ACCELEROMETER) {
            return
        }

        var (x, y, z) = pSensorEvent.values

        /*
         * Because the axes are not swapped when the device's screen orientation
         * changes. So we should swap it here. In tablets such as Motorola Xoom,
         * the default orientation is landscape, so should consider this.
         */
        val orientation = this.mContext.get()!!.resources.configuration.orientation

        if ((orientation == Configuration.ORIENTATION_LANDSCAPE) && (this.mNaturalOrientation != Surface.ROTATION_0)) {
            val tmp = x
            x = -y
            y = tmp
        } else if ((orientation == Configuration.ORIENTATION_PORTRAIT) && (this.mNaturalOrientation != Surface.ROTATION_0)) {
            val tmp = x
            x = y
            y = -tmp
        }

        Cocos2dxGLSurfaceView.queueAccelerometer(x, y, z, pSensorEvent.timestamp)
    }

    override fun onAccuracyChanged(pSensor: Sensor, pAccuracy: Int) {}
}