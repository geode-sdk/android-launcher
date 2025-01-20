package com.geode.launcher.main

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geode.launcher.GeometryDashActivity
import com.geode.launcher.R
import com.geode.launcher.preferences.SettingsActivity
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.GeodeUtils
import com.geode.launcher.utils.PreferenceUtils
import com.geode.launcher.utils.useCountdownTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest


@Composable
fun UpdateWarning(inSafeMode: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val gdVersionCode = remember { GamePackageUtils.getGameVersionCode(packageManager) }
    val gdVersionString = remember { GamePackageUtils.getGameVersionString(packageManager) }

    var lastDismissedVersion by PreferenceUtils.useLongPreference(
        preferenceKey = PreferenceUtils.Key.DISMISSED_GJ_UPDATE
    )

    val onUnsupportedVersion = gdVersionCode < Constants.SUPPORTED_VERSION_CODE_MIN || gdVersionCode > Constants.SUPPORTED_VERSION_CODE
    val canDismissRelease = gdVersionCode >= Constants.SUPPORTED_VERSION_CODE
    val shouldDismiss = canDismissRelease && gdVersionCode == lastDismissedVersion

    if (onUnsupportedVersion && !shouldDismiss) {
        AlertDialog(
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.launcher_warning_icon_alt)
                )
            },
            title = {
                Text(stringResource(R.string.launcher_unsupported_version_title))
            },
            text = {
                Text(
                    stringResource(
                    R.string.launcher_unsupported_version_description,
                    gdVersionString,
                    Constants.SUPPORTED_VERSION_STRING
                )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    lastDismissedVersion = gdVersionCode

                    onLaunch(context, inSafeMode)
                }) {
                    Text(stringResource(R.string.message_box_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text(stringResource(R.string.message_box_cancel))
                }
            },
            onDismissRequest = { onDismiss() }
        )
    } else {
        LaunchedEffect(gdVersionCode) {
            onLaunch(context, inSafeMode)
        }
    }
}

@Composable
fun SafeModeDialog(onDismiss: () -> Unit, onLaunch: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = stringResource(R.string.launcher_warning_icon_alt)
            )
        },
        title = { Text(stringResource(R.string.safe_mode_enable_title)) },
        text = { Text(stringResource(R.string.safe_mode_enable_description)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.message_box_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onLaunch) {
                Text(stringResource(R.string.message_box_continue))
            }
        },
        onDismissRequest = onDismiss
    )
}

fun onLaunch(context: Context, safeMode: Boolean = false) {
    if (safeMode) {
        GeodeUtils.setAdditionalLaunchArguments(GeodeUtils.ARGUMENT_SAFE_MODE)
    } else {
        GeodeUtils.clearLaunchArguments()
    }

    val launchIntent = Intent(context, GeometryDashActivity::class.java)
    launchIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

    context.startActivity(launchIntent)
}

fun onSettings(context: Context) {
    val launchIntent = Intent(context, SettingsActivity::class.java)
    context.startActivity(launchIntent)
}

@Composable
fun LongPressButton(onClick: () -> Unit, onLongPress: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    // compose apis don't provide a good way of adding long press to a button
    var isLongPress by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val viewConfiguration = LocalViewConfiguration.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    delay(viewConfiguration.longPressTimeoutMillis)

                    // perform a second delay to make the action more obvious
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(viewConfiguration.longPressTimeoutMillis)

                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                    isLongPress = true
                    onLongPress()
                }
                is PressInteraction.Release -> {
                    if (!isLongPress) {
                        onClick()
                    }
                    isLongPress = false
                }
            }
        }
    }

    Button(
        onClick = {},
        enabled = enabled,
        modifier = modifier,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun PlayButton(
    stopAutomaticLaunch: Boolean,
    blockLaunch: Boolean,
    onPlayGame: (Boolean) -> Unit
) {
    val context = LocalContext.current

    var showSafeModeDialog by remember { mutableStateOf(false) }
    var launchInSafeMode by remember { mutableStateOf(false) }

    val shouldAutomaticallyLaunch by PreferenceUtils.useBooleanPreference(
        preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY
    )

    if (shouldAutomaticallyLaunch && !stopAutomaticLaunch && !showSafeModeDialog) {
        val countdownTimer = useCountdownTimer(
            time = 3000,
            onCountdownFinish = { onPlayGame(launchInSafeMode) }
        )

        if (countdownTimer != 0L) {
            Text(
                pluralStringResource(
                    R.plurals.automatically_load_countdown,
                    countdownTimer.toInt(),
                    countdownTimer
                ),
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.size(12.dp))
        }
    }

    Row {
        LongPressButton(
            enabled = !blockLaunch,
            onLongPress = {
                showSafeModeDialog = true
            },
            onClick = {
                onPlayGame(launchInSafeMode)
            }
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = context.getString(R.string.launcher_launch_icon_alt)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(context.getString(R.string.launcher_launch))
        }
        Spacer(Modifier.size(2.dp))
        IconButton(onClick = { onSettings(context) }) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = context.getString(R.string.launcher_settings_icon_alt)
            )
        }
    }

    if (showSafeModeDialog) {
        SafeModeDialog(
            onDismiss = {
                launchInSafeMode = false
                showSafeModeDialog = false
            },
            onLaunch = {
                launchInSafeMode = true

                onPlayGame(true)

                showSafeModeDialog = false
            }
        )
    }
}

@Composable
fun LaunchBlockedLabel(text: String) {
    val context = LocalContext.current

    val showDownload = remember {
        !GamePackageUtils.isGameInstalled(context.packageManager) ||
                !GamePackageUtils.identifyGameLegitimacy(context.packageManager)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))

        if (showDownload) {
            GooglePlayBadge()
        }
    }
}
