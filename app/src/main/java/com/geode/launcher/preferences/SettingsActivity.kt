package com.geode.launcher.preferences

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.view.WindowManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.BuildConfig
import com.geode.launcher.MainActivity
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.preferences.components.BackupButton
import com.geode.launcher.preferences.components.OptionsButton
import com.geode.launcher.preferences.components.OptionsCard
import com.geode.launcher.preferences.components.OptionsGroup
import com.geode.launcher.preferences.components.OptionsLabel
import com.geode.launcher.preferences.components.OptionsTitle
import com.geode.launcher.preferences.components.SafeModeDialog
import com.geode.launcher.preferences.components.SettingsCard
import com.geode.launcher.preferences.components.SettingsFPSCard
import com.geode.launcher.preferences.components.SettingsRangeCard
import com.geode.launcher.preferences.components.SettingsSelectCard
import com.geode.launcher.preferences.components.SettingsStringSelectCard
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.ui.theme.robotoMonoFamily
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.utils.ApplicationIcon
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.IconUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.math.roundToInt
import kotlin.time.Clock


class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeOption by PreferenceUtils.useIntPreference(PreferenceUtils.Key.THEME)
            val theme = Theme.fromInt(themeOption)

            val backgroundOption by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.BLACK_BACKGROUND)
            val dynamicColorOption by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.DISABLE_USER_THEME)

            CompositionLocalProvider(LocalTheme provides theme) {
                GeodeLauncherTheme(theme = theme, blackBackground = backgroundOption, dynamicColor = !dynamicColorOption) {
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
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getText(R.string.no_activity_found), Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
fun UpdateErrorDialog(
    error: Exception,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardLabel = stringResource(R.string.application_log_copy_label)

    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.icon_warning),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.preference_check_for_updates_help_title)) },
        text = {
            SelectionContainer {
                Text(
                    error.stackTraceToString(),
                    fontFamily = robotoMonoFamily,
                    style = Typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                coroutineScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText(clipboardLabel, error.stackTraceToString()))
                    )
                }
            }) {
                Text(stringResource(R.string.preference_check_for_updates_help_copy))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.message_box_accept))
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
fun UpdateIndicator(
    snackbarHostState: SnackbarHostState,
    updateStatus: ReleaseViewModel.ReleaseUIState
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    var enablePopup by remember { mutableStateOf(false) }
    var enableHelp by remember { mutableStateOf(false) }

    var currentError by remember { mutableStateOf<Exception?>(null) }

    val currentErrorS = currentError
    if (currentErrorS != null && enableHelp) {
        UpdateErrorDialog(currentErrorS) {
            enableHelp = false
        }
    }


    when (updateStatus) {
        is ReleaseViewModel.ReleaseUIState.InUpdateCheck -> {
            enablePopup = true
            CircularProgressIndicator()
        }
        is ReleaseViewModel.ReleaseUIState.InDownload -> {
            // is this the ideal design? idk
            enablePopup = true
            if (updateStatus.outOf != null) {
                CircularProgressIndicator(
                    progress = {
                        val progress = updateStatus.downloaded / updateStatus.outOf.toDouble()
                        progress.toFloat()
                    },
                )
            } else {
                CircularProgressIndicator()
            }
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
                        resources.getString(R.string.release_fetch_no_internet)
                    else -> resources.getString(R.string.preference_check_for_updates_failed)
                }

                currentError = updateStatus.exception

                val snackbarResult = snackbarHostState.showSnackbar(message, resources.getString(R.string.preference_check_for_updates_failed_action))
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    enableHelp = true
                }
            }
            is ReleaseViewModel.ReleaseUIState.Finished -> {
                if (updateStatus.hasUpdated) {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.preference_check_for_updates_success)
                    )
                } else {
                    val gameVersion = GamePackageUtils.getGameVersionCodeOrNull(context.packageManager)
                    if (gameVersion != null && gameVersion < Constants.SUPPORTED_VERSION_CODE) {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.launcher_game_update_required_short)
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.preference_check_for_updates_none_found)
                        )
                    }
                }
            }
            else -> {}
        }
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

