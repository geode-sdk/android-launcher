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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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

/*
fun onExportSaveData(context: Context) {
    Toast.makeText(
        context,
        "This function is not implemented yet!",
        Toast.LENGTH_SHORT
    ).show()
}
*/

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

            CircularProgressIndicator(progress.toFloat())
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
                            imageVector = Icons.Filled.ArrowBack,
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
                    SettingsCard(
                        title = stringResource(R.string.preference_ignore_load_failure_name),
                        preferenceKey = PreferenceUtils.Key.IGNORE_LOAD_FAILURE,
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

fun toggleSetting(context: Context, preferenceKey: PreferenceUtils.Key): Boolean {
    val preferences = PreferenceUtils.get(context)

    return preferences.toggleBoolean(preferenceKey)
}

fun getSetting(context: Context, preferenceKey: PreferenceUtils.Key): Boolean {
    val preferences = PreferenceUtils.get(context)

    return preferences.getBoolean(preferenceKey)
}

@Composable
fun OptionsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = Typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
fun SettingsSelectCard(
    title: String,
    dialogTitle: String,
    maxVal: Int,
    preferenceKey: PreferenceUtils.Key,
    toLabel: @Composable (Int) -> String,
    extraSelectBehavior: ((Int) -> Unit)? = null
) {
    val preferenceValue by PreferenceUtils.useIntPreference(preferenceKey)

    var showDialog by remember { mutableStateOf(false) }

    OptionsCard(
        title = { OptionsTitle(title = title) },
        modifier = Modifier
            .clickable(
                onClick = {
                    showDialog = true
                },
                role = Role.Button
            )
    ) {
        Text(toLabel(preferenceValue))
    }

    if (showDialog) {
        val context = LocalContext.current

        SelectDialog(
            title = dialogTitle,
            onDismissRequest = {
               showDialog = false
            },
            onSelect = { selected ->
                showDialog = false
                PreferenceUtils.get(context)
                    .setInt(preferenceKey, selected)
                extraSelectBehavior?.invoke(selected)
            },
            initialValue = preferenceValue,
            toLabel = toLabel,
            optionsCount = maxVal
        )
    }
}

@Composable
fun SettingsStringCard(
    title: String,
    dialogTitle: String,
    preferenceKey: PreferenceUtils.Key,
    filterInput: ((String) -> String)? = null
) {
    var preferenceValue by PreferenceUtils.useStringPreference(preferenceKey)

    var showDialog by remember { mutableStateOf(false) }

    OptionsCard(
        title = {
            OptionsTitle(title = title, description = preferenceValue)
        },
        modifier = Modifier
            .clickable(
                onClick = {
                    showDialog = true
                },
                role = Role.Button
            )
    ) { }

    if (showDialog) {
        StringDialog(
            title = dialogTitle,
            onDismissRequest = { showDialog = false },
            onSelect = {
                preferenceValue = it
                showDialog = false
            },
            initialValue = preferenceValue ?: "",
            filterInput = filterInput
        )
    }
}

@Composable
fun StringDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    initialValue: String,
    filterInput: ((String) -> String)? = null
) {
    var enteredValue by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = { onDismissRequest() },
        title = {
            Text(title)
        },
        text = {
            OutlinedTextField(
                value = enteredValue,
                onValueChange = {
                    enteredValue = filterInput?.invoke(it) ?: it
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrect = false
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSelect(enteredValue) }) {
                Text(stringResource(R.string.message_box_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.message_box_cancel))
            }
        },
    )
}

@Composable
fun SelectDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit,
    initialValue: Int,
    toLabel: @Composable (Int) -> String,
    optionsCount: Int,
) {
    var selectedValue by remember { mutableIntStateOf(initialValue) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp)
        ) {
            // styling a dialog is actually a little hard if you're doing what i'm doing
            // maybe there's a better way to make these padding values...
            Column {
                Text(
                    title,
                    style = Typography.titleLarge,
                    modifier = Modifier.padding(
                        start = 28.dp,
                        top = 24.dp,
                        bottom = 12.dp
                    )
                )

                // do not give the row or column padding!! it messes up the selection effect
                (0..optionsCount).forEach { id ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = { selectedValue = id },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        RadioButton(
                            selected = selectedValue == id,
                            onClick = { selectedValue = id }
                        )
                        Text(
                            toLabel(id),
                            style = Typography.bodyMedium
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = 16.dp,
                            end = 16.dp,
                            top = 4.dp
                        )
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.message_box_cancel))
                    }

                    TextButton(onClick = { onSelect(selectedValue) }) {
                        Text(stringResource(R.string.message_box_accept))
                    }
                }
            }
        }
    }
}

@Composable
fun OptionsButton(title: String, description: String? = null, onClick: () -> Unit) {
    OptionsCard(
        title = {
            OptionsTitle(
                title = title,
                description = description
            )
        },
        modifier = Modifier
            .clickable(onClick = onClick, role = Role.Button)
    ) { }
}

@Composable
fun SettingsCard(title: String, description: String? = null, preferenceKey: PreferenceUtils.Key) {
    val context = LocalContext.current
    val settingEnabled = remember {
        mutableStateOf(getSetting(context, preferenceKey))
    }

    OptionsCard(
        title = {
            OptionsTitle(
                Modifier.fillMaxWidth(0.75f),
                title = title,
                description = description
            )
        },
        modifier = Modifier.toggleable(
            value = settingEnabled.value,
            onValueChange = { settingEnabled.value = toggleSetting(context, preferenceKey) },
            role = Role.Switch,
        )
    ) {
        Switch(checked = settingEnabled.value, onCheckedChange = null)
    }
}

@Composable
fun OptionsTitle(modifier: Modifier = Modifier, title: String, description: String? = null) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title)
        if (!description.isNullOrEmpty()) {
            Text(
                description,
                style = Typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun OptionsCard(modifier: Modifier = Modifier, title: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        title()
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun OptionsCardPreview() {
    GeodeLauncherTheme {
        OptionsGroup(title = "Preview Group") {
            SettingsCard(
                title = "Load files from /test",
                description = "Very long testing description goes here. It is incredibly long, it should wrap onto a new line.",
                preferenceKey = PreferenceUtils.Key.LOAD_TESTING
            )
            SettingsCard(
                title = "Testing option 2",
                preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun DefaultPreview() {
    GeodeLauncherTheme {
        SettingsScreen(null)
    }
}