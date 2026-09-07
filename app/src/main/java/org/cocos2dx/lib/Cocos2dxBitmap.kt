package org.cocos2dx.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import androidx.core.graphics.createBitmap
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.ceil

private const val HORIZONTALALIGN_LEFT = 1
private const val HORIZONTALALIGN_RIGHT = 2
private const val HORIZONTALALIGN_CENTER = 3
private const val VERTICALALIGN_TOP = 1
private const val VERTICALALIGN_BOTTOM = 2
private const val VERTICALALIGN_CENTER = 3

@Suppress("KotlinJniMissingFunction")
object Cocos2dxBitmap {
    private lateinit var context: WeakReference<Context>

    @JvmStatic
    external fun nativeInitBitmapDC(pWidth: Int, pHeight: Int, pPixels: ByteArray)

    fun setContext(context: Context) {
        this.context = WeakReference(context)
    }

    @JvmStatic
    @Suppress("unused")
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
        val horizontalAlignment = alignment and 0x0F
        val verticalAlignment = (alignment shr 4) and 0x0F
        val pString2 = refactorString(string)
        val paint = newPaint(fontName, fontSize, horizontalAlignment)
        paint.setARGB(
            255,
            (255.0 * fontTintR).toInt(),
            (255.0 * fontTintG).toInt(),
            (255.0 * fontTintB).toInt()
        )
        val textProperty = computeTextProperty(pString2, width, height, paint)
        val bitmapTotalHeight = if (height == 0) {
            textProperty.totalHeight
        } else {
            height
        }
        var bitmapPaddingX = 0.0f
        var bitmapPaddingY = 0.0f
        var renderTextDeltaX = 0.0f
        var renderTextDeltaY = 0.0f
        if (shadow) {
            val shadowColor = 0xff7d7d7du.toInt()
            paint.setShadowLayer(shadowBlur, shadowDX, shadowDY, shadowColor)
            bitmapPaddingX = abs(shadowDX)
            bitmapPaddingY = abs(shadowDY)
            if (shadowDX.toDouble() < 0.0) {
                renderTextDeltaX = bitmapPaddingX
            }
            if (shadowDY.toDouble() < 0.0) {
                renderTextDeltaY = bitmapPaddingY
            }
        }

        val bitmap = try {
            createBitmap(
                textProperty.maxWidth + bitmapPaddingX.toInt(),
                bitmapTotalHeight + bitmapPaddingY.toInt()
            )
        } catch (e: Exception) {
            // bandaid fix, hopefully someone checks logs to see this
            Log.e("Cocos2dxBitmap", "Error creating bitmap: $e")
            nativeInitBitmapDC(0, 0, ByteArray(0))
            return
        }

        val canvas = Canvas(bitmap)
        val fontMetricsInt = paint.fontMetricsInt
        var y = computeY(fontMetricsInt, height, textProperty.totalHeight, verticalAlignment)
        val lines = textProperty.lines

        for (line in lines) {
            val x = computeX(line, textProperty.maxWidth, horizontalAlignment)
            canvas.drawText(line, x + renderTextDeltaX, y + renderTextDeltaY, paint)
            y += textProperty.heightPerLine
        }

