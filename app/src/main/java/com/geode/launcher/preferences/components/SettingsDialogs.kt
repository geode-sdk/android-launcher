package com.geode.launcher.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.geode.launcher.R
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.Profile
import com.geode.launcher.utils.ProfileManager
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


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
