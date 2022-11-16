package com.geode.launcher.utils

import android.os.CountDownTimer
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

const val MS_TO_SEC = 1000

@Composable
fun countdownTimerWatcher(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, time: Int, onCountdownFinish: () -> Unit): MutableState<Int> {
    val timeData = remember {
        mutableStateOf(time / MS_TO_SEC % MS_TO_SEC)
    }

    val countdownTimer =
        object : CountDownTimer(time.toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeData.value = (millisUntilFinished / MS_TO_SEC % MS_TO_SEC).toInt() + 1
            }

            override fun onFinish() {
                onCountdownFinish()
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                countdownTimer.start()
            } else if (event == Lifecycle.Event.ON_STOP) {
                countdownTimer.cancel()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return timeData
}