package com.geode.launcher.utils

import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val MS_TO_SEC = 1000L

@Composable
fun useCountdownTimer(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    time: Duration,
    onCountdownFinish: () -> Unit
): Long {
    var millisUntilFinished by remember {
        mutableStateOf(time)
    }

    var shouldBeCounting by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(shouldBeCounting, millisUntilFinished) {
        if (!shouldBeCounting) {
            return@LaunchedEffect
        }

        if (millisUntilFinished > Duration.ZERO) {
            delay(1.seconds)
            millisUntilFinished -= 1.seconds
        } else {
            onCountdownFinish()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    shouldBeCounting = true
                    millisUntilFinished = time
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    shouldBeCounting = false
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return millisUntilFinished.inWholeSeconds
}