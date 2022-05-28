package org.cocos2dx.lib

import android.content.Context
import android.graphics.*
import android.graphics.Paint.FontMetricsInt
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil

@Suppress("KotlinJniMissingFunction")
object Cocos2dxBitmap {
    private lateinit var sContext: WeakReference<Context>

    @JvmStatic
    private external fun nativeInitBitmapDC(pWidth: Int, pHeight: Int, pPixels: ByteArray)

    fun setContext(pContext: Context) {
        sContext = WeakReference(pContext)
    }

    fun createTextBitmap(
        pString: String,
        pFontName: String,
        pFontSize: Int,
        pAlignment: Int,
        pWidth: Int,
        pHeight: Int
    ) {
        createTextBitmapShadowStroke(
            pString,
            pFontName,
            pFontSize,
            1.0f,
            1.0f,
            1.0f,
            pAlignment,
            pWidth,
            pHeight,
            false,
            0.0f,
            0.0f,
            0.0f,
            false,
            1.0f,
            1.0f,
            1.0f,
            1.0f
        )
    }

    @JvmStatic
    @Suppress("unused")
    fun createTextBitmapShadowStroke(
        pString: String,
        pFontName: String,
        pFontSize: Int,
        fontTintR: Float,
        fontTintG: Float,
        fontTintB: Float,
        pAlignment: Int,
        pWidth: Int,
        pHeight: Int,
        shadow: Boolean,
        shadowDX: Float,
        shadowDY: Float,
        shadowBlur: Float,
        stroke: Boolean,
        strokeR: Float,
        strokeG: Float,
        strokeB: Float,
        strokeSize: Float
    ) {
        val bitmapTotalHeight: Int
        val horizontalAlignment = pAlignment and 15
        val verticalAlignment = pAlignment shr 4 and 15
        val pString2 = refactorString(pString)
        val paint = newPaint(pFontName, pFontSize, horizontalAlignment)
        paint.setARGB(
            MotionEvent.ACTION_MASK,
            (255.0 * fontTintR.toDouble()).toInt(),
            (255.0 * fontTintG.toDouble()).toInt(),
            (255.0 * fontTintB.toDouble()).toInt()
        )
        val textProperty = computeTextProperty(pString2, pWidth, pHeight, paint)
        bitmapTotalHeight = if (pHeight == 0) {
            textProperty.mTotalHeight
        } else {
            pHeight
        }
        var bitmapPaddingX = 0.0f
        var bitmapPaddingY = 0.0f
        var renderTextDeltaX = 0.0f
        var renderTextDeltaY = 0.0f
        if (shadow) {
            paint.setShadowLayer(shadowBlur, shadowDX, shadowDY, -8553091)
            bitmapPaddingX = abs(shadowDX)
            bitmapPaddingY = abs(shadowDY)
            if (shadowDX.toDouble() < 0.0) {
                renderTextDeltaX = bitmapPaddingX
            }
            if (shadowDY.toDouble() < 0.0) {
                renderTextDeltaY = bitmapPaddingY
            }
        }
        val bitmap = Bitmap.createBitmap(
            textProperty.mMaxWidth + bitmapPaddingX.toInt(),
            bitmapPaddingY.toInt() + bitmapTotalHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val fontMetricsInt = paint.fontMetricsInt
        var y = computeY(fontMetricsInt, pHeight, textProperty.mTotalHeight, verticalAlignment)
        val lines = textProperty.mLines
        val length = lines.size
        for (i in 0 until length) {
            val line = lines[i]
            canvas.drawText(
                line!!,
                computeX(
                    line,
                    textProperty.mMaxWidth,
                    horizontalAlignment
                ).toFloat() + renderTextDeltaX,
                y.toFloat() + renderTextDeltaY,
                paint
            )
            y += textProperty.mHeightPerLine
        }
        if (stroke) {
            val paintStroke = newPaint(pFontName, pFontSize, horizontalAlignment)
            paintStroke.style = Paint.Style.STROKE
            paintStroke.strokeWidth = 0.5f * strokeSize
            paintStroke.setARGB(
                MotionEvent.ACTION_MASK,
                strokeR.toInt() * MotionEvent.ACTION_MASK,
                strokeG.toInt() * MotionEvent.ACTION_MASK,
                strokeB.toInt() * MotionEvent.ACTION_MASK
            )
            var y2 = computeY(fontMetricsInt, pHeight, textProperty.mTotalHeight, verticalAlignment)
            val lines2 = textProperty.mLines
            val length2 = lines2.size
            for (i2 in 0 until length2) {
                val line2 = lines2[i2]
                canvas.drawText(
                    line2!!,
                    computeX(
                        line2,
                        textProperty.mMaxWidth,
                        horizontalAlignment
                    ).toFloat() + renderTextDeltaX,
                    y2.toFloat() + renderTextDeltaY,
                    paintStroke
                )
                y2 += textProperty.mHeightPerLine
            }
        }
        initNativeObject(bitmap)
    }

    private fun newPaint(pFontName: String, pFontSize: Int, pHorizontalAlignment: Int): Paint {
        val paint = Paint()
        paint.color = -1
        paint.textSize = pFontSize.toFloat()
        paint.isAntiAlias = true
        if (pFontName.endsWith(".ttf")) {
            try {
                sContext.get()?.apply {
                    paint.typeface = Cocos2dxTypefaces[this, pFontName]
                }
            } catch (e: Exception) {
                Log.e("Cocos2dxBitmap", "error to create ttf type face: $pFontName")
                paint.typeface = Typeface.create(pFontName, Typeface.NORMAL)
            }
        } else {
            paint.typeface = Typeface.create(pFontName, Typeface.NORMAL)
        }
        when (pHorizontalAlignment) {
            2 -> paint.textAlign = Paint.Align.RIGHT
            3 -> paint.textAlign = Paint.Align.CENTER
            else -> paint.textAlign = Paint.Align.LEFT
        }
        return paint
    }

    private fun computeTextProperty(
        pString: String,
        pWidth: Int,
        pHeight: Int,
        pPaint: Paint
    ): TextProperty {
        val fm = pPaint.fontMetricsInt
        val h = ceil((fm.bottom - fm.top).toDouble()).toInt()
        var maxContentWidth = 0
        val lines = splitString(pString, pWidth, pHeight, pPaint)
        if (pWidth != 0) {
            maxContentWidth = pWidth
        } else {
            for (line in lines) {
                val temp = ceil(pPaint.measureText(line, 0, line!!.length).toDouble()).toInt()
                if (temp > maxContentWidth) {
                    maxContentWidth = temp
                }
            }
        }
        return TextProperty(maxContentWidth, h, lines)
    }

    private fun computeX(@Suppress("UNUSED_PARAMETER") pText: String?, pMaxWidth: Int, pHorizontalAlignment: Int): Int {
        return when (pHorizontalAlignment) {
            2 -> pMaxWidth
            3 -> pMaxWidth / 2
            else -> 0
        }
    }

    private fun computeY(
        pFontMetricsInt: FontMetricsInt,
        pConstrainHeight: Int,
        pTotalHeight: Int,
        pVerticalAlignment: Int
    ): Int {
        val y = -pFontMetricsInt.top
        return if (pConstrainHeight <= pTotalHeight) {
            y
        } else when (pVerticalAlignment) {
            1 -> -pFontMetricsInt.top
            2 -> -pFontMetricsInt.top + (pConstrainHeight - pTotalHeight)
            3 -> -pFontMetricsInt.top + (pConstrainHeight - pTotalHeight) / 2
            else -> y
        }
    }

    private fun splitString(
        pString: String,
        pMaxWidth: Int,
        pMaxHeight: Int,
        pPaint: Paint
    ): Array<String?> {
        val lines: Array<String?> = pString.split("\\n").toTypedArray()
        val fm = pPaint.fontMetricsInt
        val maxLines = pMaxHeight / ceil((fm.bottom - fm.top).toDouble()).toInt()
        return if (pMaxWidth != 0) {
            val strList = LinkedList<String?>()
            for (line in lines) {
                if (ceil(pPaint.measureText(line).toDouble()).toInt() > pMaxWidth) {
                    strList.addAll(divideStringWithMaxWidth(line, pMaxWidth, pPaint))
                } else {
                    strList.add(line)
                }
                if (maxLines > 0 && strList.size >= maxLines) {
                    break
                }
            }
            if (maxLines > 0 && strList.size > maxLines) {
                while (strList.size > maxLines) {
                    strList.removeLast()
                }
            }
            val ret = arrayOfNulls<String>(strList.size)
            strList.toArray(ret)
            ret
        } else if (pMaxHeight == 0 || lines.size <= maxLines) {
            lines
        } else {
            val strList2 = LinkedList<String?>()
            for (i in 0 until maxLines) {
                strList2.add(lines[i])
            }
            val ret2 = arrayOfNulls<String>(strList2.size)
            strList2.toArray(ret2)
            ret2
        }
    }

    private fun divideStringWithMaxWidth(
        pString: String?,
        pMaxWidth: Int,
        pPaint: Paint
    ): LinkedList<String?> {
        val charLength = pString!!.length
        var start = 0
        val strList = LinkedList<String?>()
        var i = 1
        while (i <= charLength) {
            val tempWidth = ceil(pPaint.measureText(pString, start, i).toDouble()).toInt()
            if (tempWidth >= pMaxWidth) {
                val lastIndexOfSpace = pString.substring(0, i).lastIndexOf(" ")
                if (lastIndexOfSpace != -1 && lastIndexOfSpace > start) {
                    strList.add(pString.substring(start, lastIndexOfSpace))
                    i = lastIndexOfSpace + 1
                } else if (tempWidth > pMaxWidth) {
                    strList.add(pString.substring(start, i - 1))
                    i--
                } else {
                    strList.add(pString.substring(start, i))
                }
                while (i < charLength && pString[i] == ' ') {
                    i++
                }
                start = i
            }
            i++
        }
        if (start < charLength) {
            strList.add(pString.substring(start))
        }
        return strList
    }

    private fun refactorString(pString: String): String {
        if (pString.compareTo("") == 0) {
            return " "
        }
        val strBuilder = StringBuilder(pString)
        var start = 0
        var index = strBuilder.indexOf("\n")
        while (index != -1) {
            start = if (index == 0 || strBuilder[index - 1].code == 10) {
                strBuilder.insert(start, " ")
                index + 2
            } else {
                index + 1
            }
            if (start > strBuilder.length || index == strBuilder.length) {
                break
            }
            index = strBuilder.indexOf("\n", start)
        }
        return strBuilder.toString()
    }

    private fun initNativeObject(pBitmap: Bitmap) {
        val pixels = getPixels(pBitmap)
        if (pixels != null) {
            nativeInitBitmapDC(pBitmap.width, pBitmap.height, pixels)
        }
    }

    private fun getPixels(pBitmap: Bitmap?): ByteArray? {
        if (pBitmap == null) {
            return null
        }
        val pixels = ByteArray(pBitmap.width * pBitmap.height * 4)
        val buf = ByteBuffer.wrap(pixels)
        buf.order(ByteOrder.nativeOrder())
        pBitmap.copyPixelsToBuffer(buf)
        return pixels
    }

    @Suppress("unused")
    @JvmStatic
    private fun getFontSizeAccordingHeight(height: Int): Int {
        val paint = Paint()
        val bounds = Rect()
        paint.typeface = Typeface.DEFAULT
        var incrTextSize = 1
        var foundDesiredSize = false
        while (!foundDesiredSize) {
            paint.textSize = incrTextSize.toFloat()
            paint.getTextBounds("SghMNy", 0, "SghMNy".length, bounds)
            incrTextSize++
            if (height - bounds.height() <= 2) {
                foundDesiredSize = true
            }
            Log.d("font size", "incr size:$incrTextSize")
        }
        return incrTextSize
    }

    @Suppress("unused")
    @JvmStatic
    private fun getStringWithEllipsis(pString: String, width: Float, fontSize: Float): String {
        if (TextUtils.isEmpty(pString)) {
            return ""
        }
        val paint = TextPaint()
        paint.typeface = Typeface.DEFAULT
        paint.textSize = fontSize
        return TextUtils.ellipsize(pString, paint, width, TextUtils.TruncateAt.END).toString()
    }

    private class TextProperty constructor(/* access modifiers changed from: private */
        val mMaxWidth: Int, /* access modifiers changed from: private */
        val mHeightPerLine: Int, pLines: Array<String?>
    ) {
        /* access modifiers changed from: private */
        val mLines: Array<String?>

        /* access modifiers changed from: private */
        val mTotalHeight: Int

        init {
            mTotalHeight = pLines.size * mHeightPerLine
            mLines = pLines
        }
    }
}