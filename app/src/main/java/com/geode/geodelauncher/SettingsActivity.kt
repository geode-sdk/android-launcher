package com.geode.geodelauncher

import android.content.Context
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
import androidx.compose.material.icons.filled.List
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

fun onExportSaveData(context: Context) {
    Toast.makeText(
        context,
        "This function is not implemented yet!",
        Toast.LENGTH_SHORT
    ).show()
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
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Text(
                        "Settings",
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
                }
                OptionsGroup("Data") {
                    OptionsButton(onClick = { onExportSaveData(context) }) {
                        Text("Export save data")
                    }
                }
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

    return preferences.getBoolean(context.getString(R.string.preference_load_testing), false)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun OptionsButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    OptionsCard(
        title = content,
        modifier = Modifier
            .clickable(onClick = onClick, role = Role.Button)
    ) { }
}

@Composable
fun SettingsCard(title: String, @StringRes preferenceId: Int) {
    val context = LocalContext.current
    val settingEnabled = remember {
        mutableStateOf(getSetting(context, preferenceId)) }

    OptionsCard(
        title = {
            Text(title)
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
        SettingsCard("Load files from /test", R.string.preference_load_testing)
    }
}

@Preview(showSystemUi = true)
@Composable
fun DefaultPreview() {
    GeodeLauncherTheme {
        SettingsScreen(null)
    }
}