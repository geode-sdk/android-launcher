package com.geode.geodelauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import com.geode.geodelauncher.utils.LaunchUtils


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

fun onOpenFolder(context: Context) {
    context.getExternalFilesDir(null)?.let { file ->
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(
                "Launcher External Folder",
                file.path
            )
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainScreen(gdInstalled: Boolean = true) {
    val context = LocalContext.current

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