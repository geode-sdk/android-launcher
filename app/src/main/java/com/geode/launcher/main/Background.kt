package com.geode.launcher.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import kotlin.math.sin
import kotlin.random.Random

val BACKGROUND_INITIAL = Color(0xff_4b_31_68)
val BACKGROUND_BOTTOM = Color(0xff_19_10_23)

val WAVE_COLOR_1 = Color(0xff_f4_d4_8e)
val WAVE_COLOR_2 = Color(0xff_f5_ae_7d)
val WAVE_COLOR_3 = Color(0xff_ec_5f_4f)
val WAVE_COLOR_4 = Color(0xff_d5_40_4a)

@Composable
fun WithWave(modifier: Modifier = Modifier, contents: @Composable () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(BACKGROUND_INITIAL, BACKGROUND_BOTTOM)
            )
        )
    ) {
        contents()

        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp

        WaveBackground(
            color = WAVE_COLOR_1,
            period = 500.dp,
            periodMod = 200.dp,
            amplitude = 30.dp,
            amplitudeMod = 20.dp,
            baseHeight = 170.dp,
            animationTimeMs = 20_000,
            modifier = Modifier.fillMaxSize().zIndex(-4.0f)
        )

        WaveBackground(
            color = WAVE_COLOR_2,
            period = 600.dp,
            periodMod = 200.dp,
            amplitude = 20.dp,
            amplitudeMod = 20.dp,
            baseHeight = 130.dp,
            animationTimeMs = 24_000,
            modifier = Modifier.fillMaxSize().zIndex(-3.0f)
        )

        WaveBackground(
            color = WAVE_COLOR_3,
            period = 800.dp,
            periodMod = 200.dp,
            amplitude = 20.dp,
            amplitudeMod = 15.dp,
            baseHeight = 80.dp,
            animationTimeMs = 27_000,
            modifier = Modifier.fillMaxSize().zIndex(-2.0f)
        )

        WaveBackground(
            color = WAVE_COLOR_4,
            period = 900.dp,
            periodMod = 200.dp,
            amplitude = 30.dp,
            amplitudeMod = 20.dp,
            baseHeight = 50.dp,
            animationTimeMs = 30_000,
            modifier = Modifier.fillMaxSize().zIndex(-1.0f)
        )
    }
}

@Composable
fun WaveBackground(color: Color, period: Dp, periodMod: Dp, amplitude: Dp, amplitudeMod: Dp, baseHeight: Dp, animationTimeMs: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Wave Animation Container")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 360.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationTimeMs, easing = LinearEasing)
        ),
        label = "Wave Animation"
    )

    val seed = remember { Random.Default.nextInt() }

    val path = remember { Path() }

    Canvas(modifier = modifier) {
        val baseHeightPx = baseHeight.toPx()
        val periodPx = period.toPx()
        val amplitudePx = amplitude.toPx()
        val periodModPx = periodMod.toPx()
        val amplitudeModPx = amplitudeMod.toPx()

        val halfPeriod = periodPx / 2

        drawPath(path, color = color)
        path.reset()

        val radOffset = Math.toRadians(offset.toDouble()).toFloat()

        path.moveTo(0.0f, size.height - baseHeightPx)

        var peakIdx = 0.0f
        var currentX = 0.0f
        val maxX = size.width

        while (currentX < maxX) {
            // using sin is weird because it causes the animation to slow towards the ends
            // that being said, it is a really cheap way of getting a repeating value between 0 and 1

            val dxFactor = sin(peakIdx + radOffset) * periodModPx
            val dx = halfPeriod + dxFactor

            val dyFactor = sin(peakIdx + radOffset) * amplitudeModPx
            val dy = amplitudePx + dyFactor
            peakIdx += seed

            path.relativeQuadraticBezierTo(
                dx / 2,
                -dy,
                dx,
                0.0f
            )

            path.relativeQuadraticBezierTo(
                dx / 2,
                dy,
                dx,
                0.0f
            )

            currentX += dx * 2
        }

        path.lineTo(size.width, size.height)
        path.lineTo(0.0f, size.height)

        path.close()
    }
}