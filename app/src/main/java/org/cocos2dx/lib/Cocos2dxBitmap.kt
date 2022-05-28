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
    private lateinit var context: WeakReference<Context>

    @JvmStatic
    private external fun nativeInitBitmapDC(pWidth: Int, pHeight: Int, pPixels: ByteArray)

    fun setContext(context: Context) {
        this.context = WeakReference(context)
    }

/*
    fun createTextBitmap(
        string: String,
        fontName: String,
        fontSize: Int,
        alignment: Int,
        width: Int,
        height: Int
    ) {
        createTextBitmapShadowStroke(
            string,
            fontName,
            fontSize,
            1.0f,
            1.0f,
            1.0f,
            alignment,
            width,
            height,
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
*/

    @JvmStatic
    @Suppress("unused")
    fun createTextBitmapShadowStroke(
        string: String,
        fontName: String,
        fontSize: Int,
        fontTintR: Float,
        fontTintG: Float,
        fontTintB: Float,
        alignment: Int,
        width: Int,
        height: Int,
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
        val horizontalAlignment = alignment and 15
        val verticalAlignment = alignment shr 4 and 15
        val pString2 = refactorString(string)
        val paint = newPaint(fontName, fontSize, horizontalAlignment)
        paint.setARGB(
            MotionEvent.ACTION_MASK,
            (255.0 * fontTintR.toDouble()).toInt(),
            (255.0 * fontTintG.toDouble()).toInt(),
            (255.0 * fontTintB.toDouble()).toInt()
        )
        val textProperty = computeTextProperty(pString2, width, height, paint)
        bitmapTotalHeight = if (height == 0) {
            textProperty.totalHeight
        } else {
            height
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
            textProperty.maxWidth + bitmapPaddingX.toInt(),
            bitmapPaddingY.toInt() + bitmapTotalHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val fontMetricsInt = paint.fontMetricsInt
        var y = computeY(fontMetricsInt, height, textProperty.totalHeight, verticalAlignment)
        val lines = textProperty.lines
        val length = lines.size
        for (i in 0 until length) {
            val line = lines[i]
            canvas.drawText(
                line!!,
                computeX(
                    line,
                    textProperty.maxWidth,
                    horizontalAlignment
                ).toFloat() + renderTextDeltaX,
                y.toFloat() + renderTextDeltaY,
                paint
            )
            y += textProperty.heightPerLine
        }
        if (stroke) {
            val paintStroke = newPaint(fontName, fontSize, horizontalAlignment)
            paintStroke.style = Paint.Style.STROKE
            paintStroke.strokeWidth = 0.5f * strokeSize
            paintStroke.setARGB(
                MotionEvent.ACTION_MASK,
                strokeR.toInt() * MotionEvent.ACTION_MASK,
                strokeG.toInt() * MotionEvent.ACTION_MASK,
                strokeB.toInt() * MotionEvent.ACTION_MASK
            )
            var y2 = computeY(fontMetricsInt, height, textProperty.totalHeight, verticalAlignment)
            val lines2 = textProperty.lines
            val length2 = lines2.size
            for (i2 in 0 until length2) {
                val line2 = lines2[i2]
                canvas.drawText(
                    line2!!,
                    computeX(
                        line2,
                        textProperty.maxWidth,
                        horizontalAlignment
                    ).toFloat() + renderTextDeltaX,
                    y2.toFloat() + renderTextDeltaY,
                    paintStroke
                )
                y2 += textProperty.heightPerLine
            }
        }
        initNativeObject(bitmap)
    }

    private fun newPaint(fontName: String, fontSize: Int, horizontalAlignment: Int): Paint {
        val paint = Paint()
        paint.color = -1
        paint.textSize = fontSize.toFloat()
        paint.isAntiAlias = true
        if (fontName.endsWith(".ttf")) {
            try {
                context.get()?.apply {
                    paint.typeface = Cocos2dxTypefaces[this, fontName]
                }
            } catch (e: Exception) {
                Log.e("Cocos2dxBitmap", "error to create ttf type face: $fontName")
                paint.typeface = Typeface.create(fontName, Typeface.NORMAL)
            }
        } else {
            paint.typeface = Typeface.create(fontName, Typeface.NORMAL)
        }
        when (horizontalAlignment) {
            2 -> paint.textAlign = Paint.Align.RIGHT
            3 -> paint.textAlign = Paint.Align.CENTER
            else -> paint.textAlign = Paint.Align.LEFT
        }
        return paint
    }

    private fun computeTextProperty(
        string: String,
        width: Int,
        height: Int,
        paint: Paint
    ): TextProperty {
        val fm = paint.fontMetricsInt
        val h = ceil((fm.bottom - fm.top).toDouble()).toInt()
        var maxContentWidth = 0
        val lines = splitString(string, width, height, paint)
        if (width != 0) {
            maxContentWidth = width
        } else {
            for (line in lines) {
                val temp = ceil(paint.measureText(line, 0, line!!.length).toDouble()).toInt()
                if (temp > maxContentWidth) {
                    maxContentWidth = temp
                }
            }
        }
        return TextProperty(maxContentWidth, h, lines)
    }

    private fun computeX(@Suppress("UNUSED_PARAMETER") text: String?, maxWidth: Int, horizontalAlignment: Int): Int {
        return when (horizontalAlignment) {
            2 -> maxWidth
            3 -> maxWidth / 2
            else -> 0
        }
    }

    private fun computeY(
        fontMetricsInt: FontMetricsInt,
        constrainHeight: Int,
        totalHeight: Int,
        verticalAlignment: Int
    ): Int {
        val y = -fontMetricsInt.top
        return if (constrainHeight <= totalHeight) {
            y
        } else when (verticalAlignment) {
            1 -> -fontMetricsInt.top
            2 -> -fontMetricsInt.top + (constrainHeight - totalHeight)
            3 -> -fontMetricsInt.top + (constrainHeight - totalHeight) / 2
            else -> y
        }
    }

    private fun splitString(
        string: String,
        maxWidth: Int,
        maxHeight: Int,
        paint: Paint
    ): Array<String?> {
        val lines: Array<String?> = string.split("\\n").toTypedArray()
        val fm = paint.fontMetricsInt
        val maxLines = maxHeight / ceil((fm.bottom - fm.top).toDouble()).toInt()
        return if (maxWidth != 0) {
            val strList = LinkedList<String?>()
            for (line in lines) {
                if (ceil(paint.measureText(line).toDouble()).toInt() > maxWidth) {
                    strList.addAll(divideStringWithMaxWidth(line, maxWidth, paint))
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
        } else if (maxHeight == 0 || lines.size <= maxLines) {
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
        string: String?,
        maxWidth: Int,
        paint: Paint
    ): LinkedList<String?> {
        val charLength = string!!.length
        var start = 0
        val strList = LinkedList<String?>()
        var i = 1
        while (i <= charLength) {
            val tempWidth = ceil(paint.measureText(string, start, i).toDouble()).toInt()
            if (tempWidth >= maxWidth) {
                val lastIndexOfSpace = string.substring(0, i).lastIndexOf(" ")
                if (lastIndexOfSpace != -1 && lastIndexOfSpace > start) {
                    strList.add(string.substring(start, lastIndexOfSpace))
                    i = lastIndexOfSpace + 1
                } else if (tempWidth > maxWidth) {
                    strList.add(string.substring(start, i - 1))
                    i--
                } else {
                    strList.add(string.substring(start, i))
                }
                while (i < charLength && string[i] == ' ') {
                    i++
                }
                start = i
            }
            i++
        }
        if (start < charLength) {
            strList.add(string.substring(start))
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
    private fun getStringWithEllipsis(string: String, width: Float, fontSize: Float): String {
        if (TextUtils.isEmpty(string)) {
            return ""
        }
        val paint = TextPaint()
        paint.typeface = Typeface.DEFAULT
        paint.textSize = fontSize
        return TextUtils.ellipsize(string, paint, width, TextUtils.TruncateAt.END).toString()
    }

    private class TextProperty constructor(
        val maxWidth: Int,
        val heightPerLine: Int, pLines: Array<String?>
    ) {
        val lines: Array<String?>

        val totalHeight: Int

        init {
            totalHeight = pLines.size * heightPerLine
            lines = pLines
        }
    }
}