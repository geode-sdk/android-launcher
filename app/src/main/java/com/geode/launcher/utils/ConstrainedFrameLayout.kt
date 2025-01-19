package com.geode.launcher.utils

import android.content.Context
import android.widget.FrameLayout
import kotlin.math.roundToInt

class ConstrainedFrameLayout(context: Context) : FrameLayout(context) {
    // avoid running the aspect ratio fix when we don't need it
    var aspectRatio: Float? = null
    var zoom: Float? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var paddingX = 0
        var paddingY = 0

        val currentAspectRatio = aspectRatio
        if (currentAspectRatio != null) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            if (width == 0 || height == 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            val calculatedWidth = height * currentAspectRatio
            val padding = ((width - calculatedWidth) / 2).toInt()

            // ignore paddings where the screen ends up looking odd
            paddingX += padding
        }

        val currentZoom = zoom
        if (currentZoom != null) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            if (width == 0 || height == 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            val zoomFactor = 1 - currentZoom
            val widthFactor = width/2.0f + paddingX/4.0f
            val heightFactor = height/2.0f

            paddingX = (widthFactor * zoomFactor).roundToInt()
            paddingY = (heightFactor * zoomFactor).roundToInt()
        }

        if (paddingX > 0 || paddingY > 0) {
            setPadding(paddingX, paddingY, paddingX, paddingY)
        }

        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )
    }
}