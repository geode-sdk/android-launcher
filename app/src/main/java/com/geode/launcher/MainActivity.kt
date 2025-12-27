package com.geode.launcher

import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.main.*
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Theme.DARK
import com.geode.launcher.ui.theme.Theme.LIGHT
import com.geode.launcher.ui.theme.launcherTitleStyle
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gdInstalled = GamePackageUtils.isGameInstalled(packageManager)
        val geodeInstalled = LaunchUtils.isGeodeInstalled(this)

        if (gdInstalled && geodeInstalled && intent.getBooleanExtra("restarted", false)) {
            onLaunch(this)
        }

        enableEdgeToEdge()
        clearDownloadedApks(this)

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

        var launchArguments: LaunchArguments? = null
        val intentData = intent.data
        if (intent.action == Intent.ACTION_VIEW && intentData != null) {
            val isLaunch = intentData.path == "/launch"
            val safeMode = if (isLaunch) {
                intentData.getBooleanQueryParameter("safe-mode", false)
            } else false

            launchArguments = LaunchArguments(
                autoSafeMode = safeMode,
                forcePause = !isLaunch,
                forceLaunch = isLaunch,
            )
        }

        val redirectToAlt = PreferenceUtils.get(this).getBoolean(PreferenceUtils.Key.DISABLE_REDESIGN)
        if (redirectToAlt && launchArguments == null) {
            val launchIntent = Intent(this, LegacyMainActivity::class.java)
            launchIntent.putExtras(intent)
            startActivity(launchIntent)

            return
        }

        setContent {
            val themeOption by PreferenceUtils.useIntPreference(PreferenceUtils.Key.THEME)
            val theme = Theme.fromInt(themeOption)

            val backgroundOption by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.BLACK_BACKGROUND)
            val dynamicColorOption by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.DISABLE_USER_THEME)

            val launchViewModel = viewModel<LaunchViewModel>(factory = LaunchViewModel.Factory)

            if (loadFailureInfo != null) {
                launchViewModel.loadFailure = loadFailureInfo
            }

            if (launchArguments != null) {
                launchViewModel.launchArguments = launchArguments
            }

            CompositionLocalProvider(LocalTheme provides theme) {
                GeodeLauncherTheme(theme = theme, blackBackground = backgroundOption, dynamicColor = !dynamicColorOption) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AltMainScreen(launchViewModel)
                    }
                }
            }
        }
    }
}

data class LaunchStatusInfo(
    val title: String?,
    val details: String? = null,
    val progress: (() -> Float)? = null
)

@Composable
fun LaunchCancelledIcon(cancelReason: LaunchViewModel.LaunchCancelReason, modifier: Modifier = Modifier) {
    when (cancelReason) {
        LaunchViewModel.LaunchCancelReason.GAME_OUTDATED,
        LaunchViewModel.LaunchCancelReason.GAME_MISSING -> Icon(painterResource(R.drawable.icon_error), contentDescription = null, modifier)
        LaunchViewModel.LaunchCancelReason.LAST_LAUNCH_CRASHED,
        LaunchViewModel.LaunchCancelReason.GEODE_NOT_FOUND -> Icon(painterResource(R.drawable.icon_warning), contentDescription = null, modifier)
        else -> Icon(painterResource(R.drawable.icon_info), contentDescription = null, modifier)
    }
}

@Composable
fun mapCancelReasonToInfo(cancelReason: LaunchViewModel.LaunchCancelReason): LaunchStatusInfo {
    return when (cancelReason) {
        LaunchViewModel.LaunchCancelReason.LAST_LAUNCH_CRASHED -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_crash)
        )
        LaunchViewModel.LaunchCancelReason.GEODE_NOT_FOUND -> LaunchStatusInfo(
            title = stringResource(R.string.geode_download_title)
        )
        LaunchViewModel.LaunchCancelReason.GAME_MISSING -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_error),
            details = stringResource(R.string.game_not_found)
        )
        LaunchViewModel.LaunchCancelReason.GAME_OUTDATED -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_error),
            details = stringResource(R.string.launcher_cancelled_outdated)
        )
        LaunchViewModel.LaunchCancelReason.AUTOMATIC,
        LaunchViewModel.LaunchCancelReason.MANUAL-> LaunchStatusInfo(
            title = null
        )
        /*
        else -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_manual)
        )
        */
    }
}