fun onOpenDeveloperOptions(context: Context) {
    val launchIntent = Intent(context, DeveloperSettingsActivity::class.java)
    context.startActivity(launchIntent)
}

@Composable
fun DeveloperModeDialog(onDismiss: () -> Unit, onEnable: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.icon_warning),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.preference_developer_options_dialog_title)) },
        text = { Text(stringResource(R.string.preference_developer_mode_about)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.message_box_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.message_box_accept))
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
fun GeneralSettingsGroup() {
    val context = LocalContext.current

    OptionsGroup(stringResource(R.string.preference_category_testing)) {
        SettingsSelectCard(
            title = stringResource(R.string.preference_theme_name),
            dialogTitle = stringResource(R.string.preference_theme_select),
            preferenceKey = PreferenceUtils.Key.THEME,
            options = linkedMapOf(
                0 to stringResource(R.string.preference_theme_default),
                1 to stringResource(R.string.preference_theme_light),
                2 to stringResource(R.string.preference_theme_dark),
            ),
            extraSelectBehavior = { updateTheme(context, it) }
        )
        SettingsCard(
            title = stringResource(R.string.preference_black_background_name),
            preferenceKey = PreferenceUtils.Key.BLACK_BACKGROUND
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsCard(
                title = stringResource(R.string.preference_disable_styles),
                preferenceKey = PreferenceUtils.Key.DISABLE_USER_THEME
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val currentIcon by PreferenceUtils.useStringPreference(PreferenceUtils.Key.SELECTED_ICON)
            val iconName = remember(currentIcon) {
                IconUtils.getIconDetails(
                    ApplicationIcon.fromId(currentIcon ?: "default")
                ).nameId
            }

            OptionsButton(
                title = stringResource(R.string.preference_set_application_icon),
                onClick = {
                    val launchIntent = Intent(context, ApplicationIconActivity::class.java)
                    context.startActivity(launchIntent)
                },
                description = stringResource(iconName),
                displayInline = true,
            )
        }

        OptionsButton(
            title = stringResource(R.string.preferences_open_file_manager),
            icon = {
                Icon(
                    painterResource(R.drawable.icon_folder),
                    contentDescription = null
                )
            },
            onClick = { onOpenFileManager(context) }
        )
    }
}

@Composable
fun GameplaySettingsGroup() {
    val context = LocalContext.current

    OptionsGroup(stringResource(R.string.preference_category_gameplay)) {
        SettingsCard(
            title = stringResource(R.string.preference_load_automatically_name),
            description = stringResource(R.string.preference_load_automatically_description),
            preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY,
        )
        SettingsSelectCard(
            title = stringResource(R.string.preference_display_mode_name),
            dialogTitle = stringResource(R.string.preference_display_mode_select),
            preferenceKey = PreferenceUtils.Key.DISPLAY_MODE,
            options = buildMap {
                put(0, stringResource(R.string.preference_display_mode_default))
                put(1, stringResource(R.string.preference_display_mode_legacy))

                // necessary api doesn't exist on older versions of android
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    put(2, stringResource(R.string.preference_display_mode_fullscreen))
                }
            }
        )
        val currentDisplayMode by PreferenceUtils.useIntPreference(PreferenceUtils.Key.DISPLAY_MODE)

        if (currentDisplayMode == 1) {
            SettingsStringSelectCard(
                title = stringResource(R.string.preference_custom_aspect_ratio_name),
                description = stringResource(R.string.preference_custom_aspect_ratio_description),
                dialogTitle = stringResource(R.string.preference_custom_aspect_ratio_select),
                preferenceKey = PreferenceUtils.Key.CUSTOM_ASPECT_RATIO,
                options = linkedMapOf(
                    "16_9" to stringResource(R.string.preference_custom_aspect_ratio_16x9),
                    "16_10" to stringResource(R.string.preference_custom_aspect_ratio_16x10),
                    "4_3" to stringResource(R.string.preference_custom_aspect_ratio_4x3),
                    "1_1" to stringResource(R.string.preference_custom_aspect_ratio_1x1),
                )
            )
        }

        SettingsRangeCard(
            title = stringResource(R.string.preference_screen_zoom_name),
            description = stringResource(R.string.preference_screen_zoom_description),
            dialogTitle = stringResource(R.string.preference_screen_zoom_select),
            preferenceKey = PreferenceUtils.Key.SCREEN_ZOOM,
            labelSuffix = "x",
            range = 25..100,
            scale = 100,
            step = 5,
        ) {
            SettingsCard(
                title = stringResource(R.string.preference_screen_zoom_fit_name),
                preferenceKey = PreferenceUtils.Key.SCREEN_ZOOM_FIT,
                asCard = false
            )
        }

        val maxFrameRate = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display.supportedModes.maxOf { it.refreshRate }
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
            }.roundToInt()
        }

        SettingsFPSCard(
            title = stringResource(R.string.preference_limit_framerate_name),
            dialogTitle = stringResource(R.string.preference_limit_framerate_select),
            preferenceKey = PreferenceUtils.Key.LIMIT_FRAME_RATE,
            maxFrameRate = maxFrameRate
        )

        var showSafeModeDialog by remember { mutableStateOf(false) }

        OptionsButton(
            title = stringResource(R.string.preference_launch_safe_mode),
            icon = {
                Icon(
                    painterResource(R.drawable.icon_shield),
                    contentDescription = null
                )
            }
        ) {
            showSafeModeDialog = true
        }

        if (showSafeModeDialog) {
            SafeModeDialog(onDismiss = {
                showSafeModeDialog = false
            }) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    // hardcode it, why not
                    action = Intent.ACTION_VIEW
                    data = "geode-launcher://main/launch?safe-mode=true".toUri()
                }
                context.startActivity(launchIntent)
            }
        }
    }
}

