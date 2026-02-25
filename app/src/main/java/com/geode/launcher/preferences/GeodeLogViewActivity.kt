package com.geode.launcher.preferences

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.R
import com.geode.launcher.log.GeodeLogsViewModel
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.ui.theme.robotoMonoFamily
import com.geode.launcher.utils.PreferenceUtils


class GeodeLogViewActivity : ComponentActivity() {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeodeLogView(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
    filename: String,
    geodeLogsViewModel: GeodeLogsViewModel = viewModel(factory = GeodeLogsViewModel.Factory)
) {
    var loadingFile by remember { mutableStateOf(true) }
    var description by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(filename) {
        loadingFile = true
        description = geodeLogsViewModel.getFileText(filename)
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
                            filename,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    scrollBehavior = scrollBehavior,
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