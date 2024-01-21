package com.geode.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.api.ReleaseViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import com.geode.launcher.utils.ReleaseManager
import com.geode.launcher.utils.useCountdownTimer
import java.net.ConnectException
import java.net.UnknownHostException


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gdInstalled = LaunchUtils.isGeometryDashInstalled(packageManager)
        val geodeInstalled = LaunchUtils.isGeodeInstalled(this)

        val returnMessage = intent.extras?.getString(Constants.LAUNCHER_KEY_RETURN_MESSAGE)
        val returnTitle = intent.extras?.getString(Constants.LAUNCHER_KEY_RETURN_TITLE)

        setContent {
            val themeOption by PreferenceUtils.useIntPreference(PreferenceUtils.Key.THEME)
            val theme = Theme.fromInt(themeOption)

            val backgroundOption by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.BLACK_BACKGROUND)

            CompositionLocalProvider(LocalTheme provides theme) {
                GeodeLauncherTheme(theme = theme, blackBackground = backgroundOption) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val loadFailed = !returnMessage.isNullOrEmpty() && !returnTitle.isNullOrEmpty()
                        MainScreen(gdInstalled, geodeInstalled, loadFailed)

                        if (!returnMessage.isNullOrEmpty() && !returnTitle.isNullOrEmpty()) {
                            LoadFailedDialog(returnTitle, returnMessage)
                        }
                    }
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
fun UpdateProgressIndicator(
    message: String,
    releaseViewModel: ReleaseViewModel,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
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

        TextButton(
            onClick = {
                releaseViewModel.cancelUpdate()
            },
            modifier = Modifier.offset((-12).dp)
        ) {
            Text(stringResource(R.string.release_fetch_button_cancel))
        }
    }
}

@Composable
fun UpdateMessageIndicator(
    message: String,
    releaseViewModel: ReleaseViewModel,
    modifier: Modifier = Modifier,
    allowRetry: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.padding(4.dp))

        if (allowRetry) {
            OutlinedButton(
                onClick = {
                    releaseViewModel.runReleaseCheck(true)
                },
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.launcher_retry_icon_alt)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.release_fetch_button_retry))
            }
        }
    }

}

@Composable
fun LoadFailedDialog(returnTitle: String, returnMessage: String) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            icon = {
                Icon(
                    painterResource(R.drawable.icon_error),
                    contentDescription = stringResource(R.string.launcher_error_icon_alt)
                )
            },
            title = { Text(returnTitle) },
            text = { Text(returnMessage) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.message_box_accept))
                }
            },
            onDismissRequest = { showDialog = false }
        )
    }
}

@Composable
fun UpdateWarning() {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val gdVersionCode = remember { LaunchUtils.getGeometryDashVersionCode(packageManager) }
    val gdVersionString = remember { LaunchUtils.getGeometryDashVersionString(packageManager) }

    var showUpdateWarning by remember { mutableStateOf(true) }

    if (gdVersionCode != Constants.SUPPORTED_VERSION_CODE) {
        if (showUpdateWarning) {
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
                    Text(stringResource(
                        R.string.launcher_unsupported_version_description,
                        gdVersionString, Constants.SUPPORTED_VERSION_STRING
                    ))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUpdateWarning = false
                        onLaunch(context)
                    }) {
                        Text(stringResource(R.string.message_box_accept))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateWarning = false }) {
                        Text(stringResource(R.string.message_box_cancel))
                    }
                },
                onDismissRequest = { showUpdateWarning = false }
            )
        }
    } else {
        LaunchedEffect(gdVersionCode) {
            onLaunch(context)
        }
    }
}

