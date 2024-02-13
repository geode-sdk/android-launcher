package com.geode.launcher.utils

import android.content.Context
import android.widget.FrameLayout

class ConstrainedFrameLayout(context: Context) : FrameLayout(context) {
    // avoid running the aspect ratio fix when we don't need it
    var aspectRatio: Float? = null;

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (aspectRatio != null) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            if (width == 0 || height == 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }

            val calculatedWidth = height * (aspectRatio ?: 1.0f);
            val padding = ((width - calculatedWidth) / 2).toInt()

            // ignore paddings where the screen ends up looking odd
            if (padding > 0) {
                setPadding(padding, 0, padding, 0)
            }
        }

        super.onMeasure(
            widthMeasureSpec,
            heightMeasureSpec
        )
    }
}