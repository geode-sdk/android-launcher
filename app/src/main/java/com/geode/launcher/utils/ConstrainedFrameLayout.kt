package com.geode.launcher.utils

import android.content.Context
import android.widget.FrameLayout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class ConstrainedFrameLayout(context: Context) : FrameLayout(context) {
    // avoid running the aspect ratio fix when we don't need it
    var aspectRatio: Float? = null
    var zoom: Float? = null
    var fitZoom: Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var paddingX = 0.0f
        var paddingY = 0.0f

        val currentZoom = zoom
        if (currentZoom != null) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            if (width == 0 || height == 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            val calculatedWidth = width * currentZoom
            val calculatedHeight = height * currentZoom

            paddingX = (width - calculatedWidth)/2
            paddingY = (height - calculatedHeight)/2
        }

        val currentAspectRatio = aspectRatio
        if (currentAspectRatio != null) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            if (width == 0 || height == 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            val calculatedWidth = (height - paddingY * 2) * currentAspectRatio
            val padding = (width - calculatedWidth) / 2

            paddingX = padding
        }

        if (paddingX > 0.0 || paddingY > 0.0) {
            if (fitZoom) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val height = MeasureSpec.getSize(heightMeasureSpec)

                val calculatedHeight = (height - paddingY * 2)
                val calculatedWidth = (width - paddingX * 2)

                val zoomFactor = min(height / calculatedHeight, width / calculatedWidth)

                scaleX = zoomFactor
                scaleY = zoomFactor
            }

            setPadding(
                ceil(paddingX).toInt(),
                ceil(paddingY).toInt(),
                floor(paddingX).toInt(),
                floor(paddingY).toInt()
            )
        }

        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )
    }
}