        if (stroke) {
            val paintStroke = newPaint(fontName, fontSize, horizontalAlignment)
            paintStroke.style = Paint.Style.STROKE
            paintStroke.strokeWidth = strokeSize * 0.5f
            paintStroke.setARGB(
                255,
                strokeR.toInt() * MotionEvent.ACTION_MASK,
                strokeG.toInt() * MotionEvent.ACTION_MASK,
                strokeB.toInt() * MotionEvent.ACTION_MASK
            )
            var y = computeY(fontMetricsInt, height, textProperty.totalHeight, verticalAlignment)
            val lines = textProperty.lines

            for (line in lines) {
                val x = computeX(line, textProperty.maxWidth, horizontalAlignment)
                canvas.drawText(line, x + renderTextDeltaX, y + renderTextDeltaY, paintStroke)
                y += textProperty.heightPerLine
            }
        }
        initNativeObject(bitmap)
    }

    private fun newPaint(fontName: String, fontSize: Int, horizontalAlignment: Int): Paint {
        val paint = Paint()
        paint.color = Color.WHITE
        paint.textSize = fontSize.toFloat()
        paint.isAntiAlias = true
        if (fontName.endsWith(".ttf")) {
            try {
                context.get()!!.apply {
                    paint.typeface = Cocos2dxTypefaces[this, fontName]
                }
            } catch (e: Exception) {
                Log.e("Cocos2dxBitmap", "error to create ttf type face: $fontName - $e")
                paint.typeface = Typeface.create(fontName, Typeface.NORMAL)
            }
        } else {
            paint.typeface = Typeface.create(fontName, Typeface.NORMAL)
        }
        when (horizontalAlignment) {
            HORIZONTALALIGN_RIGHT -> paint.textAlign = Paint.Align.RIGHT
            HORIZONTALALIGN_CENTER -> paint.textAlign = Paint.Align.CENTER
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
        val h = fm.bottom - fm.top
        var maxContentWidth = 0
        val lines = splitString(string, width, height, paint)
        if (width != 0) {
            maxContentWidth = width
        } else {
            for (line in lines) {
                val temp = ceil(paint.measureText(line, 0, line.length)).toInt()
                if (temp > maxContentWidth) {
                    maxContentWidth = temp
                }
            }
        }
        return TextProperty(maxContentWidth, h, lines)
    }

    private fun computeX(@Suppress("UNUSED_PARAMETER") text: String, maxWidth: Int, horizontalAlignment: Int): Int {
        return when (horizontalAlignment) {
            HORIZONTALALIGN_RIGHT -> maxWidth
            HORIZONTALALIGN_CENTER -> maxWidth / 2
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
        return if (constrainHeight > totalHeight) {
            when (verticalAlignment) {
                VERTICALALIGN_TOP -> -fontMetricsInt.top
                VERTICALALIGN_CENTER -> -fontMetricsInt.top + (constrainHeight - totalHeight) / 2
                VERTICALALIGN_BOTTOM -> -fontMetricsInt.top + (constrainHeight - totalHeight)
                else -> y
            }
        } else  {
            y
        }
    }

    private fun splitString(
        string: String,
        maxWidth: Int,
        maxHeight: Int,
        paint: Paint
    ): List<String> {
        val lines = string.split("\n")
        val fm = paint.fontMetricsInt
        val heightPerLine = fm.bottom - fm.top
        val maxLines = maxHeight / heightPerLine
        return if (maxWidth != 0) {
            val strList = LinkedList<String>()
            for (line in lines) {
                val lineWidth = ceil(paint.measureText(line)).toInt()

                if (lineWidth > maxWidth) {
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

            strList
        } else if (maxHeight != 0 && lines.size > maxLines) {
            lines.take(maxLines)
        } else {
            lines
        }
    }

    private fun divideStringWithMaxWidth(
        string: String,
        maxWidth: Int,
        paint: Paint
    ): LinkedList<String> {
        val charLength = string.length
        var start = 0
        val strList = LinkedList<String>()

        var i = 1
        while (i <= charLength) {
            val tempWidth = ceil(paint.measureText(string, start, i)).toInt()
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
            start = if (index == 0 || strBuilder[index - 1] == '\n') {
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
        nativeInitBitmapDC(pBitmap.width, pBitmap.height, pixels)
    }

    private fun getPixels(pBitmap: Bitmap): ByteArray {
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
            val text = "SghMNy"
            paint.getTextBounds(text, 0, text.length, bounds)

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

    private class TextProperty(
        val maxWidth: Int,
        val heightPerLine: Int,
        val lines: Collection<String>
    ) {
        val totalHeight: Int = lines.size * heightPerLine
    }
}