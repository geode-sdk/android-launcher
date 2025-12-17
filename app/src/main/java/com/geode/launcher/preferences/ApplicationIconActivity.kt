package com.geode.launcher.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.utils.PreferenceUtils
import androidx.compose.ui.semantics.Role
import com.geode.launcher.utils.ApplicationIcon
import com.geode.launcher.utils.ApplicationIconDetails
import com.geode.launcher.utils.IconUtils
import com.geode.launcher.utils.adaptiveIconPainterResource

class ApplicationIconActivity : ComponentActivity() {
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
                        ApplicationIconScreen(onBackPressedDispatcher)
                    }
                }
            }
        }
    }
}

fun onSelectIcon(context: Context, selectedIcon: ApplicationIconDetails) {
    val packageManager = context.packageManager

    try {
        ApplicationIcon.entries.forEach {
            val iconData = IconUtils.getIconDetails(it)

            packageManager.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.${iconData.component}"),
                if (iconData.id == selectedIcon.id)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, R.string.application_icon_set_failed, Toast.LENGTH_SHORT).show()
    }

    PreferenceUtils.get(context).setString(PreferenceUtils.Key.SELECTED_ICON, selectedIcon.id)
}

@Composable
fun IconCard(iconData: ApplicationIconDetails, selected: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(modifier)
            .selectable(
                selected = selected,
                onClick = {
                    onSelectIcon(context, iconData)
                },
                role = Role.RadioButton
            )
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                adaptiveIconPainterResource(iconData.iconId),
                contentDescription = null
            )
            Text(stringResource(iconData.nameId))
        }

        RadioButton(
            selected = selected,
            onClick = null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationIconScreen(
    onBackPressedDispatcher: OnBackPressedDispatcher?,
) {
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
                    title = {
                        Text(
                            stringResource(R.string.title_activity_application_icon),
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
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            val selectedIcon by PreferenceUtils.useStringPreference(PreferenceUtils.Key.SELECTED_ICON)

            Column(Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
            ) {
                ApplicationIcon.entries.forEach {
                    val iconData = IconUtils.getIconDetails(it)

                    IconCard(iconData, selectedIcon == iconData.id)
                }

            }

            Spacer(Modifier.height(4.dp))
        }
    }
}
