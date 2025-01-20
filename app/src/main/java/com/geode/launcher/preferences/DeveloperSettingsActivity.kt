package com.geode.launcher.preferences

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.utils.LabelledText
import com.geode.launcher.utils.PreferenceUtils

class DeveloperSettingsActivity : ComponentActivity() {
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
                        DeveloperSettingsScreen(onBackPressedDispatcher)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(onBackPressedDispatcher: OnBackPressedDispatcher?) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
                    title = {
                        Text(
                            stringResource(R.string.title_activity_developer_settings),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            val developerMode by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.DEVELOPER_MODE)

            SettingsCard(
                title = stringResource(R.string.preference_developer_mode),
                preferenceKey = PreferenceUtils.Key.DEVELOPER_MODE,
            )

            InlineText(
                stringResource(R.string.preference_developer_mode_about)
            )
            
            HorizontalDivider()

            if (developerMode) {
                OptionsGroup(title = stringResource(R.string.preference_category_gameplay)) {
                    SettingsStringCard(
                        title = stringResource(R.string.preference_launch_arguments_name),
                        dialogTitle = stringResource(R.string.preference_launch_arguments_set_title),
                        preferenceKey = PreferenceUtils.Key.LAUNCH_ARGUMENTS,
                        filterInput = { it.filter { c ->
                            // if only there was a better way to define this!
                            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890!@#$%^&*(){}<>[]?:;'\"~`-_+=\\| ".contains(c)
                        }}
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        SettingsCard(
                            title = stringResource(R.string.preference_force_hrr),
                            preferenceKey = PreferenceUtils.Key.FORCE_HRR,
                        )
                    }
                    /*
                    SettingsCard(
                        title = stringResource(R.string.preference_override_exceptions_name),
                        description = stringResource(R.string.preference_override_exceptions_description),
                        preferenceKey = PreferenceUtils.Key.CUSTOM_SYMBOL_LIST
                    )
                    */
                }

                OptionsGroup(title = stringResource(R.string.preference_category_updater)) {
                    SettingsSelectCard(
                        title = stringResource(R.string.preference_release_channel_tag_name),
                        dialogTitle = stringResource(R.string.preference_release_channel_select),
                        preferenceKey = PreferenceUtils.Key.RELEASE_CHANNEL_TAG,
                        options = linkedMapOf(
                            0 to stringResource(R.string.preference_release_channel_stable),
                            1 to stringResource(R.string.preference_release_channel_beta),
                            2 to stringResource(R.string.preference_release_channel_nightly),
                        )
                    )

                    ElevatedCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        LabelledText(
                            label = stringResource(R.string.preference_developer_update_notice),
                            icon = {
                                Icon(Icons.Filled.Info, null)
                            },
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OptionsGroup(title = stringResource(R.string.preference_category_testing)) {
                    SettingsCard(
                        title = stringResource(R.string.preference_enable_redesign),
                        preferenceKey = PreferenceUtils.Key.ENABLE_REDESIGN,
                    )
                }
            }
        }
    }
}