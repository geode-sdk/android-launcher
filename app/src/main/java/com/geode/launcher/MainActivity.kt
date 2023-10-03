package com.geode.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.useCountdownTimer
import com.geode.launcher.utils.usePreference


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gdInstalled = LaunchUtils.isGeometryDashInstalled(packageManager)

        setContent {
            GeodeLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(gdInstalled)
                }
            }
        }
        if (gdInstalled) {
            intent.getBooleanExtra("restarted", false).let {
                if (it) {
                    onLaunch(this)
                }
            }
        }
    }
}

fun onLaunch(context: Context) {
    val launchIntent = Intent(context, GeometryDashActivity::class.java)
    launchIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

    context.startActivity(launchIntent)
}

fun onSettings(context: Context) {
    val launchIntent = Intent(context, SettingsActivity::class.java)
    context.startActivity(launchIntent)
}

@Composable
fun MainScreen(gdInstalled: Boolean = true) {
    val context = LocalContext.current

    val shouldAutomaticallyLaunch = usePreference(
        preferenceFileKey = R.string.preference_file_key,
        preferenceId = R.string.preference_load_automatically
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.geode_logo),
            contentDescription = context.getString(R.string.launcher_logo_alt),
            modifier = Modifier.size(136.dp, 136.dp)
        )
        Text(
            context.getString(R.string.launcher_title),
            fontSize = 32.sp,
            modifier = Modifier.padding(12.dp)
        )
        if (gdInstalled) {
            if (shouldAutomaticallyLaunch.value) {
                val countdownTimer = useCountdownTimer(
                    time = 3000,
                    onCountdownFinish = {
                        // just in case this changed async
                        if (shouldAutomaticallyLaunch.value) {
                            onLaunch(context)
                        }
                    }
                )

                if (countdownTimer.value != 0) {
                    Text(
                        context.resources.getQuantityString(
                            R.plurals.automatically_load_countdown, countdownTimer.value, countdownTimer.value
                        ),
                        style = Typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.size(12.dp))
                }
            }

            Row {
                Button(onClick = { onLaunch(context) }) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = context.getString(R.string.launcher_launch_icon_alt)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(context.getString(R.string.launcher_launch))
                }
                Spacer(Modifier.size(2.dp))
                IconButton(onClick = { onSettings(context) }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = context.getString(R.string.launcher_settings_icon_alt)
                    )
                }
            }
        } else {
            Text(
                context.getString(R.string.game_not_found),
                modifier = Modifier.padding(12.dp)
            )
            OutlinedButton(onClick = { onSettings(context) }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = context.getString(R.string.launcher_settings_icon_alt)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(context.getString(R.string.launcher_settings))
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun MainScreenLightPreview() {
    GeodeLauncherTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainScreen()
        }
    }
}


@Preview(showSystemUi = true)
@Composable
fun MainScreenDarkPreview() {
    GeodeLauncherTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainScreen()
        }
    }
}

@Preview
@Composable
fun MainScreenNoGeometryDashPreview() {
    GeodeLauncherTheme {
        MainScreen(gdInstalled = false)
    }
}