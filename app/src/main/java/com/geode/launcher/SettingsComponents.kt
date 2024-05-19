package com.geode.launcher

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.PreferenceUtils


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
            optionsCount = 0..maxVal
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
                trailingIcon = {
                    if (enteredValue.isNotEmpty()) {
                        IconButton(onClick = { enteredValue = "" }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.preference_text_clear)
                            )
                        }
                    }
                }
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
fun <T> SelectDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (T) -> Unit,
    initialValue: T,
    toLabel: @Composable (T) -> String,
    optionsCount: Iterable<T>,
) {
    var selectedValue by remember { mutableStateOf(initialValue) }

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
                optionsCount.forEach { id ->
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
                preferenceKey = PreferenceUtils.Key.BLACK_BACKGROUND
            )
            SettingsCard(
                title = "Testing option 2",
                preferenceKey = PreferenceUtils.Key.LOAD_AUTOMATICALLY
            )
        }
    }
}
