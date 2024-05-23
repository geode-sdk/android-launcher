package com.geode.launcher

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import java.net.ConnectException
import java.net.UnknownHostException

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                        SettingsScreen(
                            onBackPressedDispatcher = onBackPressedDispatcher
                        )
                    }
                }
            }
        }
    }
}

fun onOpenFolder(context: Context) {
    val file = LaunchUtils.getBaseDirectory(context)
    val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(
        ClipData.newPlainText(
            context.getString(R.string.export_folder_tag),
            file.path
        )
    )
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(
            context,
            context.getString(R.string.text_copied),
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun onOpenFileManager(context: Context) {
    // future thoughts: Intent.ACTION_VIEW may not work on some devices
    // the "correct" solution is to go through a list of possible intents/applications
    // don't feel like writing that behavior. this is good enough

    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = DocumentsContract.buildRootUri(
            "${context.packageName}.user",
            UserDirectoryProvider.ROOT
        )

        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getText(R.string.no_activity_found), Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
fun UpdateIndicator(
    snackbarHostState: SnackbarHostState,
    updateStatus: ReleaseViewModel.ReleaseUIState
) {
    val context = LocalContext.current

    var enablePopup by remember { mutableStateOf(false) }

    when (updateStatus) {
        is ReleaseViewModel.ReleaseUIState.InUpdateCheck -> {
            enablePopup = true
            CircularProgressIndicator()
        }
        is ReleaseViewModel.ReleaseUIState.InDownload -> {
            // is this the ideal design? idk
            enablePopup = true
            val progress = updateStatus.downloaded / updateStatus.outOf.toDouble()

            CircularProgressIndicator(
                progress = { progress.toFloat() },
            )
        }
        else -> {}
    }

    LaunchedEffect(updateStatus) {
        // only show popup if some progress was shown
        if (!enablePopup) {
            return@LaunchedEffect
        }

        when (updateStatus) {
            is ReleaseViewModel.ReleaseUIState.Failure -> {
                val message = when (updateStatus.exception) {
                    is UnknownHostException, is ConnectException ->
                        context.getString(R.string.release_fetch_no_internet)
                    else -> context.getString(R.string.preference_check_for_updates_failed)
                }

                snackbarHostState.showSnackbar(message)
            }
            is ReleaseViewModel.ReleaseUIState.Finished -> {
                if (updateStatus.hasUpdated) {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.preference_check_for_updates_success)
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.preference_check_for_updates_none_found)
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
fun themeToKey(theme: Int): String {
    return when (theme) {
        1 -> stringResource(R.string.preference_theme_light)
        2 -> stringResource(R.string.preference_theme_dark)
        else -> stringResource(R.string.preference_theme_default)
    }
}

@Composable
fun displayOptionToKey(option: Int): String {
    return when (option) {
        1 -> stringResource(R.string.preference_display_mode_legacy)
        2 -> stringResource(R.string.preference_display_mode_fullscreen)
        else -> stringResource(R.string.preference_display_mode_default)
    }
}

fun updateTheme(context: Context, theme: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.setApplicationNightMode(
            when (theme) {
                1 -> UiModeManager.MODE_NIGHT_NO
                2 -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_CUSTOM
            }
        )
    } else {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}

fun onOpenLogs(context: Context) {
    val launchIntent = Intent(context, ApplicationLogsActivity::class.java)
    context.startActivity(launchIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    releaseViewModel: ReleaseViewModel = viewModel(factory = ReleaseViewModel.Factory)
) {
    val context = LocalContext.current

    val currentRelease by PreferenceUtils.useStringPreference(PreferenceUtils.Key.CURRENT_VERSION_TAG)
    val updateStatus by releaseViewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showUpdateInProgress by remember { mutableStateOf(false) }

    if (showUpdateInProgress) {
        LaunchedEffect(snackbarHostState) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.preference_check_for_updates_already_updating)
            )
            showUpdateInProgress = false
        }
    }

    // fix theme transition by giving it the exact same animation as the top bar
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "background color"
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        snackbarHost = {
           SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.back_icon_alt)
                        )
                    }
                },
                title = {
                    Text(
                        context.getString(R.string.title_activity_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionsGroup(context.getString(R.string.preference_category_testing)) {
                    SettingsSelectCard(
                        title = stringResource(R.string.preference_theme_name),
                        dialogTitle = stringResource(R.string.preference_theme_select),
                        maxVal = 2,
                        preferenceKey = PreferenceUtils.Key.THEME,
                        toLabel = { themeToKey(it) },
                        extraSelectBehavior = { updateTheme(context, it) }
                    )
                    SettingsCard(
                        title = stringResource(R.string.preference_black_background_name),
                        preferenceKey = PreferenceUtils.Key.BLACK_BACKGROUND
                    )
                    SettingsCard(
                        title = context.getString(R.string.preference_load_automatically_name),
                        description = context.getString(R.string.preference_load_automatically_description),
                        preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY,
                    )
                    SettingsSelectCard(
                        title = stringResource(R.string.preference_display_mode_name),
                        dialogTitle = stringResource(R.string.preference_display_mode_select),
                        // necessary api doesn't exist on older versions of android
                        maxVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 2 else 1,
                        preferenceKey = PreferenceUtils.Key.DISPLAY_MODE,
                        toLabel = { displayOptionToKey(it) }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        SettingsCard(
                            title = context.getString(R.string.preference_force_hrr),
                            preferenceKey = PreferenceUtils.Key.FORCE_HRR,
                        )
                    }
                    OptionsButton(
                        title = stringResource(R.string.preferences_open_file_manager),
                        onClick = { onOpenFileManager(context) }
                    )
                }

                OptionsGroup(context.getString(R.string.preference_category_updater)) {
                    SettingsCard(
                        title = context.getString(R.string.preference_update_automatically_name),
                        preferenceKey = PreferenceUtils.Key.UPDATE_AUTOMATICALLY,
                    )
                    SettingsCard(
                        title = context.getString(R.string.preference_release_channel_name),
                        preferenceKey = PreferenceUtils.Key.RELEASE_CHANNEL,
                    )
                    OptionsCard(
                        title = {
                            OptionsTitle(
                                title = stringResource(R.string.preference_check_for_updates_button),
                                description = stringResource(
                                    R.string.preference_check_for_updates_description,
                                    currentRelease ?: "unknown"
                                )
                            )
                        },
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    if (releaseViewModel.isInUpdate) {
                                        showUpdateInProgress = true
                                    } else {
                                        releaseViewModel.runReleaseCheck(true)
                                    }
                                },
                                role = Role.Button
                            )
                    ) {
                        UpdateIndicator(snackbarHostState, updateStatus)
                    }
                }

                OptionsGroup(stringResource(R.string.preference_category_developer)) {
                    SettingsStringCard(
                        title = stringResource(R.string.preference_launch_arguments_name),
                        dialogTitle = stringResource(R.string.preference_launch_arguments_set_title),
                        preferenceKey = PreferenceUtils.Key.LAUNCH_ARGUMENTS,
                        filterInput = { it.filter { c ->
                            // if only there was a better way to define this!
                            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890!@#$%^&*(){}<>[]?:;'\"~`-_+=\\| ".contains(c)
                        }}
                    )
                    OptionsButton(
                        title = context.getString(R.string.preferences_copy_external_button),
                        description = LaunchUtils.getBaseDirectory(context).path,
                        onClick = { onOpenFolder(context) }
                    )
                    OptionsButton(
                        title = stringResource(R.string.preferences_view_logs),
                        onClick = { onOpenLogs(context) }
                    )
                }

                Text(
                    stringResource(R.string.preference_launcher_version, BuildConfig.VERSION_NAME),
                    style = Typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    )
}

@Preview(showSystemUi = true)
@Composable
fun DefaultPreview() {
    GeodeLauncherTheme {
        SettingsScreen(null)
    }
}