package dev.xyze.geodelauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import dev.xyze.geodelauncher.ui.theme.GeodeLauncherTheme
import dev.xyze.geodelauncher.utils.LaunchUtils
import java.io.File


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gdInstalled = LaunchUtils.isGeometryDashInstalled(packageManager)

        setContent {
            GeodeLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    Toast.makeText(context, "Settings are not implemented yet!", Toast.LENGTH_SHORT).show()
}

fun onOpenFolder(context: Context) {
    context.getExternalFilesDir(null)?.let { file ->
        val geodePath = file.path + File.separator + "geode" + File.separator
    }
}

fun toggleLoadTesting(context: Context): Boolean {
    val preferences = context.getSharedPreferences(
        context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)

    val current = preferences.getBoolean(
        context.getString(R.string.preference_load_testing), false)
    preferences.edit {
        putBoolean(context.getString(R.string.preference_load_testing), !current)
        commit()
    }

    return preferences.getBoolean(context.getString(R.string.preference_load_testing), false)
}

fun getLoadTesting(context: Context): Boolean {
    val preferences = context.getSharedPreferences(
        context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)

    return preferences.getBoolean(context.getString(R.string.preference_load_testing), false)
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
/*
                IconButton(onClick = { onSettings(context) }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings Icon"
                    )
                }
 */
            }
        } else {
            Text(
                "Geometry Dash could not be found.",
                modifier = Modifier.padding(12.dp)
            )
/*
            Button(onClick = { onSettings(context) }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings Icon"
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Settings")
            }
 */
        }

        Spacer(modifier = Modifier.height(12.dp))

        TestingUtils()
    }
}

@Composable
fun TestingUtils() {
    val context = LocalContext.current

    val isLoadTesting = remember { mutableStateOf(getLoadTesting(context)) }

    Card(
        elevation = CardDefaults.elevatedCardElevation(),
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "Warning Icon"
                )
                Spacer(Modifier.size(4.dp))
                Text("Testing Utils")
            }
            Row(
                Modifier
                    .height(48.dp)
                    .toggleable(
                        value = isLoadTesting.value,
                        onValueChange = { isLoadTesting.value = toggleLoadTesting(context) },
                        role = Role.Checkbox
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isLoadTesting.value,
                    onCheckedChange = null
                )
                Text(
                    text = "Load files in /test",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Button(
                onClick = { onOpenFolder(context) }
            ) {
                Icon(
                    Icons.Filled.ExitToApp,
                    contentDescription = "Folder Icon"
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Open Geode folder")
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