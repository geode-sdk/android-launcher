package com.geode.launcher.preferences

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.BuildConfig
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.utils.Constants
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.math.roundToInt

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
                    val gameVersion = GamePackageUtils.getGameVersionCodeOrNull(context.packageManager)
                    if (gameVersion != null && gameVersion < Constants.SUPPORTED_VERSION_CODE) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.launcher_game_update_required_short)
                        )
                    } else {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.preference_check_for_updates_none_found)
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

fun onOpenLogs(context: Context) {
    val launchIntent = Intent(context, ApplicationLogsActivity::class.java)
    context.startActivity(launchIntent)
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
                Icons.Filled.Warning,
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
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
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
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.surfaceContainerLow),
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
                    OptionsButton(
                        title = stringResource(R.string.preferences_open_file_manager),
                        onClick = { onOpenFileManager(context) }
                    )
                }

                OptionsGroup(stringResource(R.string.preference_category_gameplay)) {
                    SettingsCard(
                        title = context.getString(R.string.preference_load_automatically_name),
                        description = context.getString(R.string.preference_load_automatically_description),
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
                            title = context.getString(R.string.preference_screen_zoom_fit_name),
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
                }

                OptionsGroup(context.getString(R.string.preference_category_updater)) {
                    SettingsCard(
                        title = context.getString(R.string.preference_update_automatically_name),
                        preferenceKey = PreferenceUtils.Key.UPDATE_AUTOMATICALLY,
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

                val developerModeEnabled by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.DEVELOPER_MODE)

                OptionsGroup(stringResource(R.string.preference_category_developer)) {
                    OptionsButton(
                        title = stringResource(R.string.preferences_view_logs),
                        icon = {
                            Icon(painterResource(R.drawable.icon_description), contentDescription = null)
                        },
                        onClick = { onOpenLogs(context) }
                    )

                    if (developerModeEnabled) {
                        OptionsButton(
                            title = stringResource(R.string.preference_open_developer_options),
                            icon = {
                                Icon(painterResource(R.drawable.icon_data_object), contentDescription = null)
                            },
                            onClick = { onOpenDeveloperOptions(context) }
                        )
                    }

                    OptionsButton(
                        title = context.getString(R.string.preferences_copy_external_button),
                        description = LaunchUtils.getBaseDirectory(context).path,
                        onClick = { onOpenFolder(context) }
                    )
                }

                OptionsGroup(stringResource(R.string.preference_category_about)) {
                    var launcherButtonTapped by remember { mutableIntStateOf(0) }
                    var showDeveloperDialog by remember { mutableStateOf(false) }

                    OptionsButton(
                        stringResource(R.string.preference_launcher_version_name),
                        stringResource(R.string.preference_launcher_version_description, BuildConfig.VERSION_NAME),
                        displayInline = true
                    ) {
                        launcherButtonTapped += 1

                        if (launcherButtonTapped >= 7 && !developerModeEnabled) {
                            showDeveloperDialog = true
                            launcherButtonTapped = 0
                        }
                    }

                    val context = LocalContext.current
                    if (showDeveloperDialog) {
                        DeveloperModeDialog(
                            onDismiss = {
                                showDeveloperDialog = false
                            },
                            onEnable = {
                                PreferenceUtils.get(context).setBoolean(PreferenceUtils.Key.DEVELOPER_MODE, true)
                                showDeveloperDialog = false
                            }
                        )
                    }

                    OptionsLabel(
                        stringResource(R.string.preference_loader_version_name),
                        stringResource(R.string.preference_loader_version_description, currentRelease ?: "unknown"),
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
                            context.getString(when (GamePackageUtils.identifyGameSource(context.packageManager)) {
                                GamePackageUtils.GameSource.GOOGLE -> R.string.preference_game_source_google
                                GamePackageUtils.GameSource.AMAZON -> R.string.preference_game_source_amazon
                                else -> R.string.preference_game_source_unknown
                            })
                        }

                        OptionsLabel(
                            stringResource(R.string.preference_game_version_name),
                            stringResource(R.string.preference_game_version_description, gameVersion, gameSource),
                            displayInline = true
                        )

                        OptionsLabel(
                            stringResource(R.string.preference_architecture_name),
                            stringResource(R.string.preference_architecture_description, Build.MODEL, LaunchUtils.applicationArchitecture),
                            displayInline = true
                        )
                    }
                }
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