package com.geode.launcher.utils

import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

const val MS_TO_SEC = 1000L

@Composable
fun useCountdownTimer(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    time: Long,
    onCountdownFinish: () -> Unit
): Long {
    var millisUntilFinished by remember {
        mutableLongStateOf(time)
    }

    var shouldBeCounting by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(shouldBeCounting, millisUntilFinished) {
        if (!shouldBeCounting) {
            return@LaunchedEffect
        }

        if (millisUntilFinished > 0) {
            delay(MS_TO_SEC)
            millisUntilFinished -= MS_TO_SEC
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

    return millisUntilFinished / MS_TO_SEC
}