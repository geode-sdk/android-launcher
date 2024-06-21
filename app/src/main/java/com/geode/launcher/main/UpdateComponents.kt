package com.geode.launcher.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException

fun clearDownloadedApks(context: Context) {
    // technically we should be using the activity results but it was too inconsistent for my liking
    val preferenceUtils = PreferenceUtils.get(context)
    val performCleanup = preferenceUtils.getBoolean(PreferenceUtils.Key.CLEANUP_APKS)
    if (!performCleanup) {
        return
    }

    val baseDirectory = LaunchUtils.getBaseDirectory(context)

    baseDirectory.listFiles {
        // only select apk files
            _, name -> name.lowercase().endsWith(".apk")
    }?.forEach {
        it.delete()
    }

    preferenceUtils.setBoolean(PreferenceUtils.Key.CLEANUP_APKS, false)
}

fun generateInstallIntent(uri: Uri): Intent {
    // maybe one day i'll rewrite this to use packageinstaller. not today
    return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun installLauncherUpdate(context: Context) {
    val nextUpdate = ReleaseManager.get(context).availableLauncherUpdate.value
    val launcherDownload = nextUpdate?.getLauncherDownload()

    if (launcherDownload != null) {
        val outputFile = launcherDownload.name
        val baseDirectory = LaunchUtils.getBaseDirectory(context)

        val outputPathFile = File(baseDirectory, outputFile)
        if (!outputPathFile.exists()) {
            return
        }

        val outputPath = "${UserDirectoryProvider.ROOT}${outputFile}"

        val uri = DocumentsContract.buildDocumentUri("${context.packageName}.user", outputPath)

        PreferenceUtils.get(context).setBoolean(PreferenceUtils.Key.CLEANUP_APKS, true)

        try {
            val intent = generateInstallIntent(uri)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.no_activity_found), Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, context.getString(R.string.release_fetch_no_releases), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun LauncherUpdateIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    ElevatedCard(modifier) {
        Column(
            // buttons add enough padding already, lower to compensate
            modifier = Modifier.padding(
                top = 20.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.launcher_update_available),
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            TextButton(
                onClick = { installLauncherUpdate(context) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.launcher_install))
            }
        }
    }
}

@Composable
fun UpdateProgressIndicator(
    message: String,
    releaseViewModel: ReleaseViewModel,
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
        modifier = modifier
    ) {
        Text(message)

        Spacer(modifier = Modifier.padding(4.dp))

        if (progress == null) {
            LinearProgressIndicator()
        } else {
            LinearProgressIndicator(progress = progress)
        }

        TextButton(
            onClick = {
                releaseViewModel.cancelUpdate()
            },
            modifier = Modifier.offset((-12).dp)
        ) {
            Text(stringResource(R.string.release_fetch_button_cancel))
        }
    }
}

@Composable
fun UpdateMessageIndicator(
    message: String,
    releaseViewModel: ReleaseViewModel,
    modifier: Modifier = Modifier,
    allowRetry: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.padding(4.dp))

        if (allowRetry) {
            OutlinedButton(
                onClick = {
                    releaseViewModel.runReleaseCheck(true)
                },
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.launcher_retry_icon_alt)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.release_fetch_button_retry))
            }
        }
    }
}

@Composable
fun UpdateCard(releaseViewModel: ReleaseViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val releaseState by releaseViewModel.uiState.collectAsState()

    when (val state = releaseState) {
        is ReleaseViewModel.ReleaseUIState.Failure -> {
            val message = when (state.exception) {
                is UnknownHostException, is ConnectException ->
                    stringResource(R.string.release_fetch_no_internet)
                is ReleaseManager.UpdateException -> {
                    when (state.exception.reason) {
                        ReleaseManager.UpdateException.Reason.EXTERNAL_FILE_IN_USE -> stringResource(
                            R.string.release_fetch_manual_check_required
                        )
                        else -> null
                    }
                }
                else -> null
            }

            UpdateMessageIndicator(
                stringResource(
                    R.string.release_fetch_failed,
                    message ?: stringResource(R.string.release_fetch_try_later)
                ),
                modifier = modifier,
                allowRetry = true,
                releaseViewModel = releaseViewModel
            )
        }
        is ReleaseViewModel.ReleaseUIState.InDownload -> {
            val downloaded = remember(state.downloaded) {
                Formatter.formatShortFileSize(context, state.downloaded)
            }

            val outOf = remember(state.outOf) {
                Formatter.formatShortFileSize(context, state.outOf)
            }

            UpdateProgressIndicator(
                stringResource(
                    R.string.release_fetch_downloading,
                    downloaded,
                    outOf
                ),
                modifier = modifier,
                releaseViewModel = releaseViewModel,
                progress = {
                    val progress = state.downloaded / state.outOf.toDouble()
                    progress.toFloat()
                }
            )
        }
        is ReleaseViewModel.ReleaseUIState.InUpdateCheck -> {
            UpdateProgressIndicator(
                stringResource(R.string.release_fetch_in_progress),
                modifier = modifier,
                releaseViewModel = releaseViewModel
            )
        }
        is ReleaseViewModel.ReleaseUIState.Finished -> {
            if (state.hasUpdated) {
                UpdateMessageIndicator(
                    stringResource(R.string.release_fetch_success),
                    modifier = modifier,
                    releaseViewModel = releaseViewModel
                )
            }
        }
        is ReleaseViewModel.ReleaseUIState.Cancelled -> {
            if (state.isCancelling) {
                UpdateProgressIndicator(
                    stringResource(R.string.release_fetch_cancelling),
                    modifier = modifier,
                    releaseViewModel = releaseViewModel
                )
            } else {
                UpdateMessageIndicator(
                    stringResource(R.string.release_fetch_cancelled),
                    modifier = modifier,
                    allowRetry = true,
                    releaseViewModel = releaseViewModel
                )
            }
        }
    }

    val nextUpdate by releaseViewModel.nextLauncherUpdate.collectAsState()

    if (!releaseViewModel.isInUpdate && nextUpdate != null) {
        LauncherUpdateIndicator(
            modifier = modifier
        )
    }
}