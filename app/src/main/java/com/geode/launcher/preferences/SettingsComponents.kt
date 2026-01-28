package com.geode.launcher.preferences

import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.geode.launcher.R
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.LabelledText
import com.geode.launcher.utils.PreferenceUtils
import com.geode.launcher.utils.Profile
import com.geode.launcher.utils.ProfileManager
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.exitProcess


fun toggleSetting(context: Context, preferenceKey: PreferenceUtils.Key): Boolean {
    val preferences = PreferenceUtils.get(context)

    return preferences.toggleBoolean(preferenceKey)
}

fun getSetting(context: Context, preferenceKey: PreferenceUtils.Key): Boolean {
    val preferences = PreferenceUtils.get(context)

    return preferences.getBoolean(preferenceKey)
}

@Composable
fun OptionsGroup(title: String? = null, content: @Composable () -> Unit) {
    Column {
        if (title != null) {
            Text(
                title,
                style = Typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        Column(modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
        ) {
            content()
        }
    }
}

@Composable
fun SettingsSelectCard(
    title: String,
    dialogTitle: String,
    preferenceKey: PreferenceUtils.Key,
    options: Map<Int, String>,
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
        Text(options[preferenceValue] ?: preferenceValue.toString())
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
        ) {
            options.forEach { (k, v) ->
                SelectOption(name = v, value = k)
            }
        }
    }
}

