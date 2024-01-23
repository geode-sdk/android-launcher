package com.geode.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

            CompositionLocalProvider(LocalTheme provides theme) {
                GeodeLauncherTheme(theme = theme, blackBackground = backgroundOption) {
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
            LogPriority.ERROR -> Color(0xffdf5151)
            LogPriority.WARN -> Color(0xffbcb500)
            LogPriority.FATAL -> Color(0xffb8b6fc)
            else -> Color.Unspecified
        }
    } else {
        when (priority) {
            LogPriority.ERROR -> Color(0xffdf5151)
            LogPriority.WARN -> Color(0xffeab700)
            LogPriority.FATAL -> Color(0xffa142f4)
            else -> Color.Unspecified
        }
    }
}

fun copyLogText(context: Context, logMessage: String) {
    val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(
        ClipData.newPlainText(
            context.getString(R.string.application_log_copy_label),
            logMessage
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

@Composable
fun LogCard(logLine: LogLine, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        copyLogText(context, logLine.asSimpleString)
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
            "[${logLine.tag}]: ${logLine.message}",
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_icon_alt)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (!logLines.isEmpty()) {
                                coroutineScope.launch {
                                    val data = logViewModel.getLogData()

                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, data)
                                        type = "text/plain"
                                    }

                                    val shareIntent = Intent.createChooser(sendIntent, null)

                                    context.startActivity(shareIntent)
                                }
                            }
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.application_logs_share)
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
                                        painterResource(R.drawable.icon_save),
                                        contentDescription = stringResource(R.string.application_logs_export)
                                    )
                                },
                                text = {
                                    Text(stringResource(R.string.application_logs_export))
                                }, onClick = {
                                    if (!logLines.isEmpty()) {
                                        saveLauncher.launch(context.getString(R.string.application_logs_default_filename))
                                    }
                                    showMoreOptions = false
                                }
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.icon_delete),
                                        contentDescription = stringResource(R.string.application_logs_delete)
                                    )
                                },
                                text = {
                                    Text(stringResource(R.string.application_logs_delete))
                                }, onClick = {
                                    logViewModel.clearLogs()
                                    onBackPressedDispatcher?.onBackPressed()
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
                Divider()
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