@Composable
fun mapLaunchStatusToInfo(state: LaunchViewModel.LaunchUIState, inSafeMode: Boolean = false): LaunchStatusInfo {
    return when (state) {
        is LaunchViewModel.LaunchUIState.Initial,
        is LaunchViewModel.LaunchUIState.Working,
        is LaunchViewModel.LaunchUIState.Ready -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_starting_game),
            details = if (inSafeMode) stringResource(R.string.launcher_in_safe_mode) else null
        )
        is LaunchViewModel.LaunchUIState.UpdateCheck -> LaunchStatusInfo(
            title = stringResource(R.string.release_fetch_in_progress)
        )
        is LaunchViewModel.LaunchUIState.Updating -> {
            val context = LocalContext.current

            val downloaded = remember(state.downloaded) {
                Formatter.formatShortFileSize(context, state.downloaded)
            }

            val outOf = remember(state.outOf) {
                state.outOf?.run {
                    Formatter.formatShortFileSize(context, this)
                }
            }

            LaunchStatusInfo(
                title = stringResource(R.string.launcher_downloading_update),
                details = stringResource(R.string.launcher_downloading_update_details, downloaded, outOf ?: "…"),
                progress = {
                    val progress = state.downloaded / state.outOf!!.toDouble()
                    progress.toFloat()
                }.takeIf { state.outOf != null }
            )
        }
        is LaunchViewModel.LaunchUIState.Cancelled -> {
            return mapCancelReasonToInfo(state.reason)
        }
    }
}

@Composable
fun LaunchCancelledBody(statusInfo: LaunchStatusInfo, icon: @Composable () -> Unit, inProgress: Boolean, modifier: Modifier = Modifier) {
    if (statusInfo.title == null && statusInfo.details == null && !inProgress) {
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(300.dp)
    ) {
        if (statusInfo.title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                    icon()
                    Spacer(Modifier.size(8.dp))
                    Text(statusInfo.title)
                }
            }
        }

        if (inProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (statusInfo.details != null) {
            Text(
                statusInfo.details,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }

}

@Composable
fun LaunchProgressBody(statusInfo: LaunchStatusInfo, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
        modifier = modifier.width(300.dp)
    ) {
        if (statusInfo.title != null) {
            Text(statusInfo.title, style = MaterialTheme.typography.bodyLarge)
        }

        if (statusInfo.progress != null) {
            LinearProgressIndicator(
                statusInfo.progress,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (statusInfo.details != null) {
            Text(statusInfo.details, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RetryButtonContents(reason: LaunchViewModel.LaunchCancelReason) {
    when (reason) {
        LaunchViewModel.LaunchCancelReason.LAST_LAUNCH_CRASHED -> {
            Icon(
                painterResource(R.drawable.icon_resume),
                contentDescription = null
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.launcher_cancelled_resume))
        }
        LaunchViewModel.LaunchCancelReason.MANUAL,
        LaunchViewModel.LaunchCancelReason.AUTOMATIC -> {
            Icon(
                painterResource(R.drawable.icon_play_arrow),
                contentDescription = null
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.launcher_launch))
        }
        LaunchViewModel.LaunchCancelReason.GEODE_NOT_FOUND -> {
            Icon(
                painterResource(R.drawable.icon_refresh),
                contentDescription = null
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.launcher_cancelled_retry))
        }
        // these shouldn't be allowing retries anyways
        LaunchViewModel.LaunchCancelReason.GAME_MISSING,
        LaunchViewModel.LaunchCancelReason.GAME_OUTDATED -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchProgressCard(
    uiState: LaunchViewModel.LaunchUIState,
    crashInfo: LoadFailureInfo?,
    onCancel: () -> Unit, 
    onResume: (safeMode: Boolean) -> Unit,
    onMore: () -> Unit,
    extraOptions: @Composable () -> Unit,
    safeModeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showSafeModeDialog by remember { mutableStateOf(false) }
    val status = mapLaunchStatusToInfo(uiState, safeModeEnabled)

    Box(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState is LaunchViewModel.LaunchUIState.Cancelled) {
                LaunchCancelledBody(
                    statusInfo = status,
                    icon = { LaunchCancelledIcon(uiState.reason, Modifier.size(32.dp)) },
                    inProgress = uiState.inProgress
                )

                if (uiState.reason.isGameInstallIssue()) {
                    val context = LocalContext.current
                    val showDownload = remember { GamePackageUtils.showDownloadBadge(context.packageManager) }

                    if (showDownload) {
                        GooglePlayBadge(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }

                if (uiState.reason.allowsRetry()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterHorizontally),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        LongPressButton(onClick = { onResume(false) }, onLongPress = {
                            showSafeModeDialog = true
                        }) {
                            RetryButtonContents(uiState.reason)
                        }

                        if (crashInfo != null) {
                            FilledTonalButton(onClick = onMore) {
                                Icon(
                                    painterResource(R.drawable.icon_question_mark),
                                    contentDescription = null
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.launcher_cancelled_help_alt))
                            }
                        }

                        extraOptions()
                    }
                }
            } else {
                LaunchProgressBody(statusInfo = status, modifier = Modifier.padding(top = 16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(300.dp)) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.release_fetch_button_cancel))
                    }

                    Spacer(Modifier.weight(1.0f))

                    extraOptions()
                }
            }
        }
    }

    if (showSafeModeDialog) {
        SafeModeDialog(
            onDismiss = {
                showSafeModeDialog = false
            },
            onLaunch = {
                onResume(true)
                showSafeModeDialog = false
            }
        )
    }
}

