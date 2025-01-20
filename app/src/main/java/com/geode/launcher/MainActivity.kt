package com.geode.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.main.*
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import com.geode.launcher.utils.GamePackageUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        clearDownloadedApks(this)

        super.onCreate(savedInstanceState)

        val gdInstalled = GamePackageUtils.isGameInstalled(packageManager)
        val geodeInstalled = LaunchUtils.isGeodeInstalled(this)

        val returnMessage = intent.extras?.getString(LaunchUtils.LAUNCHER_KEY_RETURN_MESSAGE)
        val returnExtendedMessage = intent.extras?.getString(LaunchUtils.LAUNCHER_KEY_RETURN_EXTENDED_MESSAGE)

        @Suppress("DEPRECATION") // the new api is android 13 only (why)
        val returnError = intent.extras?.getSerializable(LaunchUtils.LAUNCHER_KEY_RETURN_ERROR)
                as? LaunchUtils.LauncherError

        val loadFailureInfo = if (returnError != null && returnMessage != null) {
            LoadFailureInfo(returnError, returnMessage, returnExtendedMessage)
        } else if (LaunchUtils.lastSessionCrashed(this)) {
            LoadFailureInfo(LaunchUtils.LauncherError.CRASHED)
        } else { null }

        val redirectToAlt = PreferenceUtils.get(this).getBoolean(PreferenceUtils.Key.ENABLE_REDESIGN)
        if (redirectToAlt) {
            val launchIntent = Intent(this, AltMainActivity::class.java)
            launchIntent.putExtras(intent)
            startActivity(launchIntent)

            return
        }

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
                        MainScreen(gdInstalled, geodeInstalled, loadFailureInfo)
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
fun MainScreen(
    gdInstalled: Boolean = true,
    geodePreinstalled: Boolean = true,
    loadFailureInfo: LoadFailureInfo? = null,
    releaseViewModel: ReleaseViewModel = viewModel(factory = ReleaseViewModel.Factory)
) {
    val context = LocalContext.current

    val shouldUpdate by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.UPDATE_AUTOMATICALLY)
    val autoUpdateState by releaseViewModel.uiState.collectAsState()

    val geodeJustInstalled = (autoUpdateState as? ReleaseViewModel.ReleaseUIState.Finished)
        ?.hasUpdated == true
    val geodeInstalled = geodePreinstalled || geodeJustInstalled

    var beginLaunch by remember { mutableStateOf(false) }
    var launchInSafeMode by remember { mutableStateOf(false) }

    LaunchedEffect(shouldUpdate) {
        if (shouldUpdate && !releaseViewModel.hasPerformedCheck && gdInstalled) {
            releaseViewModel.runReleaseCheck()
        } else {
            releaseViewModel.useGlobalCheckState()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // automatically show load failed dialog (but not crash)
    var showErrorInfo by remember { mutableStateOf(
        loadFailureInfo != null && loadFailureInfo.title != LaunchUtils.LauncherError.CRASHED
    )}

    val hasError = loadFailureInfo != null
    LaunchedEffect(hasError, showErrorInfo) {
        if (!showErrorInfo && loadFailureInfo != null) {
            showFailureSnackbar(context, loadFailureInfo, snackbarHostState) {
                showErrorInfo = true
            }
        }
    }

    if (showErrorInfo && loadFailureInfo != null) {
        ErrorInfoSheet(loadFailureInfo, onDismiss = { showErrorInfo = false })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.geode_logo),
                contentDescription = context.getString(R.string.launcher_logo_alt),
                modifier = Modifier.size(100.dp, 100.dp)
            )
            Text(
                context.getString(R.string.launcher_title),
                fontSize = 32.sp,
                modifier = Modifier.padding(12.dp)
            )

            when {
                gdInstalled && geodeInstalled -> {
                    val gdVersion = remember {
                        GamePackageUtils.getGameVersionCode(context.packageManager)
                    }

                    if (gdVersion < Constants.SUPPORTED_VERSION_CODE_MIN) {
                        val versionName = remember {
                            GamePackageUtils.getUnifiedVersionName(context.packageManager)
                        }

                        LaunchBlockedLabel(stringResource(R.string.game_outdated, versionName))
                    } else {
                        val stopLaunch = releaseViewModel.isInUpdate || hasError
                        PlayButton(
                            stopAutomaticLaunch = stopLaunch,
                            blockLaunch = releaseViewModel.isInUpdate,
                            onPlayGame = { safeMode ->
                                launchInSafeMode = safeMode
                                beginLaunch = true
                            },
                        )

                        if (gdVersion < Constants.SUPPORTED_VERSION_CODE_MIN_WARNING) {
                            UnsupportedVersionWarning()
                        }
                    }
                }
                gdInstalled -> {
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
                }
                else -> LaunchBlockedLabel(stringResource(R.string.game_not_found))
            }

            UpdateCard(
                releaseViewModel,
                modifier = Modifier
                    .padding(12.dp)
            )
        }
    }

    if (beginLaunch) {
        UpdateWarning(launchInSafeMode) {
            beginLaunch = false
            launchInSafeMode = false
        }
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