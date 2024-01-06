package com.geode.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.api.ReleaseViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.PreferenceUtils
import java.net.ConnectException
import java.net.UnknownHostException

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeodeLauncherTheme {
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
    context.getExternalFilesDir(null)?.let { file ->
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
                context.getString(R.string.export_folder_copied),
                Toast.LENGTH_SHORT
            ).show()
        }
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

    Scaffold(
        snackbarHost = {
           SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.settings_back_icon_alt)
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
                    SettingsCard(
                        title = context.getString(R.string.preference_load_testing_name),
                        preferenceKey = PreferenceUtils.Key.LOAD_TESTING,
                    )
                    SettingsCard(
                        title = context.getString(R.string.preference_load_automatically_name),
                        description = context.getString(R.string.preference_load_automatically_description),
                        preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY,
                    )
                    OptionsButton(
                        title = context.getString(R.string.preferences_copy_external_button),
                        description = context.getExternalFilesDir(null)?.path,
                        onClick = { onOpenFolder(context) }
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
                                        releaseViewModel.runReleaseCheck()
                                    }
                                },
                                role = Role.Button
                            )
                    ) {
                        UpdateIndicator(snackbarHostState, updateStatus)
                    }
                }
/*
                OptionsGroup("Data") {
                    OptionsButton(
                        title = "Export save data",
                        onClick = { onExportSaveData(context) }
                    )
                }
 */
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