package org.cocos2dx.lib

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

class Cocos2dxEditText : AppCompatEditText {
    private var mCocos2dxGLSurfaceView: Cocos2dxGLSurfaceView? = null

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(
        context, attrs
    ) {
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context, attrs, defStyle
    ) {
    }

    fun setCocos2dxGLSurfaceView(pCocos2dxGLSurfaceView: Cocos2dxGLSurfaceView?) {
        mCocos2dxGLSurfaceView = pCocos2dxGLSurfaceView
    }

    override fun onKeyDown(pKeyCode: Int, pKeyEvent: KeyEvent): Boolean {
        super.onKeyDown(pKeyCode, pKeyEvent)
        if (pKeyCode != 4) {
            return true
        }
        mCocos2dxGLSurfaceView!!.requestFocus()
        return true
    }
}