package com.geode.geodelauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.geode.geodelauncher.ui.theme.GeodeLauncherTheme
import com.geode.geodelauncher.ui.theme.Typography

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackPressedDispatcher: OnBackPressedDispatcher?) {
    val context = LocalContext.current

    Scaffold(
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
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionsGroup(context.getString(R.string.preference_category_testing)) {
                    SettingsCard(
                        title = context.getString(R.string.preference_load_testing_name),
                        preferenceId = R.string.preference_load_testing
                    )
                    SettingsCard(
                        title = context.getString(R.string.preference_load_automatically_name),
                        description = context.getString(R.string.preference_load_automatically_description),
                        preferenceId = R.string.preference_load_automatically
                    )
                    OptionsButton(
                        title = context.getString(R.string.preferences_copy_external_button),
                        onClick = { onOpenFolder(context) }
                    )
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

fun toggleSetting(context: Context, @StringRes preferenceId: Int): Boolean {
    val preferences = context.getSharedPreferences(
        context.getString(R.string.preference_file_key), Context.MODE_PRIVATE
    )

    val current = preferences.getBoolean(
        context.getString(preferenceId), false
    )
    preferences.edit {
        putBoolean(context.getString(preferenceId), !current)
        commit()
    }

    return preferences.getBoolean(context.getString(preferenceId), false)
}

fun getSetting(context: Context, @StringRes preferenceId: Int): Boolean {
    val preferences = context.getSharedPreferences(
        context.getString(R.string.preference_file_key), Context.MODE_PRIVATE
    )

    return preferences.getBoolean(context.getString(preferenceId), false)
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
fun OptionsButton(title: String, onClick: () -> Unit) {
    OptionsCard(
        title = {
            Text(title)
        },
        modifier = Modifier
            .clickable(onClick = onClick, role = Role.Button)
    ) { }
}

@Composable
fun SettingsCard(title: String, description: String? = null, @StringRes preferenceId: Int) {
    val context = LocalContext.current
    val settingEnabled = remember {
        mutableStateOf(getSetting(context, preferenceId)) }

    OptionsCard(
        title = {
            Column(
                Modifier.fillMaxWidth(0.75f),
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
        },
        modifier = Modifier.toggleable(
            value = settingEnabled.value,
            onValueChange = { settingEnabled.value = toggleSetting(context, preferenceId) },
            role = Role.Switch,
        )
    ) {
        Switch(checked = settingEnabled.value, onCheckedChange = null)
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
                preferenceId = R.string.preference_load_testing
            )
            SettingsCard(
                title = "Testing option 2",
                preferenceId = R.string.preference_load_automatically
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