@Composable
fun UpdateSettingsGroup(releaseViewModel: ReleaseViewModel, snackbarHostState: SnackbarHostState, onUpdate: () -> Unit) {
    val updateStatus by releaseViewModel.uiState.collectAsState()

    OptionsGroup(stringResource(R.string.preference_category_updater)) {
        SettingsCard(
            title = stringResource(R.string.preference_update_automatically_name),
            preferenceKey = PreferenceUtils.Key.UPDATE_AUTOMATICALLY,
        )

        val lastUpdateCheck by PreferenceUtils.useLongPreference(PreferenceUtils.Key.LAST_UPDATE_CHECK_TIME)
        val checkAgoString = remember(lastUpdateCheck) {
            if (lastUpdateCheck != 0L) {
                val now = Clock.System.now().toEpochMilliseconds()
                DateUtils.getRelativeTimeSpanString(
                    lastUpdateCheck,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            } else {
                "never"
            }
        }

        OptionsCard(
            title = {
                OptionsTitle(
                    title = stringResource(R.string.preference_check_for_updates_button),
                    icon = {
                        Icon(
                            painterResource(R.drawable.icon_update),
                            contentDescription = null
                        )
                    },
                    description = stringResource(
                        R.string.preference_check_for_updates_description,
                        checkAgoString
                    )
                        .takeIf { lastUpdateCheck != 0L }
                )
            },
            modifier = Modifier
                .clickable(
                    onClick = {
                        onUpdate()
                    },
                    role = Role.Button
                )
        ) {
            UpdateIndicator(snackbarHostState, updateStatus)
        }
    }
}

@Composable
fun AdvancedSettingsGroup(developerModeEnabled: Boolean, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current

    OptionsGroup(stringResource(R.string.preference_category_developer)) {
        if (developerModeEnabled) {
            OptionsButton(
                title = stringResource(R.string.preference_open_developer_options),
                icon = {
                    Icon(
                        painterResource(R.drawable.icon_data_object),
                        contentDescription = null
                    )
                },
                onClick = { onOpenDeveloperOptions(context) }
            )
        }

        OptionsButton(
            title = stringResource(R.string.preferences_view_crashes),
            icon = {
                Icon(
                    painterResource(R.drawable.icon_bug_report),
                    contentDescription = null
                )
            },
            onClick = {
                val launchIntent = Intent(context, CrashLogsActivity::class.java)
                context.startActivity(launchIntent)
            }
        )

        BackupButton(snackbarHostState)
    }
}

@Composable
fun AboutSettingsGroup(developerModeEnabled: Boolean) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val currentRelease by PreferenceUtils.useStringPreference(PreferenceUtils.Key.CURRENT_VERSION_TAG)

    OptionsGroup(stringResource(R.string.preference_category_about)) {
        var launcherButtonTapped by remember { mutableIntStateOf(0) }
        var showDeveloperDialog by remember { mutableStateOf(false) }

        OptionsButton(
            stringResource(R.string.preference_launcher_version_name),
            stringResource(
                R.string.preference_launcher_version_description,
                BuildConfig.VERSION_NAME
            ),
            displayInline = true
        ) {
            launcherButtonTapped += 1

            if (launcherButtonTapped >= 7) {
                if (developerModeEnabled) {
                    Toast.makeText(
                        context,
                        R.string.preference_enable_developer_again,
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } else {
                    showDeveloperDialog = true
                }
                launcherButtonTapped = 0
            }
        }

        if (showDeveloperDialog) {
            DeveloperModeDialog(
                onDismiss = {
                    showDeveloperDialog = false
                },
                onEnable = {
                    PreferenceUtils.get(context)
                        .setBoolean(PreferenceUtils.Key.DEVELOPER_MODE, true)
                    showDeveloperDialog = false
                }
            )
        }

        OptionsLabel(
            stringResource(R.string.preference_loader_version_name),
            stringResource(
                R.string.preference_loader_version_description,
                currentRelease ?: "unknown"
            ),
            displayInline = true
        )

        val gameInstalled = remember {
            GamePackageUtils.isGameInstalled(context.packageManager)
        }

        if (gameInstalled) {
            val gameVersion = remember {
                GamePackageUtils.getUnifiedVersionName(context.packageManager)
            }

            val gameSource = remember {
                resources.getString(
                    when (GamePackageUtils.identifyGameSource(context.packageManager)) {
                        GamePackageUtils.GameSource.GOOGLE -> R.string.preference_game_source_google
                        GamePackageUtils.GameSource.AMAZON -> R.string.preference_game_source_amazon
                        else -> R.string.preference_game_source_unknown
                    }
                )
            }

            OptionsLabel(
                stringResource(R.string.preference_game_version_name),
                stringResource(
                    R.string.preference_game_version_description,
                    gameVersion,
                    gameSource
                ),
                displayInline = true
            )

            OptionsLabel(
                stringResource(R.string.preference_architecture_name),
                stringResource(
                    R.string.preference_architecture_description,
                    Build.MODEL,
                    LaunchUtils.applicationArchitecture
                ),
                displayInline = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    releaseViewModel: ReleaseViewModel = viewModel(factory = ReleaseViewModel.Factory)
) {
    val resources = LocalResources.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showUpdateInProgress by remember { mutableStateOf(false) }

    if (showUpdateInProgress) {
        LaunchedEffect(snackbarHostState) {
            snackbarHostState.showSnackbar(
                resources.getString(R.string.preference_check_for_updates_already_updating)
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
                            painterResource(R.drawable.icon_arrow_back),
                            contentDescription = stringResource(R.string.back_icon_alt)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.title_activity_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.background),
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
                GeneralSettingsGroup()
                GameplaySettingsGroup()

                UpdateSettingsGroup(releaseViewModel, snackbarHostState, onUpdate = {
                    if (releaseViewModel.isInUpdate) {
                        showUpdateInProgress = true
                    } else {
                        releaseViewModel.runReleaseCheck(true)
                    }
                })

                val developerModeEnabled by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.DEVELOPER_MODE)

                AdvancedSettingsGroup(developerModeEnabled, snackbarHostState)
                AboutSettingsGroup(developerModeEnabled)

                Spacer(Modifier.height(4.dp))
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