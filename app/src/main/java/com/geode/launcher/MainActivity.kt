package com.geode.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.api.ReleaseViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import com.geode.launcher.utils.useCountdownTimer


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gdInstalled = LaunchUtils.isGeometryDashInstalled(packageManager)
        val geodeInstalled = LaunchUtils.isGeodeInstalled(this)

        setContent {
            GeodeLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(gdInstalled, geodeInstalled)
                }
            }
        }
        if (gdInstalled && geodeInstalled) {
            intent.getBooleanExtra("restarted", false).let {
                if (it) {
                    onLaunch(this)
                }
            }
        }
    }
}

@Composable
fun UpdateProgressIndicator(modifier: Modifier = Modifier, message: String, progress: Float? = null) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
        modifier = modifier
    ) {
        Text(message)

        Spacer(modifier = Modifier.padding(4.dp))

        if (progress == null) {
            LinearProgressIndicator()
        } else {
            LinearProgressIndicator(progress)
        }
    }
}

@Composable
fun UpdateCard(state: ReleaseViewModel.ReleaseUIState, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    when (state) {
        is ReleaseViewModel.ReleaseUIState.Failure -> {
            val message = state.exception.message
            val messageBody = context.getString(R.string.release_fetch_failed, message)

            Text(messageBody)
        }
        is ReleaseViewModel.ReleaseUIState.InDownload -> {
            val progress = state.downloaded / state.outOf.toDouble()

            val downloaded = formatShortFileSize(context, state.downloaded)
            val outOf = formatShortFileSize(context, state.outOf)

            UpdateProgressIndicator(
                modifier = modifier,
                message = context.getString(
                    R.string.release_fetch_downloading,
                    downloaded,
                    outOf
                ),
                progress = progress.toFloat()
            )
        }
        is ReleaseViewModel.ReleaseUIState.InUpdateCheck -> {
            UpdateProgressIndicator(
                modifier = modifier,
                message = context.getString(R.string.release_fetch_in_progress)
            )
        }
        else -> {}
    }
}

fun onLaunch(context: Context) {
    val launchIntent = Intent(context, GeometryDashActivity::class.java)
    launchIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

    context.startActivity(launchIntent)
}

fun onSettings(context: Context) {
    val launchIntent = Intent(context, SettingsActivity::class.java)
    context.startActivity(launchIntent)
}

@Composable
fun MainScreen(
    gdInstalled: Boolean = true,
    geodeInstalled: Boolean = true,
    releaseViewModel: ReleaseViewModel = viewModel(factory = ReleaseViewModel.Factory)
) {
    val context = LocalContext.current

    val shouldAutomaticallyLaunch = PreferenceUtils.useBooleanPreference(
        preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY
    )

    val autoUpdateState by releaseViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.geode_logo),
            contentDescription = context.getString(R.string.launcher_logo_alt),
            modifier = Modifier.size(136.dp, 136.dp)
        )
        Text(
            context.getString(R.string.launcher_title),
            fontSize = 32.sp,
            modifier = Modifier.padding(12.dp)
        )

        if (gdInstalled && geodeInstalled) {
            if (shouldAutomaticallyLaunch.value) {
                val countdownTimer = useCountdownTimer(
                    time = 3000,
                    onCountdownFinish = {
                        // just in case this changed async
                        if (shouldAutomaticallyLaunch.value) {
                            onLaunch(context)
                        }
                    }
                )

                if (countdownTimer.value != 0) {
                    Text(
                        context.resources.getQuantityString(
                            R.plurals.automatically_load_countdown, countdownTimer.value, countdownTimer.value
                        ),
                        style = Typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.size(12.dp))
                }
            }

            Row {
                Button(onClick = { onLaunch(context) }) {
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
        } else if (gdInstalled && !geodeInstalled) {
            Text(
                context.getString(R.string.geode_download_title),
                modifier = Modifier.padding(12.dp)
            )
            OutlinedButton(onClick = { onSettings(context) }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = context.getString(R.string.launcher_settings_icon_alt)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(context.getString(R.string.launcher_settings))
            }
        } else {
            Text(
                context.getString(R.string.game_not_found),
                modifier = Modifier.padding(12.dp)
            )
            OutlinedButton(onClick = { onSettings(context) }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = context.getString(R.string.launcher_settings_icon_alt)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(context.getString(R.string.launcher_settings))
            }
        }

        UpdateCard(
            autoUpdateState,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun MainScreenLightPreview() {
    GeodeLauncherTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainScreen()
        }
    }
}


@Preview(showSystemUi = true)
@Composable
fun MainScreenDarkPreview() {
    GeodeLauncherTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainScreen()
        }
    }
}

@Preview
@Composable
fun MainScreenNoGeometryDashPreview() {
    GeodeLauncherTheme {
        MainScreen(gdInstalled = false)
    }
}