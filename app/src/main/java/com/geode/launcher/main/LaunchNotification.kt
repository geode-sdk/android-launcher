package com.geode.launcher.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.delay

enum class LaunchNotificationType {
    GEODE_UPDATED, LAUNCHER_UPDATE_AVAILABLE, UPDATE_FAILED;
}

fun determineDisplayedCards(context: Context): List<LaunchNotificationType> {
    val cards = mutableListOf<LaunchNotificationType>()

    val releaseManager = ReleaseManager.get(context)

    val availableUpdate = releaseManager.availableLauncherUpdate.value
    if (availableUpdate != null) {
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
    val context = LocalContext.current

    when (type) {
        LaunchNotificationType.LAUNCHER_UPDATE_AVAILABLE -> {
            AnimatedNotificationCard(
                onClick = {
                    val nextUpdate = ReleaseManager.get(context).availableLauncherUpdate.value
                    val launcherUrl = nextUpdate?.getLauncherDownload()?.browserDownloadUrl
                    if (launcherUrl != null) {
                        downloadUrl(context, launcherUrl)
                    }
                }
            ) {
                LauncherUpdateContent()
            }
        }
        LaunchNotificationType.GEODE_UPDATED -> {
            AnimatedNotificationCard {
                UpdateNotificationContent()
            }
        }
        LaunchNotificationType.UPDATE_FAILED -> {
            AnimatedNotificationCard {
                UpdateFailedContent()
            }
        }
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
                // using manual placements as column layouts seem to mess up the animation
                Column(
                    modifier = Modifier.displayCutoutPadding()
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
fun AnimatedNotificationCard(visibilityDelay: Long = 0L, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, contents: @Composable () -> Unit) {
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

        delay(3000L)
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(16.dp)
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(32.dp, 32.dp)
        )

        Column {
            Text(
                text = stringResource(id = R.string.launcher_update_available),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(id = R.string.launcher_notification_update_cta),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun UpdateNotificationContent(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(16.dp)
    ) {
        Icon(
            painterResource(R.drawable.geode_monochrome),
            contentDescription = null,
            modifier = Modifier.size(32.dp, 32.dp)
        )

        Text(
            text = stringResource(id = R.string.launcher_notification_update_success),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun UpdateFailedContent(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(16.dp)
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(32.dp, 32.dp)
        )

        Text(
            text = stringResource(id = R.string.launcher_notification_update_failed),
            style = MaterialTheme.typography.titleLarge
        )
    }
}