@Composable
fun SettingsStringSelectCard(
    title: String,
    dialogTitle: String,
    preferenceKey: PreferenceUtils.Key,
    options: Map<String, String>,
    extraSelectBehavior: ((String?) -> Unit)? = null,
    description: String? = null,
) {
    val preferenceValue by PreferenceUtils.useStringPreference(preferenceKey)

    var showDialog by remember { mutableStateOf(false) }

    OptionsCard(
        title = { OptionsTitle(title = title, description = description) },
        modifier = Modifier
            .clickable(
                onClick = {
                    showDialog = true
                },
                role = Role.Button
            )
    ) {
        Text(options[preferenceValue] ?: preferenceValue ?: "")
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
                    .setString(preferenceKey, selected)
                extraSelectBehavior?.invoke(selected)
            },
            initialValue = preferenceValue,
        ) {
            options.forEach { (k, v) ->
                SelectOption(name = v, value = k)
            }
        }
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

    BaseDialog(
        onDismissRequest = { onDismissRequest() },
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = enteredValue,
                    onValueChange = {
                        enteredValue = filterInput?.invoke(it) ?: it
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false
                    ),
                    trailingIcon = {
                        if (enteredValue.isNotEmpty()) {
                            IconButton(onClick = { enteredValue = "" }) {
                                Icon(
                                    painterResource(R.drawable.icon_close_small),
                                    contentDescription = stringResource(R.string.preference_text_clear)
                                )
                            }
                        }
                    }
                )
            }

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
fun RangeDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit,
    labelSuffix: String,
    initialValue: Int,
    range: IntRange,
    scale: Int,
    step: Int,
    children: @Composable () -> Unit
) {
    var enteredValue by remember {
        mutableFloatStateOf(initialValue / scale.toFloat())
    }

    BaseDialog(
        onDismissRequest = { onDismissRequest() },
        title = title,
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val precision = log10(scale.toFloat()).roundToInt()

                Slider(
                    value = enteredValue,
                    onValueChange = { enteredValue = it },
                    valueRange = (range.first.toFloat()/scale)..(range.last.toFloat()/scale),
                    steps = ((range.last - range.first) - 1) / step,
                    modifier = Modifier.weight(1.0f)
                )

                Text("%.${precision}f$labelSuffix".format(enteredValue))
            }

            children()
        },
        confirmButton = {
            TextButton(onClick = { onSelect((enteredValue * scale).roundToInt()) }) {
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
fun FrameRateDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit,
    initialValue: Int,
    maxFrameRate: Int,
) {
    var enteredValue by remember {
        mutableStateOf(
            if (initialValue == 0) maxFrameRate.toString()
            else initialValue.toString()
        )
    }

    val minFrameRate = 5
    val currentValue = enteredValue.toIntOrNull()
    val maximumReached = currentValue != null && currentValue > maxFrameRate
    val minimumReached = currentValue != null && currentValue < minFrameRate

    BaseDialog(
        onDismissRequest = { onDismissRequest() },
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        val prevValue = max((currentValue ?: 0) - 5, minFrameRate)
                        enteredValue = prevValue.toString()
                    }, enabled = currentValue == null || currentValue > minFrameRate) {
                        Icon(
                            painterResource(R.drawable.icon_remove),
                            contentDescription = stringResource(R.string.preference_limit_framerate_subtract)
                        )
                    }

                    Spacer(Modifier.size(8.dp))

                    OutlinedTextField(
                        value = enteredValue,
                        onValueChange = {
                            enteredValue = it
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        suffix = {
                            Text(stringResource(R.string.preference_limit_framerate_suffix))
                        },
                        label = {
                            Text(stringResource(R.string.preference_limit_framerate_label))
                        },
                        isError = minimumReached || maximumReached,
                        modifier = Modifier.weight(1.0f),
                    )

                    Spacer(Modifier.size(8.dp))

                    IconButton(onClick = {
                        val nextValue = min((currentValue ?: 0) + 5, maxFrameRate)
                        enteredValue = nextValue.toString()
                    }, enabled = currentValue == null || currentValue < maxFrameRate) {
                        Icon(
                            painterResource(R.drawable.icon_add),
                            contentDescription = stringResource(R.string.preference_limit_framerate_add)
                        )
                    }

                    IconButton(onClick = { onSelect(0) }) {
                        Icon(
                            painterResource(R.drawable.icon_delete),
                            contentDescription = stringResource(R.string.preference_limit_framerate_reset)
                        )
                    }
                }


                if (minimumReached) {
                    Spacer(Modifier.size(8.dp))

                    Text(
                        stringResource(R.string.preference_limit_framerate_error_min, minFrameRate),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else if (maximumReached) {
                    Spacer(Modifier.size(8.dp))

                    Text(
                        stringResource(
                            R.string.preference_limit_framerate_error_max,
                            maxFrameRate
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // automatically disable if already max
                    if (currentValue == maxFrameRate) {
                        onSelect(0)
                    } else {
                        onSelect(currentValue ?: 0)
                    }
                },
                enabled = !minimumReached && !maximumReached && currentValue != null
            ) {
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
fun SettingsFPSCard(
    title: String,
    dialogTitle: String,
    preferenceKey: PreferenceUtils.Key,
    maxFrameRate: Int,
    description: String? = null,
) {
    var preferenceValue by PreferenceUtils.useIntPreference(preferenceKey)

    var showDialog by remember { mutableStateOf(false) }

    OptionsCard(
        title = { OptionsTitle(title = title, description = description) },
        modifier = Modifier
            .clickable(
                onClick = {
                    showDialog = true
                },
                role = Role.Button
            )
    ) {
        if (preferenceValue == 0) {
            Text(stringResource(R.string.preference_limit_framerate_default))
        } else {
            Text(stringResource(R.string.preference_limit_framerate_value, preferenceValue))
        }
    }

    if (showDialog) {
        FrameRateDialog(
            title = dialogTitle,
            onDismissRequest = { showDialog = false },
            onSelect = {
                preferenceValue = it
                showDialog = false
            },
            initialValue = preferenceValue,
            maxFrameRate = maxFrameRate
        )
    }
}

@Composable
fun SettingsRangeCard(
    title: String,
    dialogTitle: String,
    preferenceKey: PreferenceUtils.Key,
    labelSuffix: String,
    range: IntRange,
    scale: Int,
    step: Int,
    description: String? = null,
    children: @Composable () -> Unit = {}
) {
    var preferenceValue by PreferenceUtils.useIntPreference(preferenceKey)

    var showDialog by remember { mutableStateOf(false) }

    OptionsCard(
        title = {
            OptionsTitle(title = title, description = description)
        },
        modifier = Modifier
            .clickable(
                onClick = {
                    showDialog = true
                },
                role = Role.Button
            )
    ) {
        val precision = log10(scale.toFloat()).roundToInt()
        val scaledValue = preferenceValue / scale.toFloat()

        Text("%.${precision}f$labelSuffix".format(scaledValue))
    }

    if (showDialog) {
        RangeDialog(
            title = dialogTitle,
            onDismissRequest = { showDialog = false },
            onSelect = {
                preferenceValue = it
                showDialog = false
            },
            initialValue = preferenceValue,
            range = range,
            scale = scale,
            labelSuffix = labelSuffix,
            step = step,
            children = children,
        )
    }
}

internal val LocalSelectValue = compositionLocalOf<Any> { 0 }
internal val LocalSelectSetValue = staticCompositionLocalOf<(Any) -> Unit> { {} }

@Composable
fun <T> SelectOption(name: String, value: T, enabled: Boolean = true, leadingContent: @Composable (Boolean) -> Unit = {}) {
    val currentValue = LocalSelectValue.current
    val setValue = LocalSelectSetValue.current

    val isSelected = currentValue.equals(value)

    // do not give the row or column padding!! it messes up the selection effect
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { setValue(value as Any) },
                role = Role.RadioButton,
                enabled = enabled
            )
            .padding(horizontal = 12.dp)
    ) {
        Row(modifier = Modifier.weight(1.0f, true), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = { setValue(value as Any) },
                enabled = enabled
            )
            Text(name, style = Typography.bodyMedium)
        }

        leadingContent(isSelected)
    }
}

@Composable
fun ProfileCreateDialog(onDismissRequest: () -> Unit) {
    var enteredValue by remember { mutableStateOf("") }

    val filename = enteredValue.take(16)
        .lowercase()
        .map {
            if ("qwertyuiopasdfghjklzxcvbnm1234567890-_.".contains(it))
                it else '_'
        }
        .joinToString("")


    val context = LocalContext.current

    val minimumReached = enteredValue.isEmpty() || filename.isEmpty()

    val profileManager = ProfileManager.get(context)
    val currentProfiles = remember {
        profileManager.getProfiles().map { it.path }.toSet()
    }

    val isDuplicate = currentProfiles.contains(filename)

    BaseDialog(
        onDismissRequest = { onDismissRequest() },
        title = stringResource(R.string.preference_profiles_create),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                OutlinedTextField(
                    value = enteredValue,
                    onValueChange = {
                        enteredValue = it
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                    ),
                    label = {
                        Text(stringResource(R.string.preference_profiles_create_name))
                    },
                    isError = isDuplicate,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (isDuplicate) {
                    Spacer(Modifier.size(8.dp))

                    Text(stringResource(R.string.preference_profiles_create_duplicate))
                } else if (!minimumReached) {
                    Spacer(Modifier.size(8.dp))

                    Text(stringResource(R.string.preference_profiles_create_info, filename))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ProfileManager.get(context).storeProfile(Profile(filename, enteredValue))
                    onDismissRequest()
                },
                enabled = !minimumReached && !isDuplicate
            ) {
                Text(stringResource(R.string.preference_profiles_create_action))
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
fun ProfileCreateCard() {
    var showDialog by remember { mutableStateOf(false) }

    OptionsButton(
        title = stringResource(R.string.preference_profiles_create),
        icon = {
            Icon(painterResource(R.drawable.icon_person_add), contentDescription = null)
        }
    ) {
        showDialog = true
    }

    if (showDialog) {
        ProfileCreateDialog(onDismissRequest = {
            showDialog = false
        })
    }
}

@Composable
fun ProfileSelectCard() {
    val context = LocalContext.current
    val profileManager = ProfileManager.get(context)

    val currentProfileId = remember {
        profileManager.getCurrentProfile()
    }

    var currentProfileName = remember {
        val savedProfile = currentProfileId
        if (savedProfile == null) {
            "Default"
        } else {
            profileManager.getProfile(savedProfile)
                ?.name ?: savedProfile
        }
    }

    val clearedProfiles = remember { mutableStateListOf<String>() }

    val profileList by profileManager.storedProfiles.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    OptionsButton(
        title = stringResource(R.string.preference_profiles_select),
        description = stringResource(R.string.preference_profiles_current, currentProfileName)
    ) {
        showDialog = true
    }

    val activity = LocalActivity.current
    if (showDialog) {
        SelectDialog(
            title = stringResource(R.string.preference_profiles_select),
            onDismissRequest = {
                showDialog = false
            },
            onSelect = {
                showDialog = false

                val selectedProfileId = it.takeIf { it.isNotEmpty() }

                if (clearedProfiles.isNotEmpty()) {
                    profileManager.deleteProfiles(clearedProfiles)
                }

                if (currentProfileId != selectedProfileId) {
                    profileManager
                        .setCurrentProfile(selectedProfileId)

                    activity?.run {
                        packageManager.getLaunchIntentForPackage(packageName)?.also {
                            val mainIntent = Intent.makeRestartActivityTask(it.component)
                            startActivity(mainIntent)
                            exitProcess(0)
                        }
                    }
                }
            },
            initialValue = currentProfileId ?: "",
        ) {
            SelectOption("Default", "")

            profileList.forEach { profile ->
                val path = profile.path

                SelectOption(
                    stringResource(R.string.preference_profiles_select_value, profile.name, path),
                    path,
                    enabled = !clearedProfiles.contains(path),
                    leadingContent = { selected ->
                        IconButton(onClick = {
                            if (clearedProfiles.contains(path)) {
                                clearedProfiles.remove(path)
                            } else {
                                clearedProfiles.add(path)
                            }
                        }, enabled = !selected) {
                            if (clearedProfiles.contains(path)) {
                                Icon(
                                    painterResource(R.drawable.icon_undo),
                                    contentDescription = stringResource(R.string.preference_profiles_delete_undo_alt)
                                )
                            } else {
                                Icon(
                                    painterResource(R.drawable.icon_delete),
                                    contentDescription = stringResource(R.string.preference_profiles_delete_alt)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BaseDialog(
    title: String,
    onDismissRequest: () -> Unit,
    dismissButton: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    content: @Composable (ColumnScope.() -> Unit),
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            // styling a dialog is actually a little hard if you're doing what i'm doing
            // maybe there's a better way to make these padding values...
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    title,
                    style = Typography.titleLarge,
                    modifier = Modifier.padding(
                        start = 28.dp,
                        top = 24.dp,
                        bottom = 12.dp
                    )
                )

                content()

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
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun <T> SelectDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (T) -> Unit,
    initialValue: T,
    options: @Composable () -> Unit,
) {
    val (selectedValue, setSelectedValue) = remember { mutableStateOf(initialValue) }

    BaseDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.message_box_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selectedValue) }) {
                Text(stringResource(R.string.message_box_accept))
            }
        }
    ) {
        CompositionLocalProvider(
            LocalSelectValue provides (selectedValue as Any),
            LocalSelectSetValue provides ({
                @Suppress("UNCHECKED_CAST")
                setSelectedValue(it as T)
            })
        ) {
            options()
        }
    }
}

@Composable
fun OptionsButton(title: String, description: String? = null, icon: (@Composable () -> Unit)? = null, displayInline: Boolean = false, onClick: () -> Unit) {
    OptionsCard(
        title = {
            OptionsTitle(
                title = title,
                description = description.takeIf { !displayInline },
                icon = icon
            )
        },
        modifier = Modifier
            .clickable(onClick = onClick, role = Role.Button),
        wrapContent = true
    ) {
        if (displayInline && description != null) {
            Text(description, textAlign = TextAlign.End)
        }
    }
}

@Composable
fun OptionsLabel(title: String, description: String? = null, icon: (@Composable () -> Unit)? = null, displayInline: Boolean = false) {
    OptionsCard(
        title = {
            OptionsTitle(
                title = title,
                description = description.takeIf { !displayInline },
                icon = icon
            )
        },
        wrapContent = true
    ) {
        if (displayInline && description != null) {
            Text(description, textAlign = TextAlign.End)
        }
    }
}

@Composable
fun SettingsCard(title: String, description: String? = null, icon: (@Composable () -> Unit)? = null, asCard: Boolean = true, preferenceKey: PreferenceUtils.Key) {
    val context = LocalContext.current
    val settingEnabled = remember {
        mutableStateOf(getSetting(context, preferenceKey))
    }

    OptionsCard(
        title = {
            OptionsTitle(
                title = title,
                description = description,
                icon = icon
            )
        },
        modifier = Modifier.toggleable(
            value = settingEnabled.value,
            onValueChange = { settingEnabled.value = toggleSetting(context, preferenceKey) },
            role = Role.Switch,
        ),
        backgroundColor = if (asCard)
            MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Switch(
            checked = settingEnabled.value,
            onCheckedChange = null
        )
    }
}

@Composable
fun OptionsTitle(modifier: Modifier = Modifier, title: String, description: String? = null, icon: (@Composable () -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            icon()
        }
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title)
            if (!description.isNullOrEmpty()) {
                Text(
                    description,
                    style = Typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun OptionsCard(
    modifier: Modifier = Modifier,
    wrapContent: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(modifier)
            .background(backgroundColor)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        if (!wrapContent) {
            Box(modifier = Modifier.weight(1.0f)) {
                title()
            }
            Spacer(modifier = Modifier.size(12.dp))
        } else {
            title()
        }

        content()
    }
}

@Composable
fun SafeModeDialog(onDismiss: () -> Unit, onLaunch: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.icon_shield_alert),
                contentDescription = null
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
fun InlineText(label: String, icon: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    LabelledText(label = label, icon = icon, modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp))
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