@Composable
fun UpdateCard(releaseViewModel: ReleaseViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val releaseState by releaseViewModel.uiState.collectAsState()

    when (val state = releaseState) {
        is ReleaseViewModel.ReleaseUIState.Failure -> {
            val message = when (state.exception) {
                is UnknownHostException, is ConnectException ->
                    stringResource(R.string.release_fetch_no_internet)
                is ReleaseManager.UpdateException -> {
                    when (state.exception.reason) {
                        ReleaseManager.UpdateException.Reason.EXTERNAL_FILE_IN_USE -> stringResource(
                            R.string.release_fetch_manual_check_required
                        )
                        else -> state.exception.message
                    }
                }
                else -> state.exception.message
            }

            UpdateMessageIndicator(
                stringResource(R.string.release_fetch_failed, message ?: ""),
                modifier = modifier,
                allowRetry = true,
                releaseViewModel = releaseViewModel
            )
        }
        is ReleaseViewModel.ReleaseUIState.InDownload -> {
            val progress = state.downloaded / state.outOf.toDouble()

            val downloaded = remember(state.downloaded) {
                formatShortFileSize(context, state.downloaded)
            }

            val outOf = remember(state.outOf) {
                formatShortFileSize(context, state.outOf)
            }

            UpdateProgressIndicator(
                stringResource(
                    R.string.release_fetch_downloading,
                    downloaded,
                    outOf
                ),
                modifier = modifier,
                releaseViewModel = releaseViewModel,
                progress = progress.toFloat()
            )
        }
        is ReleaseViewModel.ReleaseUIState.InUpdateCheck -> {
            UpdateProgressIndicator(
                stringResource(R.string.release_fetch_in_progress),
                modifier = modifier,
                releaseViewModel = releaseViewModel
            )
        }
        is ReleaseViewModel.ReleaseUIState.Finished -> {
            if (state.hasUpdated) {
                UpdateMessageIndicator(
                    stringResource(R.string.release_fetch_success),
                    modifier = modifier,
                    releaseViewModel = releaseViewModel
                )
            }
        }
        is ReleaseViewModel.ReleaseUIState.Cancelled -> {
            if (state.isCancelling) {
                UpdateProgressIndicator(
                    stringResource(R.string.release_fetch_cancelling),
                    modifier = modifier,
                    releaseViewModel = releaseViewModel
                )
            } else {
                UpdateMessageIndicator(
                    stringResource(R.string.release_fetch_cancelled),
                    modifier = modifier,
                    allowRetry = true,
                    releaseViewModel = releaseViewModel
                )
            }
        }
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
    geodePreinstalled: Boolean = true,
    disableAutomaticLaunch: Boolean = false,
    releaseViewModel: ReleaseViewModel = viewModel(factory = ReleaseViewModel.Factory)
) {
    val context = LocalContext.current

    val shouldAutomaticallyLaunch = PreferenceUtils.useBooleanPreference(
        preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY
    )

    val shouldUpdate by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.UPDATE_AUTOMATICALLY)

    val autoUpdateState by releaseViewModel.uiState.collectAsState()

    val geodeJustInstalled = (autoUpdateState as? ReleaseViewModel.ReleaseUIState.Finished)
        ?.hasUpdated ?: false
    val geodeInstalled = geodePreinstalled || geodeJustInstalled

    var beginLaunch by remember { mutableStateOf(false) }

    LaunchedEffect(shouldUpdate) {
        if (shouldUpdate && !releaseViewModel.hasPerformedCheck) {
            releaseViewModel.runReleaseCheck()
        } else {
            releaseViewModel.useGlobalCheckState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
            if (shouldAutomaticallyLaunch.value && !releaseViewModel.isInUpdate && !disableAutomaticLaunch) {
                val countdownTimer = useCountdownTimer(
                    time = 3000,
                    onCountdownFinish = {
                        // just in case this changed async
                        if (shouldAutomaticallyLaunch.value) {
                            beginLaunch = true
                        }
                    }
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
                Button(
                    onClick = { beginLaunch = true },
                    enabled = !releaseViewModel.isInUpdate
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
        } else if (gdInstalled) {
            Text(
                context.getString(R.string.geode_download_title),
                modifier = Modifier.padding(12.dp)
            )

            Row {
                Button(
                    onClick = { releaseViewModel.runReleaseCheck(true) },
                    enabled = !releaseViewModel.isInUpdate
                ) {
                    Icon(
                        painterResource(R.drawable.icon_download),
                        contentDescription = context.getString(R.string.launcher_download_icon_alt)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(context.getString(R.string.launcher_download))
                }
                Spacer(Modifier.size(2.dp))
                IconButton(onClick = { onSettings(context) }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = context.getString(R.string.launcher_settings_icon_alt)
                    )
                }
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
            releaseViewModel,
            modifier = Modifier.padding(12.dp)
        )
    }

    if (beginLaunch) {
        UpdateWarning()
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
    GeodeLauncherTheme(theme = Theme.DARK) {
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