@Composable
fun ExtraOptions(onSettings: () -> Unit) {
    IconButton(
        onClick = onSettings,
    ) {
        Icon(
            painterResource(R.drawable.icon_settings),
            contentDescription = stringResource(R.string.launcher_settings_icon_alt)
        )
    }
}

val lightOuterColorGradient = arrayOf(
    0.0f to Color(0xfff5b11d),
    0.10f to Color(0xfff0834d),
    0.24f to Color(0xffe85f6b),
    0.49f to Color(0xffc9659e),
    1.0f to Color(0xffb9588f)
)

val lightInnerColorGradient = arrayOf(
    0.48f to Color(0xfff2ce00),
    0.63f to Color(0xfff4b97e),
    0.73f to Color(0xfff7b66a),
    1.0f to Color(0xffeb8fac)
)

val darkOuterColorGradient = arrayOf(
    0.0f to Color(0xfffffdff),
    0.08f to Color(0xffF2EAF5),
    0.12f to Color(0xffEDE5EF),
    0.31f to Color(0xffCDB5CD),
    0.49f to Color(0xffBA9BBC),
    1.0f to Color(0xff8D7ACF),
)

val darkInnerColorGradient = arrayOf(
    0.48f to Color(0xffffffff),
    0.63f to Color(0xfff5e4c2),
    0.73f to Color(0xffe5c7ad),
    1.0f to Color(0xffb790a9),
)

@Preview(name = "non-animated, light")
@Preview(name = "non-animated, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GeodeLogoPreview() {
    val theme = if (isSystemInDarkTheme()) DARK else LIGHT
    CompositionLocalProvider(LocalTheme provides theme) {
        GeodeLauncherTheme(theme = theme) {
            Surface {
                GeodeLogo()
            }
        }
    }
}

@Preview(name = "animated, light")
@Preview(name = "animated, dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GeodeLogoAnimatedPreview() {
    val theme = if (isSystemInDarkTheme()) DARK else LIGHT
    CompositionLocalProvider(LocalTheme provides theme) {
        GeodeLauncherTheme(theme = theme) {
            Surface {
                GeodeLogo(true)
            }
        }
    }
}

@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    val theme = LocalTheme.current
    val innerColorPalette = remember(theme) {
        if (theme == LIGHT) lightInnerColorGradient else darkInnerColorGradient
    }

    val outerColorPalette = remember(theme) {
        if (theme == LIGHT) lightOuterColorGradient else darkOuterColorGradient
    }

    val infiniteTransition = rememberInfiniteTransition(label = "infinite transition")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient offset"
    )

    val innerBrush = Brush.linearGradient(
        colorStops = innerColorPalette,
        start = Offset(0.0f, 0.0f + (165.06f * offset)),
        end = Offset(0.0f, 165.06f + (165.06f * offset)),
        tileMode = TileMode.Mirror
    )

    val outerBrush = Brush.linearGradient(
        colorStops = outerColorPalette,
        start = Offset(0.0f + (84.69f * offset), 0.0f + (165.06f * offset)),
        end = Offset(84.69f + (84.69f * offset), 169.61f + (165.06f * offset)),
        tileMode = TileMode.Mirror
    )

    Box(modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.geode_monochrome_inner),
            contentDescription = null,
            modifier = Modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = innerBrush,
                        blendMode = BlendMode.SrcIn
                    )
                }
        )
        Icon(
            painter = painterResource(id = R.drawable.geode_monochrome_outer),
            contentDescription = null,
            modifier = Modifier
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = outerBrush,
                        blendMode = BlendMode.SrcIn
                    )
                }
        )
    }
}

