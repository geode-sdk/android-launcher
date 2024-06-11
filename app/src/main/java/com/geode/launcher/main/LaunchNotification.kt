package com.geode.launcher.main

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.SettingsActivity
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import kotlinx.coroutines.delay

fun onNotificationSettings(context: Context) {
    val launchIntent = Intent(context, SettingsActivity::class.java)
    context.startActivity(launchIntent)
}

@Composable
fun LaunchNotification() {
    val context = LocalContext.current
    val theme = Theme.DARK

    CompositionLocalProvider(LocalTheme provides theme) {
        GeodeLauncherTheme(theme = theme, blackBackground = true) {
            // surface is not in use, so this is unfortunately not provided
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                // using manual placements as column layouts seem to mess up the animation
                AnimatedNotificationCard(
                    onClick = { onNotificationSettings(context) }
                ) {
                    NotificationContent()
                }
            }
        }
    }
}

@Composable
fun AnimatedNotificationCard(visibilityDelay: Long = 0L, offset: Dp = 0.dp, onClick: (() -> Unit)? = null, contents: @Composable () -> Unit) {
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
        enter = slideInHorizontally(),
        exit = slideOutHorizontally()
    ) {
        Box(modifier = Modifier
                .padding(8.dp)
                .offset(y = offset)
        ) {
            CardView(onClick) {
                contents()
            }
        }
    }
}

@Composable
fun CardView(onClick: (() -> Unit)?, contents: @Composable () -> Unit) {
    val surfaceColor = MaterialTheme.colorScheme.background.copy(alpha = 0.75f)

    if (onClick != null) {
        OutlinedCard(
            colors = CardDefaults.cardColors(
                containerColor = surfaceColor
            ),
            onClick = onClick
        ) {
            contents()
        }
    } else {
        OutlinedCard(
            colors = CardDefaults.cardColors(
                containerColor = surfaceColor
            )
        ) {
            contents()
        }
    }
}

@Composable
fun LauncherUpdateContent(modifier: Modifier = Modifier, openTo: String, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        // buttons add enough padding already, lower to compensate
        modifier = modifier.padding(
            top = 20.dp,
            start = 12.dp,
            end = 12.dp,
            bottom = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.launcher_update_available),
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Row(modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.launcher_update_dismiss))
            }

            Spacer(Modifier.size(4.dp))

            TextButton(onClick = { uriHandler.openUri(openTo) }) {
                Text(stringResource(R.string.launcher_download))
            }
        }
    }
}

@Composable
fun NotificationContent() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.geode_monochrome),
            contentDescription = null,
            modifier = Modifier.size(32.dp, 32.dp)
        )

        Text(
            text = stringResource(id = R.string.launcher_notification_settings),
            style = MaterialTheme.typography.titleLarge
        )
    }
}