package com.geode.launcher.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner


@Composable
fun preferenceWatcher(@StringRes preferenceFileKey: Int, @StringRes preferenceId: Int, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): MutableState<Boolean> {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences(
        context.getString(preferenceFileKey), Context.MODE_PRIVATE
    )

    val preferenceValue = remember {
        mutableStateOf(
            preferences.getBoolean(
                context.getString(preferenceId),
                false
            )
        )
    }

    val listener = SharedPreferences.OnSharedPreferenceChangeListener {
        sharedPreferences, key -> preferenceValue.value = sharedPreferences.getBoolean(key, false)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                preferences.registerOnSharedPreferenceChangeListener(listener)
            } else if (event == Lifecycle.Event.ON_DESTROY) {
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return preferenceValue
}