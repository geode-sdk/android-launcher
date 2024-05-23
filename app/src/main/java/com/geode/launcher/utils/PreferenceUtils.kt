package com.geode.launcher.utils

import android.content.Context
import android.content.SharedPreferences
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
import com.geode.launcher.BuildConfig

/**
 * Extension object for SharedPreferences to add better key safety and default values.
 */
class PreferenceUtils(private val sharedPreferences: SharedPreferences) {
    companion object {
        private const val FILE_KEY = "GeodeLauncherPreferencesFileKey"

        @Composable
        fun useBooleanPreference(preferenceKey: Key, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): MutableState<Boolean> {
            return usePreference(
                preferenceKey,
                lifecycleOwner,
                preferenceGet = { p, k -> p.getBoolean(k) },
                preferenceSet = { p, k, v -> p.setBoolean(k, v) }
            )
        }

        @Composable
        fun useStringPreference(preferenceKey: Key, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): MutableState<String?> {
            return usePreference(
                preferenceKey,
                lifecycleOwner,
                preferenceGet = { p, k -> p.getString(k) },
                preferenceSet = { p, k, v -> p.setString(k, v) }
            )
        }

        @Composable
        fun useIntPreference(preferenceKey: Key, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): MutableState<Int> {
            return usePreference(
                preferenceKey,
                lifecycleOwner,
                preferenceGet = { p, k -> p.getInt(k) },
                preferenceSet = { p, k, v -> p.setInt(k, v) }
            )
        }

        @Composable
        fun useLongPreference(preferenceKey: Key, lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): MutableState<Long> {
            return usePreference(
                preferenceKey,
                lifecycleOwner,
                preferenceGet = { p, k -> p.getLong(k) },
                preferenceSet = { p, k, v -> p.setLong(k, v) }
            )
        }

        @Composable
        private fun <T> usePreference(
            preferenceKey: Key,
            lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
            preferenceGet: (PreferenceUtils, Key) -> T,
            preferenceSet: (PreferenceUtils, Key, T) -> Unit
        ): MutableState<T> {
            val context = LocalContext.current
            val sharedPreferences = context.getSharedPreferences(FILE_KEY, Context.MODE_PRIVATE)

            val preferences = get(sharedPreferences)

            val preferenceValue = remember {
                val state = mutableStateOf(preferenceGet(preferences, preferenceKey))
                object : MutableState<T> by state {
                    override var value: T
                        get() = state.value
                        set(value) {
                            // ignore setting the same thing (messes up default preferences)
                            if (state.value == value) { return }
                            state.value = value

                            preferenceSet(preferences, preferenceKey, value)
                        }
                }
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
        LOAD_AUTOMATICALLY,
        UPDATE_AUTOMATICALLY,
        RELEASE_CHANNEL,
        CURRENT_VERSION_TAG,
        CURRENT_VERSION_TIMESTAMP,
        THEME,
        BLACK_BACKGROUND,
        CURRENT_RELEASE_MODIFIED,
        LAST_DISMISSED_UPDATE,
        DISMISSED_GJ_UPDATE,
        LAUNCH_ARGUMENTS,
        LIMIT_ASPECT_RATIO,
        DISPLAY_MODE,
        FORCE_HRR
    }

    private fun defaultValueForBooleanKey(key: Key): Boolean {
        return when (key) {
            Key.UPDATE_AUTOMATICALLY, Key.FORCE_HRR -> true
            Key.RELEASE_CHANNEL -> BuildConfig.DEBUG
            else -> false
        }
    }

    private fun defaultValueForIntKey(key: Key) = when (key) {
        Key.DISPLAY_MODE -> if (this.getBoolean(Key.LIMIT_ASPECT_RATIO)) 1 else 0
        else -> 0
    }

    private fun keyToName(key: Key): String {
        return when (key) {
            Key.LOAD_AUTOMATICALLY -> "PreferenceLoadAutomatically"
            Key.UPDATE_AUTOMATICALLY -> "PreferenceUpdateAutomatically"
            Key.RELEASE_CHANNEL -> "PreferenceReleaseChannel"
            Key.CURRENT_VERSION_TAG -> "PreferenceCurrentVersionName"
            Key.CURRENT_VERSION_TIMESTAMP -> "PreferenceCurrentVersionDescriptor"
            Key.THEME -> "PreferenceTheme"
            Key.BLACK_BACKGROUND -> "PreferenceBlackBackground"
            Key.CURRENT_RELEASE_MODIFIED -> "PreferenceReleaseModifiedHash"
            Key.LAST_DISMISSED_UPDATE -> "PreferenceLastDismissedUpdate"
            Key.DISMISSED_GJ_UPDATE -> "PreferenceDismissedGJUpdate"
            Key.LAUNCH_ARGUMENTS -> "PreferenceLaunchArguments"
            Key.LIMIT_ASPECT_RATIO -> "PreferenceLimitAspectRatio"
            Key.DISPLAY_MODE -> "PreferenceDisplayMode"
            Key.FORCE_HRR -> "PreferenceForceHighRefreshRate"
        }
    }

    fun getBoolean(key: Key): Boolean {
        val defaultValue = defaultValueForBooleanKey(key)
        val keyName = keyToName(key)

        return sharedPreferences.getBoolean(keyName, defaultValue)
    }

    fun setBoolean(key: Key, value: Boolean) {
        val keyName = keyToName(key)
        sharedPreferences.edit {
            putBoolean(keyName, value)
        }
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

    fun setString(key: Key, value: String?) {
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

    fun getInt(key: Key): Int {
        val keyName = keyToName(key)
        return sharedPreferences.getInt(keyName, defaultValueForIntKey(key))
    }

    fun setInt(key: Key, value: Int) {
        val keyName = keyToName(key)
        sharedPreferences.edit {
            putInt(keyName, value)
        }
    }
}