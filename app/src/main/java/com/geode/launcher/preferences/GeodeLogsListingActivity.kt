package com.geode.launcher.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.log.GeodeLog
import com.geode.launcher.log.GeodeLogsViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.ui.theme.robotoMonoFamily
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import java.io.File

class GeodeLogsListingActivity : ComponentActivity() {
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
                        GeodeLogsListingScreen(onBackPressedDispatcher)
                    }
                }
            }
        }
    }
}

fun shareLog(context: Context, filename: String) {
    val baseDirectory = LaunchUtils.getBaseDirectory(context)
    val crashPath = File(LaunchUtils.getGeodeLogsDirectory(context), filename)

    if (!crashPath.exists()) {
        Toast.makeText(context, R.string.launcher_error_export_missing, Toast.LENGTH_SHORT).show()
        return
    }

    val providerLocation = crashPath.toRelativeString(baseDirectory)
    val documentsPath = "${UserDirectoryProvider.ROOT}${providerLocation}"

    val uri = DocumentsContract.buildDocumentUri("${context.packageName}.user", documentsPath)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "text/plain"
    }

    try {
        context.startActivity(shareIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_activity_found, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun GeodeLogCard(geodeLog: GeodeLog, geodeLogsViewModel: GeodeLogsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = 8.dp)
            .height(64.dp)
            .then(modifier)
            .clickable {
                val launchIntent = Intent(context, GeodeLogViewActivity::class.java).run {
                    putExtra(GeodeLogViewActivity.EXTRA_LOG_VIEW_FILENAME, geodeLog.filename)
                }
                context.startActivity(launchIntent)
            }
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxHeight().offset(x = 12.dp).weight(0.7f)
        ) {
            Text(
                geodeLog.filename,
                fontFamily = robotoMonoFamily,
                style = MaterialTheme.typography.titleSmall,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )

            val context = LocalContext.current
            val formattedSize = remember(geodeLog.fileSize) {
                Formatter.formatShortFileSize(context, geodeLog.fileSize)
            }
            Text(formattedSize, style = MaterialTheme.typography.labelMedium)
        }

        Row(modifier = Modifier.weight(0.3f), horizontalArrangement = Arrangement.End) {
            val context = LocalContext.current
            IconButton(onClick = {
                shareLog(context, geodeLog.filename)
            }) {
                Icon(
                    painterResource(R.drawable.icon_share),
                    contentDescription = stringResource(R.string.application_logs_share)
                )
            }

            IconButton(onClick = {
                geodeLogsViewModel.removeFile(geodeLog.filename)
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
fun GeodeLogsListingScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    geodeLogsViewModel: GeodeLogsViewModel = viewModel(factory = GeodeLogsViewModel.Factory)
) {
    DirectoryListingScreen(
        onBackPressedDispatcher,
        geodeLogsViewModel,
        titleId = R.string.title_activity_geode_logs,
        noneAvailableId = R.string.application_geode_logs_no_logs,
    ) { line, shape ->
        GeodeLogCard(
            line,
            geodeLogsViewModel,
            modifier = Modifier.clip(shape)
        )
    }
}