@Composable
fun GeodeLogo(shouldAnimate: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val theme = LocalTheme.current

        Crossfade(
            targetState = shouldAnimate,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            label="logo fade"
        ) { screen ->
            when (screen) {
                true -> AnimatedLogo(modifier = Modifier.size(64.dp, 64.dp))
                false -> Image(
                    painterResource(if (theme == LIGHT)
                        R.drawable.geode_base_light else R.drawable.geode_base
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp, 64.dp)
                )
            }
        }

        Text(
            stringResource(R.string.launcher_title),
            style = launcherTitleStyle,
            fontSize = 64.sp,
            modifier = Modifier
                .padding(12.dp)
        )
    }
}

@Composable
fun GeodeUpdateIndicator(snackbarHostState: SnackbarHostState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val releaseState = ReleaseManager.get(context).uiState.collectAsState().value

    if (releaseState !is ReleaseManager.ReleaseManagerState.Failure) {
        return
    }

    val message = when (releaseState.exception) {
        is UnknownHostException, is ConnectException ->
            stringResource(R.string.release_fetch_no_internet_short)
        is ReleaseManager.UpdateException -> {
            when (releaseState.exception.reason) {
                ReleaseManager.UpdateException.Reason.EXTERNAL_FILE_IN_USE -> stringResource(
                    R.string.release_fetch_external_in_use_short
                )
                else -> stringResource(R.string.release_fetch_generic_short)
            }
        }
        is CancellationException -> null
        else -> stringResource(R.string.release_fetch_generic_short)
    }

    if (message == null) {
        return
    }

    LaunchedEffect(message) {
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = resources.getString(R.string.release_fetch_button_retry),
            duration = SnackbarDuration.Indefinite,
            withDismissAction = true
        )

        when (result) {
            SnackbarResult.ActionPerformed -> {
                onRetry()
            }
            else -> {}
        }
    }
}

@Composable
fun AltMainScreen(
    launchViewModel: LaunchViewModel = viewModel(factory = LaunchViewModel.Factory)
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        launchViewModel.beginLaunchFlow()
    }

    val launchUIState by launchViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var launchInSafeMode by remember {
        mutableStateOf(launchViewModel.launchArguments?.autoSafeMode == true)
    }

    var showErrorInfo by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.safeDrawingPadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                GeodeLogo(shouldAnimate = launchUIState.isInProgress())

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LaunchProgressCard(
                        launchUIState,
                        crashInfo = launchViewModel.currentCrashInfo(),
                        onCancel = {
                            launchInSafeMode = false
                            coroutineScope.launch {
                                launchViewModel.cancelLaunch()
                            }
                        },
                        onResume = { safeMode ->
                            launchInSafeMode = safeMode

                            launchViewModel.clearCrashInfo()
                            coroutineScope.launch {
                                launchViewModel.beginLaunchFlow(true)
                            }
                        },
                        onMore = {
                            showErrorInfo = true
                        },
                        extraOptions = {
                            ExtraOptions(
                                onSettings = {
                                    coroutineScope.launch {
                                        launchViewModel.cancelLaunch(true)
                                    }

                                    onSettings(context)
                                }
                            )
                        },
                        safeModeEnabled = launchInSafeMode
                    )

                    // only show launcher update in a case where a user won't see it ingame
                    if (launchUIState is LaunchViewModel.LaunchUIState.Cancelled) {
                        val gameVersion = remember { GamePackageUtils.getGameVersionCodeOrNull(context.packageManager) }

                        if (gameVersion != null && gameVersion < Constants.SUPPORTED_VERSION_CODE_MIN_WARNING) {
                            UnsupportedVersionWarning()
                        }

                        val nextLauncherUpdate by launchViewModel.nextLauncherUpdate.collectAsState()
                        if (nextLauncherUpdate != null) {
                            LauncherUpdateIndicator()
                        }

                        GeodeUpdateIndicator(snackbarHostState) {
                            launchViewModel.retryUpdate()
                        }
                    }
                }
            }
        }
    }

    if (launchUIState is LaunchViewModel.LaunchUIState.Ready) {
        UpdateWarning(launchInSafeMode) {
            launchInSafeMode = false
            coroutineScope.launch {
                launchViewModel.cancelLaunch()
            }
        }
    }

    val loadFailure = launchViewModel.currentCrashInfo()
    if (showErrorInfo && loadFailure != null) {
        ErrorInfoSheet(loadFailure, onDismiss = { showErrorInfo = false })
    }
}