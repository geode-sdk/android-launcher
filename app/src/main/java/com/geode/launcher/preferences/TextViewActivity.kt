package com.geode.launcher.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.ui.theme.robotoMonoFamily
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


class TextViewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LOG_VIEW_FILENAME = "EXTRA_LOG_FILENAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        val filename = intent.getStringExtra(EXTRA_LOG_VIEW_FILENAME)
        if (filename.isNullOrEmpty()) {
            val launchIntent = Intent(this, GeodeLogsListingActivity::class.java)
            startActivity(launchIntent)

            return
        }

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
                        GeodeLogView(onBackPressedDispatcher, filename)
                    }
                }
            }
        }
    }
}

fun onShareFile(context: Context, filename: File) {
    val baseDirectory = LaunchUtils.getBaseDirectory(context, true)

    val providerLocation = filename.toRelativeString(baseDirectory)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeodeLogView(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    filename: String,
) {
    var loadingFile by remember { mutableStateOf(true) }
    var description by remember { mutableStateOf(emptyList<String>()) }

    val toRead = File(filename)

    LaunchedEffect(filename) {
        loadingFile = true

        withContext(Dispatchers.IO) {
            if (!toRead.isFile) {
                description = listOf("File does not exist!")
            } else {
                description = toRead.readLines()
            }
        }

        loadingFile = false
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
                    title = {
                        Text(
                            toRead.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        val context = LocalContext.current
                        IconButton(onClick = { onShareFile(context, toRead) }) {
                            Icon(
                                painterResource(R.drawable.icon_share),
                                contentDescription = stringResource(R.string.application_logs_share)
                            )
                        }
                    }
                )

                if (loadingFile) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { innerPadding ->
        if (!loadingFile) {
            if (description.isEmpty()) {
                Text(
                    stringResource(R.string.application_geode_logs_empty),
                    textAlign = TextAlign.Center,
                    style = Typography.bodyLarge,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            } else {
                SelectionContainer {
                    LazyColumn(modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 8.dp)
                    ) {

                        items(description) { line ->
                            Text(
                                line,
                                fontFamily = robotoMonoFamily,
                                style = Typography.bodyMedium
                            )
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}