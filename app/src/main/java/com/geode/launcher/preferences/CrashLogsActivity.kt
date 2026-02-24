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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
                crashViewModel.removeFile(crashDump.filename)
            }) {
                Icon(
                    painterResource(R.drawable.icon_delete),
                    contentDescription = stringResource(R.string.application_crashes_delete_one)
                )
            }
        }
    }
}

@Composable
fun CrashLogsScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    crashViewModel: CrashViewModel = viewModel(factory = CrashViewModel.Factory)
) {
    DirectoryListingScreen(
        onBackPressedDispatcher,
        crashViewModel,
        titleId = R.string.title_activity_crash_logs,
        noneAvailableId = R.string.application_crashes_no_dumps,
        additionalOptions = { onDismiss ->
            val developerMode by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.DEVELOPER_MODE)
            if (developerMode) {
                if (crashViewModel.hasIndicator) {
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
                            onDismiss()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.icon_skull),
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(stringResource(R.string.application_crashes_create_indicator))
                        }, onClick = {
                            crashViewModel.createIndicator()
                            onDismiss()
                        }
                    )
                }
            }
        }
    ) { line, shape ->
        CrashCard(
            line,
            crashViewModel,
            modifier = Modifier.clip(shape)
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun CrashLogsPreview() {
    GeodeLauncherTheme {
        CrashLogsScreen(null)
    }
}
