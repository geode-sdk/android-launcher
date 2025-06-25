package org.cocos2dx.lib

import android.content.Context
import android.text.TextWatcher
import android.widget.TextView.OnEditorActionListener
import android.text.Editable
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

class Cocos2dxTextInputWrapper(private val cocos2dxGLSurfaceView: Cocos2dxGLSurfaceView) : TextWatcher, OnEditorActionListener {
    private var originText: String? = null
    private var text: String? = null

    private val isFullScreenEdit: Boolean
        get() = (cocos2dxGLSurfaceView.cocos2dxEditText?.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).isFullscreenMode

    fun setOriginText(originText: String?) {
        this.originText = originText
    }

    override fun afterTextChanged(s: Editable) {
        if (!isFullScreenEdit) {
            var nModified = s.length - text!!.length
            if (nModified > 0) {
                cocos2dxGLSurfaceView.insertText(
                    s.subSequence(text!!.length, s.length).toString()
                )
            } else {
                while (nModified < 0) {
                    cocos2dxGLSurfaceView.deleteBackward()
                    nModified++
                }
            }
            text = s.toString()
        }
    }

    override fun beforeTextChanged(
        charSequence: CharSequence,
        start: Int,
        count: Int,
        after: Int
    ) {
        text = charSequence.toString()
    }

    override fun onTextChanged(pCharSequence: CharSequence, start: Int, before: Int, count: Int) {}
    override fun onEditorAction(textView: TextView, actionID: Int, keyEvent: KeyEvent?): Boolean {
        if (cocos2dxGLSurfaceView.cocos2dxEditText == textView && isFullScreenEdit) {
            for (i in originText!!.length downTo 1) {
                cocos2dxGLSurfaceView.deleteBackward()
            }
            var text = textView.text.toString()
            if (!text.endsWith('\n')) {
                text += '\n'
            }

            cocos2dxGLSurfaceView.insertText(text)
        }
        if (actionID != 6) {
            return false
        }
        cocos2dxGLSurfaceView.requestFocus()
        Cocos2dxGLSurfaceView.closeIMEKeyboard()
        return false
    }
}