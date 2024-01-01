package com.geode.launcher.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Extension object for SharedPreferences to add better key safety and default values.
 */
class PreferenceUtils(private val sharedPreferences: SharedPreferences) {
    companion object {
        private const val FILE_KEY = "GeodeLauncherPreferencesFileKey"

        @Composable
        fun useBooleanPreference(preferenceKey: Key, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): MutableState<Boolean> {
            return usePreference(preferenceKey, lifecycleOwner) { p, k ->
                p.getBoolean(k)
            }
        }

        @Composable
        fun useStringPreference(preferenceKey: Key, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): MutableState<String?> {
            return usePreference(preferenceKey, lifecycleOwner) { p, k ->
                p.getString(k)
            }
        }

        @Composable
        private fun <T> usePreference(preferenceKey: Key, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current, preferenceGet: (PreferenceUtils, Key) -> T): MutableState<T> {
            val context = LocalContext.current
            val sharedPreferences = context.getSharedPreferences(FILE_KEY, Context.MODE_PRIVATE)

            val preferences = get(sharedPreferences)

            val preferenceValue = remember {
                mutableStateOf(
                    preferenceGet(preferences, preferenceKey)
                )
            }

            val listener = SharedPreferences.OnSharedPreferenceChangeListener {
                    _, _ -> preferenceValue.value = preferenceGet(preferences, preferenceKey)
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
                    } else if (event == Lifecycle.Event.ON_DESTROY) {
                        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            return preferenceValue
        }

        fun get(context: Context): PreferenceUtils {
            val sharedPreferences = context.getSharedPreferences(FILE_KEY, Context.MODE_PRIVATE)
            return get(sharedPreferences)
        }

        fun get(sharedPreferences: SharedPreferences): PreferenceUtils {
            return PreferenceUtils(sharedPreferences)
        }
    }

    enum class Key {
        LOAD_TESTING,
        LOAD_AUTOMATICALLY,
        UPDATE_AUTOMATICALLY,
        RELEASE_CHANNEL,
        CURRENT_VERSION_TAG,
        CURRENT_VERSION_TIMESTAMP
    }

    private fun defaultValueForBooleanKey(key: Key): Boolean {
        return when (key) {
            Key.UPDATE_AUTOMATICALLY, Key.RELEASE_CHANNEL -> true
            else -> false
        }
    }

    private fun keyToName(key: Key): String {
        return when (key) {
            Key.LOAD_TESTING -> "PreferenceLoadTesting"
            Key.LOAD_AUTOMATICALLY -> "PreferenceLoadAutomatically"
            Key.UPDATE_AUTOMATICALLY -> "PreferenceUpdateAutomatically"
            Key.RELEASE_CHANNEL -> "PreferenceReleaseChannel"
            Key.CURRENT_VERSION_TAG -> "PreferenceCurrentVersionName"
            Key.CURRENT_VERSION_TIMESTAMP -> "PreferenceCurrentVersionDescriptor"
        }
    }

    fun getBoolean(key: Key): Boolean {
        val defaultValue = defaultValueForBooleanKey(key)
        val keyName = keyToName(key)

        return sharedPreferences.getBoolean(keyName, defaultValue)
    }

    fun toggleBoolean(key: Key): Boolean {
        val currentValue = getBoolean(key)
        val keyName = keyToName(key)

        sharedPreferences.edit {
            putBoolean(keyName, !currentValue)
        }

        return !currentValue
    }

    fun getString(key: Key): String? {
        val keyName = keyToName(key)
        return sharedPreferences.getString(keyName, null)
    }

    fun setString(key: Key, value: String) {
        val keyName = keyToName(key)
        sharedPreferences.edit {
            putString(keyName, value)
        }
    }

    fun getLong(key: Key): Long {
        val keyName = keyToName(key)
        return sharedPreferences.getLong(keyName, 0L)
    }

    fun setLong(key: Key, value: Long) {
        val keyName = keyToName(key)
        sharedPreferences.edit {
            putLong(keyName, value)
        }
    }
}