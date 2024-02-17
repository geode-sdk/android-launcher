package com.geode.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.Formatter.formatShortFileSize
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.utils.GeodeUtils
import com.geode.launcher.utils.useCountdownTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.net.ConnectException
import java.net.UnknownHostException

data class LoadFailureInfo(
    val title: LaunchUtils.LauncherError,
    val description: String? = null,
    val extendedMessage: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        val gdInstalled = LaunchUtils.isGeometryDashInstalled(packageManager)
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
fun LauncherUpdateIndicator(modifier: Modifier = Modifier, openTo: String, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    ElevatedCard(modifier) {
        Column(
            // buttons add enough padding already, lower to compensate
            modifier = Modifier.padding(
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
}

@Composable
fun UpdateProgressIndicator(
    message: String,
    releaseViewModel: ReleaseViewModel,
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null
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
            LinearProgressIndicator(progress = progress)
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
fun UpdateWarning(inSafeMode: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val gdVersionCode = remember { LaunchUtils.getGeometryDashVersionCode(packageManager) }
    val gdVersionString = remember { LaunchUtils.getGeometryDashVersionString(packageManager) }

    var lastDismissedVersion by PreferenceUtils.useLongPreference(
        preferenceKey = PreferenceUtils.Key.DISMISSED_GJ_UPDATE
    )

    val canDismissRelease = gdVersionCode >= Constants.SUPPORTED_VERSION_CODE
    val shouldDismiss = canDismissRelease && gdVersionCode == lastDismissedVersion

    if (gdVersionCode != Constants.SUPPORTED_VERSION_CODE && !shouldDismiss) {
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
                val message = if (gdVersionCode >= Constants.SUPPORTED_VERSION_CODE) {
                    R.string.launcher_unsupported_version_description
                } else {
                    R.string.launcher_outdated_version_description
                }

                Text(stringResource(
                    message, gdVersionString, Constants.SUPPORTED_VERSION_STRING
                ))
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
                progress = {
                    val progress = state.downloaded / state.outOf.toDouble()
                    progress.toFloat()
                }
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

    val nextUpdate by releaseViewModel.nextLauncherUpdate.collectAsState()
    val nextUpdateValue = nextUpdate

    if (!releaseViewModel.isInUpdate && nextUpdateValue != null) {
        val updateUrl = nextUpdateValue.getLauncherDownload()?.browserDownloadUrl
            ?: nextUpdateValue.htmlUrl

        LauncherUpdateIndicator(
            modifier = modifier,
            openTo = updateUrl,
            onDismiss = {
                releaseViewModel.dismissLauncherUpdate()
            }
        )
    }
}

@Composable
fun ErrorInfoBody(failureReason: LaunchUtils.LauncherError, modifier: Modifier = Modifier) {
    val headline = when (failureReason) {
        LaunchUtils.LauncherError.LINKER_NEEDS_64BIT,
        LaunchUtils.LauncherError.LINKER_NEEDS_32BIT -> stringResource(R.string.load_failed_abi_error)
        LaunchUtils.LauncherError.LINKER_FAILED -> stringResource(R.string.load_failed_link_error_description)
        LaunchUtils.LauncherError.GENERIC -> stringResource(R.string.load_failed_generic_error_description)
        LaunchUtils.LauncherError.CRASHED -> stringResource(R.string.load_failed_crashed_description)
    }

    val recommendations = when (failureReason) {
        LaunchUtils.LauncherError.LINKER_NEEDS_64BIT -> listOf(
            stringResource(R.string.load_failed_recommendation_reinstall),
            stringResource(R.string.load_failed_recommendation_switch_64bit_abi),
        )
        LaunchUtils.LauncherError.LINKER_NEEDS_32BIT -> listOf(
            stringResource(R.string.load_failed_recommendation_reinstall),
            stringResource(R.string.load_failed_recommendation_switch_32bit_abi),
        )
        LaunchUtils.LauncherError.LINKER_FAILED -> listOf(
            stringResource(R.string.load_failed_recommendation_reinstall),
            stringResource(R.string.load_failed_recommendation_update),
        )
        LaunchUtils.LauncherError.GENERIC -> listOf(
            stringResource(R.string.load_failed_recommendation_reinstall),
            stringResource(R.string.load_failed_recommendation_update),
            stringResource(R.string.load_failed_recommendation_report)
        )
        LaunchUtils.LauncherError.CRASHED -> listOf(
            stringResource(R.string.load_failed_recommendation_safe_mode),
            stringResource(R.string.load_failed_recommendation_report)
        )
    }

    Column(modifier = modifier) {
        Text(headline)

        recommendations.forEach { r ->
            Text("\u2022\u00A0\u00A0$r")
        }
    }
}

@Composable
fun ErrorInfoTitle(failureReason: LaunchUtils.LauncherError) {
    val message = when (failureReason) {
        LaunchUtils.LauncherError.LINKER_NEEDS_64BIT,
        LaunchUtils.LauncherError.LINKER_NEEDS_32BIT,
        LaunchUtils.LauncherError.LINKER_FAILED -> stringResource(R.string.load_failed_link_error)
        LaunchUtils.LauncherError.GENERIC -> stringResource(R.string.load_failed_generic_error)
        LaunchUtils.LauncherError.CRASHED -> stringResource(R.string.load_failed_crashed)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.icon_error),
            contentDescription = stringResource(R.string.launcher_error_icon_alt),
            modifier = Modifier.size(28.dp)
        )
        Text(message, style = Typography.headlineMedium)
    }

}

fun onShareCrash(context: Context) {
    val lastCrash = LaunchUtils.getLastCrash(context)
    if (lastCrash == null) {
        Toast.makeText(context, R.string.launcher_error_export_missing, Toast.LENGTH_SHORT).show()
        return
    }

    val baseDirectory = LaunchUtils.getBaseDirectory(context)

    val providerLocation = lastCrash.toRelativeString(baseDirectory)
    val documentsPath = "${UserDirectoryProvider.ROOT}${providerLocation}"

    val uri = DocumentsContract.buildDocumentUri("${context.packageName}.user", documentsPath)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "application/octet-stream"
    }

    try {
        context.startActivity(shareIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_activity_found, Toast.LENGTH_SHORT).show()
    }
}

fun onShowLogs(context: Context) {
    val launchIntent = Intent(context, ApplicationLogsActivity::class.java)
    context.startActivity(launchIntent)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ErrorInfoSheet(failureInfo: LoadFailureInfo, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = { onDismiss() }, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ErrorInfoTitle(failureInfo.title)

            Spacer(Modifier.size(16.dp))

            ErrorInfoBody(failureInfo.title)

            if (failureInfo.description != null) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.launcher_error_details),
                        style = Typography.headlineSmall
                    )

                    Spacer(Modifier.size(8.dp))

                    Text(
                        failureInfo.description,
                        fontFamily = FontFamily.Monospace,
                        style = Typography.bodyMedium
                    )
                }
            }

            val clipboardManager = LocalClipboardManager.current
            val context = LocalContext.current

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    12.dp, alignment = Alignment.CenterHorizontally
                )
            ) {
                if (failureInfo.description != null) {
                    Button(onClick = { onShowLogs(context) }) {
                        Icon(
                            painterResource(R.drawable.icon_description),
                            contentDescription = null
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.launcher_error_view_logs))
                    }

                    FilledTonalButton(onClick = {
                        val message = failureInfo.extendedMessage ?: failureInfo.description
                        clipboardManager.setText(AnnotatedString(message))
                    }) {
                        Icon(
                            painterResource(R.drawable.icon_content_copy),
                            contentDescription = null
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.launcher_error_copy))
                    }
                } else {
                    Button(onClick = { onShareCrash(context) }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.launcher_error_export_crash))
                    }

                    FilledTonalButton(onClick = { onShowLogs(context) }) {
                        Icon(
                            painterResource(R.drawable.icon_description),
                            contentDescription = null
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.launcher_error_view_logs))
                    }
                }
            }

            Spacer(Modifier.size(8.dp))
        }
    }
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

