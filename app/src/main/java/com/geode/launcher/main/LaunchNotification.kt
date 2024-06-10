package com.geode.launcher.main

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.SettingsActivity
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import kotlinx.coroutines.delay

fun onSettings(context: Context) {
    val launchIntent = Intent(context, SettingsActivity::class.java)
    context.startActivity(launchIntent)
}

@Composable
fun LaunchNotification() {
    val theme = Theme.DARK

    CompositionLocalProvider(LocalTheme provides theme) {
        GeodeLauncherTheme(theme = theme, blackBackground = true) {
            FadeOutContainer()
        }
    }
}

@Composable
fun FadeOutContainer() {
    val state = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    LaunchedEffect(true) {
        delay(3000L)
        state.targetState = false
    }

    AnimatedVisibility(
        visibleState = state,
        exit = slideOutHorizontally()
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            MainView()
        }
    }
}

@Composable
fun MainView() {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.background.copy(alpha = 0.75f)

    OutlinedCard(
        colors = CardDefaults.cardColors(
            containerColor = surfaceColor
        ),
        onClick = {
            onSettings(context)
        }
    ) {
        NotificationContent()
    }
}

@Composable
fun NotificationContent() {
    // surface is not in use, so this is unfortunately not provided
    val textColor = MaterialTheme.colorScheme.onSurface

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.geode_monochrome),
            colorFilter = ColorFilter.tint(textColor),
            contentDescription = null,
            modifier = Modifier.size(32.dp, 32.dp)
        )

        Text(
            text = stringResource(id = R.string.launcher_settings_notification_body),
            color = textColor,
            style = MaterialTheme.typography.titleLarge
        )
    }
}