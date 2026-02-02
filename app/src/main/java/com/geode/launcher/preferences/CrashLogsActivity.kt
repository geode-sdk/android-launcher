package com.geode.launcher.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import java.text.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.log.CrashDump
import com.geode.launcher.log.CrashViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.ui.theme.robotoMonoFamily
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import java.io.File
import java.util.Date
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

class CrashLogsActivity : ComponentActivity() {
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
                        CrashLogsScreen(onBackPressedDispatcher)
                    }
                }
            }
        }
    }
}

fun shareCrash(context: Context, filename: String) {
    val baseDirectory = LaunchUtils.getBaseDirectory(context)
    val crashPath = File(LaunchUtils.getCrashDirectory(context), filename)

    if (!crashPath.exists()) {
        Toast.makeText(context, R.string.launcher_error_export_missing, Toast.LENGTH_SHORT).show()
        return
    }

    val providerLocation = crashPath.toRelativeString(baseDirectory)
    val documentsPath = "${UserDirectoryProvider.ROOT}${providerLocation}"

    val uri = DocumentsContract.buildDocumentUri("${context.packageName}.user", documentsPath)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "application/octet-stream"
    }

    try {
        context.startActivity(shareIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_activity_found, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun CrashCard(crashDump: CrashDump, crashViewModel: CrashViewModel, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 1.dp, horizontal = 8.dp)
        .height(64.dp)
        .then(modifier)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxHeight().offset(x = 12.dp).weight(0.7f)
        ) {
            Text(
                crashDump.filename,
                fontFamily = robotoMonoFamily,
                style = MaterialTheme.typography.titleSmall,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

            val formattedLastModified = remember {
                DateFormat.getDateTimeInstance()
                    .format(
                        Date.from(crashDump.lastModified.toJavaInstant())
                    )

            }
            Text(formattedLastModified, style = MaterialTheme.typography.labelMedium)
        }

        Row(modifier = Modifier.weight(0.3f), horizontalArrangement = Arrangement.End) {
            val context = LocalContext.current
            IconButton(onClick = {
                shareCrash(context, crashDump.filename)
            }) {
                Icon(
                    painterResource(R.drawable.icon_share),
                    contentDescription = stringResource(R.string.application_logs_share)
                )
            }

            IconButton(onClick = {
                crashViewModel.removeCrash(crashDump.filename)
            }) {
                Icon(
                    painterResource(R.drawable.icon_delete),
                    contentDescription = stringResource(R.string.application_crashes_delete_one)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogsScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    crashViewModel: CrashViewModel = viewModel(factory = CrashViewModel.Factory)
) {
    val logLines = crashViewModel.lineState
    val isLoading by crashViewModel.isLoading.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showMoreOptions by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
                            Icon(
                                painterResource(R.drawable.icon_arrow_back),
                                contentDescription = stringResource(R.string.back_icon_alt)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMoreOptions = !showMoreOptions }) {
                            Icon(
                                painterResource(R.drawable.icon_more_vert),
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
                                        painterResource(R.drawable.icon_delete),
                                        contentDescription = null
                                    )
                                },
                                text = {
                                    Text(stringResource(R.string.application_logs_delete))
                                }, onClick = {
                                    crashViewModel.clearCrashes()
                                    showMoreOptions = false
                                }
                            )

                            val developerMode by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.DEVELOPER_MODE)
                            if (developerMode) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.icon_remove_selection),
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(stringResource(R.string.application_crashes_clear_indicator))
                                    }, onClick = {
                                        crashViewModel.clearIndicator()
                                        showMoreOptions = false
                                    },
                                    enabled = crashViewModel.hasIndicator
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            stringResource(R.string.title_activity_crash_logs),
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
        ) {
            if (!isLoading && logLines.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.application_crashes_no_dumps),
                        textAlign = TextAlign.Center,
                        style = Typography.bodyLarge,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
            }

            val itemsSize = logLines.size

            itemsIndexed(
                logLines,
                key = { index, line -> line.filename }
            ) { index, line ->
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) 16.dp else 4.dp,
                    topEnd = if (index == 0) 16.dp else 4.dp,
                    bottomStart = if (index + 1 == itemsSize) 16.dp else 4.dp,
                    bottomEnd = if (index + 1 == itemsSize) 16.dp else 4.dp
                )

                CrashCard(
                    line,
                    crashViewModel,
                    modifier = Modifier.clip(shape)
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun CrashLogsPreview() {
    GeodeLauncherTheme {
        CrashLogsScreen(null)
    }
}