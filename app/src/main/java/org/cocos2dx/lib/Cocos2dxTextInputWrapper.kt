package org.cocos2dx.lib

import android.content.Context
import android.text.TextWatcher
import android.widget.TextView.OnEditorActionListener
import android.text.Editable
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.customRobTop.JniToCpp

class Cocos2dxTextInputWrapper(private val cocos2dxGLSurfaceView: Cocos2dxGLSurfaceView) : TextWatcher, OnEditorActionListener {
    private var originText: String? = null
    private var text: String? = null

    private val isFullScreenEdit: Boolean
        get() = (cocos2dxGLSurfaceView.cocos2dxEditText?.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).isFullscreenMode

    fun setOriginText(originText: String?) {
        this.originText = originText
    }

    override fun afterTextChanged(editable: Editable) {
        if (isFullScreenEdit) {
            return
        }

        val cText = text ?: return

        val str = editable.toString()
        val unchanged = str.commonPrefixWith(cText)

        var unchangedUntil = unchanged.length
        if (unchangedUntil > 0 && unchangedUntil < str.length && Character.isLowSurrogate(unchanged.last())) {
            unchangedUntil--
        }

        val codePointLength = cText.codePointCount(unchangedUntil, cText.length)
        repeat(codePointLength) {
            cocos2dxGLSurfaceView.deleteBackward()
        }

        if (unchangedUntil < str.length) {
            cocos2dxGLSurfaceView.insertText(
                str.substring(unchangedUntil)
            )
        }

        text = str
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
        val fullscreenIme = cocos2dxGLSurfaceView.cocos2dxEditText == textView && isFullScreenEdit
        if (fullscreenIme) {
            var text = textView.text.toString()
            if (originText != text) {
                if (cocos2dxGLSurfaceView.sendNewCocosEvents) {
                    cocos2dxGLSurfaceView.queueEvent {
                        JniToCpp.textInputCaretToEnd()
                    }
                }

                for (i in originText!!.length downTo 1) {
                    cocos2dxGLSurfaceView.deleteBackward()
                }

                // this bit was removed in 2.209, and I don't know if it's important on previous versions
                if (!cocos2dxGLSurfaceView.sendNewCocosEvents) {
                    if (!text.endsWith('\n')) {
                        text += '\n'
                    }
                }

                cocos2dxGLSurfaceView.insertText(text)
            }
        }

        if (fullscreenIme || actionID == EditorInfo.IME_ACTION_DONE) {
            if (cocos2dxGLSurfaceView.sendNewCocosEvents) {
                cocos2dxGLSurfaceView.queueEvent {
                    JniToCpp.textInputDone()
                }
            }
        }

        if (actionID == EditorInfo.IME_ACTION_DONE) {
            cocos2dxGLSurfaceView.requestFocus()
            Cocos2dxGLSurfaceView.closeIMEKeyboard()
        }

        return false
    }
}