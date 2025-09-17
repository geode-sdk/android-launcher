package com.geode.launcher.preferences

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.R
import com.geode.launcher.log.LogLine
import com.geode.launcher.log.LogPriority
import com.geode.launcher.log.LogViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.launch

class ApplicationLogsActivity : ComponentActivity() {
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
                        ApplicationLogsScreen(onBackPressedDispatcher)
                    }
                }
            }
        }
    }
}

@Composable
fun getLogColor(priority: LogPriority): Color {
    val isDark = LocalTheme.current != Theme.LIGHT

    // i just copied these from my ide tbh
    return if (isDark) {
        when (priority) {
            LogPriority.ERROR -> Color(0xff_df_51_51)
            LogPriority.WARN -> Color(0xff_bc_b5_00)
            LogPriority.FATAL -> Color(0xff_b8_b6_fc)
            LogPriority.DEBUG -> Color(0xff_aa_aa_aa)
            LogPriority.VERBOSE -> Color(0xff_77_77_77)
            else -> Color.Unspecified
        }
    } else {
        when (priority) {
            LogPriority.ERROR -> Color(0xff_df_51_51)
            LogPriority.WARN -> Color(0xff_ea_b7_00)
            LogPriority.FATAL -> Color(0xff_a1_42_f4)
            LogPriority.DEBUG -> Color(0xff_77_77_77)
            LogPriority.VERBOSE -> Color(0xff_aa_aa_aa)
            else -> Color.Unspecified
        }
    }
}

@Composable
fun SelectLogLevelDialog(
    onDismissRequest: () -> Unit,
    onSelect: (LogPriority) -> Unit,
    initialValue: LogPriority
) {
    SelectDialog(
        title = stringResource(R.string.application_logs_level_dialog_name),
        onDismissRequest = onDismissRequest,
        onSelect = { onSelect(it) },
        initialValue = initialValue,
    ) {
        (LogPriority.VERBOSE..LogPriority.FATAL).reversed().forEach {
            SelectOption(
                name = it.name.lowercase().replaceFirstChar { c -> c.uppercaseChar() },
                value = it
            )
        }
    }
}

@Composable
fun LogCard(logLine: LogLine, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboardManager.setText(AnnotatedString(logLine.asSimpleString))
                    }
                )
            },
    ) {
        Text(
            "[${logLine.priority.toChar()}] ${logLine.formattedTime}",
            style = Typography.labelSmall,
            color = getLogColor(logLine.priority),
            fontFamily = FontFamily.Monospace
        )

        Text(
            "[${logLine.tag}]: ${logLine.messageTrimmed}",
            fontFamily = FontFamily.Monospace,
            style = Typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationLogsScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    logViewModel: LogViewModel = viewModel()
) {
    val logLines = logViewModel.lineState
    val isLoading by logViewModel.isLoading.collectAsState()

    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    var showMoreOptions by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val logs = logViewModel.getLogData()

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(logs)
                    }
                }

                Toast.makeText(context, R.string.application_logs_export_completed, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    LaunchedEffect(logLines.size) {
        listState.scrollToItem(logLines.size)
    }

    var showLogLevelSelect by remember { mutableStateOf(false) }
    if (showLogLevelSelect) {
        SelectLogLevelDialog(
            onDismissRequest = { showLogLevelSelect = false },
            onSelect = {
                logViewModel.logLevel = it
                showLogLevelSelect = false
            },
            initialValue = logViewModel.logLevel
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_icon_alt)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                saveLauncher.launch(context.getString(R.string.application_logs_default_filename))
                            }
                        }, enabled = !logLines.isEmpty()) {
                            Icon(
                                painterResource(R.drawable.icon_save),
                                contentDescription = stringResource(R.string.application_logs_export),
                            )
                        }
                        IconButton(onClick = { showMoreOptions = !showMoreOptions }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.application_logs_more)
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreOptions,
                            onDismissRequest = { showMoreOptions = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.icon_bug_report),
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = {
                                    if (logViewModel.filterCrashes) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = stringResource(R.string.application_logs_crash_only_enabled_alt)
                                        )
                                    }
                                },
                                text = {
                                    Text(stringResource(R.string.application_logs_crash_only))
                                }, onClick = {
                                    logViewModel.toggleCrashBuffer()
                                    showMoreOptions = false
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.icon_filter_list),
                                        contentDescription = null,
                                    )
                                },
                                text = {
                                    Text(stringResource(R.string.application_logs_level))
                                },
                                onClick = {
                                    showLogLevelSelect = true
                                    showMoreOptions = false
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.icon_delete),
                                        contentDescription = null
                                    )
                                },
                                text = {
                                    Text(stringResource(R.string.application_logs_delete))
                                }, onClick = {
                                    logViewModel.clearLogs()
                                    showMoreOptions = false
                                }
                            )
                        }
                    },
                    title = {
                        Text(
                            stringResource(R.string.title_activity_application_logs),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            Modifier
                .padding(innerPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
            state = listState
        ) {
            if (!isLoading && logLines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.application_logs_no_logs),
                        textAlign = TextAlign.Center,
                        style = Typography.bodyLarge,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            items(
                logLines
            ) { line ->
                LogCard(line)
                HorizontalDivider()
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun LogsPreview() {
    GeodeLauncherTheme {
        ApplicationLogsScreen(null)
    }
}