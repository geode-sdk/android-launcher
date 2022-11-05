package com.geode.geodelauncher

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
import com.geode.geodelauncher.ui.theme.GeodeLauncherTheme
import com.geode.geodelauncher.ui.theme.Typography
import com.geode.geodelauncher.utils.LaunchUtils
import com.geode.geodelauncher.utils.countdownTimerWatcher
import com.geode.geodelauncher.utils.preferenceWatcher


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

    val shouldAutomaticallyLaunch = preferenceWatcher(
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
            contentDescription = "Geode Logo",
            modifier = Modifier.size(136.dp, 136.dp)
        )
        Text(
            "Geode",
            fontSize = 32.sp,
            modifier = Modifier.padding(12.dp)
        )
        if (gdInstalled) {
            if (shouldAutomaticallyLaunch.value) {
                val countdownTimer = countdownTimerWatcher(
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
                Button(onClick = { onLaunch(context) }, enabled = gdInstalled) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play Icon"
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Launch")
                }
                Spacer(Modifier.size(2.dp))
                IconButton(onClick = { onSettings(context) }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings Icon"
                    )
                }
            }
        } else {
            Text(
                "Geometry Dash could not be found.",
                modifier = Modifier.padding(12.dp)
            )
            OutlinedButton(onClick = { onSettings(context) }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings Icon"
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Settings")
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