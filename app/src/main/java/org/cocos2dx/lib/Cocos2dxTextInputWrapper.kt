package org.cocos2dx.lib

import android.content.Context
import android.text.TextWatcher
import android.widget.TextView.OnEditorActionListener
import android.text.Editable
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

class Cocos2dxTextInputWrapper(private val mCocos2dxGLSurfaceView: Cocos2dxGLSurfaceView) : TextWatcher, OnEditorActionListener {
    private var mOriginText: String? = null
    private var mText: String? = null

    private val isFullScreenEdit: Boolean
        get() = (mCocos2dxGLSurfaceView.cocos2dxEditText?.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).isFullscreenMode

    fun setOriginText(pOriginText: String?) {
        mOriginText = pOriginText
    }

    override fun afterTextChanged(s: Editable) {
        if (!isFullScreenEdit) {
            var nModified = s.length - mText!!.length
            if (nModified > 0) {
                mCocos2dxGLSurfaceView.insertText(
                    s.subSequence(mText!!.length, s.length).toString()
                )
            } else {
                while (nModified < 0) {
                    mCocos2dxGLSurfaceView.deleteBackward()
                    nModified++
                }
            }
            mText = s.toString()
        }
    }

    override fun beforeTextChanged(
        pCharSequence: CharSequence,
        start: Int,
        count: Int,
        after: Int
    ) {
        mText = pCharSequence.toString()
    }

    override fun onTextChanged(pCharSequence: CharSequence, start: Int, before: Int, count: Int) {}
    override fun onEditorAction(pTextView: TextView, pActionID: Int, pKeyEvent: KeyEvent?): Boolean {
        if (mCocos2dxGLSurfaceView.cocos2dxEditText == pTextView && isFullScreenEdit) {
            for (i in mOriginText!!.length downTo 1) {
                mCocos2dxGLSurfaceView.deleteBackward()
            }
            var text = pTextView.text.toString()
            if (text.compareTo("") == 0) {
                text = "\n"
            }
            if (10 != text[text.length - 1].code) {
                text += 10.toChar()
            }
            mCocos2dxGLSurfaceView.insertText(text)
        }
        if (pActionID != 6) {
            return false
        }
        mCocos2dxGLSurfaceView.requestFocus()
        Cocos2dxGLSurfaceView.closeIMEKeyboard()
        return false
    }
}