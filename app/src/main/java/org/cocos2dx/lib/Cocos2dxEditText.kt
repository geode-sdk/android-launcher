package org.cocos2dx.lib

import android.content.Context
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

class Cocos2dxEditText(context: Context) : AppCompatEditText(context) {
    var cocos2dxGLSurfaceView: Cocos2dxGLSurfaceView? = null

    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        super.onKeyDown(keyCode, keyEvent)
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            return true
        }
        cocos2dxGLSurfaceView?.requestFocus()
        return true
    }
}