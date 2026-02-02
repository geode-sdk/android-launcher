package com.geode.launcher.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.GeodeUtils
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.delay

enum class LaunchNotificationType {
    GEODE_UPDATED, LAUNCHER_UPDATE_AVAILABLE, UPDATE_FAILED, UNSUPPORTED_VERSION;
}

fun determineDisplayedCards(context: Context): List<LaunchNotificationType> {
    val cards = mutableListOf<LaunchNotificationType>()

    if (GamePackageUtils.getGameVersionCode(context.packageManager) < Constants.SUPPORTED_VERSION_CODE_MIN_WARNING) {
        cards.add(LaunchNotificationType.UNSUPPORTED_VERSION)
    }

    val releaseManager = ReleaseManager.get(context)
    val loadAutomatically = PreferenceUtils.get(context).getBoolean(PreferenceUtils.Key.LOAD_AUTOMATICALLY)

    val availableUpdate = releaseManager.availableLauncherUpdateTag.value
    if (loadAutomatically && availableUpdate != null) {
        cards.add(LaunchNotificationType.LAUNCHER_UPDATE_AVAILABLE)
    }

    val releaseState = releaseManager.uiState.value

    if (releaseState is ReleaseManager.ReleaseManagerState.Finished && releaseState.hasUpdated) {
        cards.add(LaunchNotificationType.GEODE_UPDATED)
    }

    if (releaseState is ReleaseManager.ReleaseManagerState.Failure) {
        cards.add(LaunchNotificationType.UPDATE_FAILED)
    }

    return cards
}

@Composable
fun NotificationCardFromType(type: LaunchNotificationType) {
    when (type) {
        LaunchNotificationType.LAUNCHER_UPDATE_AVAILABLE -> {
            var showInfoDialog by remember { mutableStateOf(false) }

            if (showInfoDialog) {
                LauncherUpdateInformation {
                    showInfoDialog = false
                }
            }

            AnimatedNotificationCard(
                displayLength = 5000L,
                onClick = {
                    showInfoDialog = true
                }
            ) {
                LauncherUpdateContent()
            }
        }
        LaunchNotificationType.UPDATE_FAILED -> {
            AnimatedNotificationCard {
                UpdateFailedContent()
            }
        }
        LaunchNotificationType.UNSUPPORTED_VERSION -> {
            AnimatedNotificationCard {
                OutdatedVersionContent()
            }
        }
        else -> {}
    }
}

@Composable
fun LaunchNotification() {
    val context = LocalContext.current

    val themeOption by PreferenceUtils.useIntPreference(PreferenceUtils.Key.THEME)
    val theme = Theme.fromInt(themeOption)

    val backgroundOption by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.BLACK_BACKGROUND)

    val cards = determineDisplayedCards(context)

    CompositionLocalProvider(LocalTheme provides theme) {
        GeodeLauncherTheme(theme = theme, blackBackground = backgroundOption) {
            // surface is not in use, so this is unfortunately not provided
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier = if (GeodeUtils.handleSafeArea)
                        Modifier.displayCutoutPadding()
                    else Modifier
                ) {
                    cards.forEach {
                        NotificationCardFromType(it)
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedNotificationCard(modifier: Modifier = Modifier, visibilityDelay: Long = 0L, displayLength: Long = 3000L, onClick: (() -> Unit)? = null, contents: @Composable () -> Unit) {
    val state = remember {
        MutableTransitionState(false).apply {
            targetState = visibilityDelay <= 0
        }
    }

    LaunchedEffect(true) {
        if (visibilityDelay > 0) {
            delay(visibilityDelay)
            state.targetState = true
        }

        delay(displayLength)
        state.targetState = false
    }

    AnimatedVisibility(
        visibleState = state,
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut(),
        modifier = modifier
    ) {
        CardView(onClick, modifier = Modifier.padding(8.dp)) {
            contents()
        }
    }
}

@Composable
fun CardView(onClick: (() -> Unit)?, modifier: Modifier = Modifier, contents: @Composable () -> Unit) {
    // val surfaceColor = MaterialTheme.colorScheme.background.copy(alpha = 0.75f)

    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier
        ) {
            contents()
        }
    } else {
        OutlinedCard(
            modifier = modifier
        ) {
            contents()
        }
    }
}

@Composable
fun LauncherUpdateContent(modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.launcher_update_available))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.launcher_notification_update_cta))
        },
        leadingContent = {
            Icon(
                painterResource(R.drawable.icon_info),
                contentDescription = null,
            )
        },
        modifier = modifier.width(IntrinsicSize.Max)
    )
}

@Composable
fun UpdateNotificationContent(modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.launcher_notification_update_success))
        },
        leadingContent = {
            Icon(
                painterResource(R.drawable.geode_monochrome),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        modifier = modifier.width(IntrinsicSize.Max)
    )
}

@Composable
fun OutdatedVersionContent(modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.launcher_notification_compatibility))
        },
        leadingContent = {
            Icon(
                painterResource(R.drawable.icon_warning),
                contentDescription = null,
            )
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.launcher_notification_compatibility_description))
        },
        modifier = modifier.width(IntrinsicSize.Max)
    )
}

@Composable
fun UpdateFailedContent(modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.launcher_notification_update_failed))
        },
        leadingContent = {
            Icon(
                painterResource(R.drawable.icon_warning),
                contentDescription = null,
            )
        },
        modifier = modifier.width(IntrinsicSize.Max)
    )
}