package com.geode.geodelauncher

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackPressedDispatcher: OnBackPressedDispatcher?) {
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
                OptionsCard(R.string.preference_load_testing_name, R.string.preference_load_testing)
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
fun OptionsCard(@StringRes title: Int, @StringRes preferenceId: Int) {
    val context = LocalContext.current
    val settingEnabled = remember {
        mutableStateOf(getSetting(context, preferenceId)) }
    Row(
        Modifier.fillMaxWidth()
            .height(64.dp)
            .toggleable(
                value = settingEnabled.value,
                onValueChange = { settingEnabled.value = toggleSetting(context, preferenceId) },
                role = Role.Switch,
            )
            .padding(horizontal = 16.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Text(context.getString(title))
        Switch(checked = settingEnabled.value, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
fun OptionsCardPreview() {
    GeodeLauncherTheme {
        OptionsCard(R.string.preference_load_testing_name, R.string.preference_load_testing)
    }
}

@Preview(showSystemUi = true)
@Composable
fun DefaultPreview() {
    GeodeLauncherTheme {
        SettingsScreen(null)
    }
}