suspend fun showFailureSnackbar(
    context: Context,
    loadFailureInfo: LoadFailureInfo,
    snackbarHostState: SnackbarHostState,
    onActionPerformed: () -> Unit
) {
    val message = if (loadFailureInfo.title == LaunchUtils.LauncherError.CRASHED) {
        context.getString(R.string.launcher_crashed)
    } else {
        context.getString(R.string.launcher_failed_to_load)
    }

    val res = snackbarHostState.showSnackbar(
        message = message,
        actionLabel = context.getString(R.string.launcher_error_more),
        duration = SnackbarDuration.Indefinite,
    )

    when (res) {
        SnackbarResult.ActionPerformed -> {
            onActionPerformed()
        }
        SnackbarResult.Dismissed -> {}
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

    val shouldAutomaticallyLaunch by PreferenceUtils.useBooleanPreference(
        preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY
    )

    val shouldUpdate by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.UPDATE_AUTOMATICALLY)

    val autoUpdateState by releaseViewModel.uiState.collectAsState()

    val geodeJustInstalled = (autoUpdateState as? ReleaseViewModel.ReleaseUIState.Finished)
        ?.hasUpdated ?: false
    val geodeInstalled = geodePreinstalled || geodeJustInstalled

    var beginLaunch by remember { mutableStateOf(false) }
    var showSafeModeDialog by remember { mutableStateOf(false) }
    var launchInSafeMode by remember { mutableStateOf(false) }

    LaunchedEffect(shouldUpdate) {
        if (shouldUpdate && !releaseViewModel.hasPerformedCheck) {
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
                modifier = Modifier.size(136.dp, 136.dp)
            )
            Text(
                context.getString(R.string.launcher_title),
                fontSize = 32.sp,
                modifier = Modifier.padding(12.dp)
            )

            if (gdInstalled && geodeInstalled) {
                val stopLaunch = releaseViewModel.isInUpdate || hasError || showSafeModeDialog
                if (shouldAutomaticallyLaunch && !stopLaunch) {
                    val countdownTimer = useCountdownTimer(
                        time = 3000,
                        onCountdownFinish = { beginLaunch = true }
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

                // compose apis don't provide a good way of adding long press to a button
                val interactionSource = remember { MutableInteractionSource() }
                val viewConfiguration = LocalViewConfiguration.current
                val haptics = LocalHapticFeedback.current

                LaunchedEffect(interactionSource) {
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                launchInSafeMode = false

                                delay(viewConfiguration.longPressTimeoutMillis)

                                // perform a second delay to make the action more obvious
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                delay(viewConfiguration.longPressTimeoutMillis)

                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                                showSafeModeDialog = true
                            }

                            is PressInteraction.Release -> {
                                if (!showSafeModeDialog) {
                                    beginLaunch = true
                                }
                            }
                        }
                    }
                }

                Row {
                    Button(
                        onClick = { },
                        enabled = !releaseViewModel.isInUpdate,
                        interactionSource = interactionSource
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

    if (showSafeModeDialog) {
        SafeModeDialog(
            onDismiss = {
                showSafeModeDialog = false
            },
            onLaunch = {
                launchInSafeMode = true
                beginLaunch = true

                showSafeModeDialog = false
            }
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