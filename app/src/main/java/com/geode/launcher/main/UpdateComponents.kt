package com.geode.launcher.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.datetime.toJavaInstant
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.text.DateFormat
import java.util.Date

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
    val launcherDownload = nextUpdate?.getDownload()

    if (launcherDownload != null) {
        val outputFile = launcherDownload.filename
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
        } catch (_: ActivityNotFoundException) {
            // if it fails to install, just open it in the browser
            try {
                val downloadUrl = launcherDownload.url
                val downloadIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(downloadUrl)
                }

                context.startActivity(downloadIntent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.no_activity_found), Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        Toast.makeText(context, context.getString(R.string.release_fetch_no_releases), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherUpdateInformation(onDismiss: () -> Unit) {
    val context = LocalContext.current

    val nextRelease = remember { ReleaseManager.get(context).availableLauncherUpdate.value }
    val sheetState = rememberModalBottomSheetState()

    if (nextRelease != null) {
        ModalBottomSheet(onDismissRequest = { onDismiss() }, sheetState = sheetState) {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(R.string.launcher_update_name, nextRelease.release.tagName),
                        style = Typography.headlineSmall,
                    )

                    val releasedTime = remember {
                        DateFormat.getDateInstance().format(
                            Date.from(nextRelease.release.publishedAt.toJavaInstant())
                        )
                    }

                    Text(
                        stringResource(R.string.launcher_update_released, "$releasedTime"),
                        style = Typography.labelLarge
                    )

                    Row(modifier = Modifier.padding(vertical = 12.dp)) {
                        Button(
                            onClick = { installLauncherUpdate(context) },
                        ) {
                            Icon(painterResource(R.drawable.icon_download), contentDescription = null)
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.launcher_install))
                        }

                        val uriHandler = LocalUriHandler.current

                        IconButton(onClick = { uriHandler.openUri(nextRelease.release.htmlUrl) }) {
                            Icon(
                                painterResource(R.drawable.icon_link),
                                stringResource(R.string.launcher_update_external_link)
                            )
                        }

                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        stringResource(R.string.launcher_update_changelog),
                        style = Typography.titleLarge
                    )

                    if (nextRelease.release.body != null) {
                        CompositionLocalProvider(LocalBulletListHandler provides { _, _, _ -> "•  " }) {
                            Markdown(
                                content = nextRelease.release.body.replace("\r", ""),
                                colors = markdownColor(
                                    linkText = MaterialTheme.colorScheme.primary
                                ),
                                typography = markdownTypography(
                                    link = MaterialTheme.typography.bodyLarge.copy(
                                        textDecoration = TextDecoration.Underline
                                    )
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(4.dp))
                }
            }
        }
    }
}

@Composable
fun LauncherUpdateIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        LauncherUpdateInformation {
            showInfoDialog = false
        }
    }

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

            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(
                    onClick = { showInfoDialog = true },
                ) {
                    Text(stringResource(R.string.launcher_update_view))
                }

                TextButton(
                    onClick = { installLauncherUpdate(context) },
                ) {
                    Text(stringResource(R.string.launcher_install))
                }
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
        Text(message, style = MaterialTheme.typography.labelMedium)

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
                state.outOf?.run {
                    Formatter.formatShortFileSize(context, this)
                }
            }

            UpdateProgressIndicator(
                stringResource(
                    R.string.release_fetch_downloading,
                    downloaded,
                    outOf ?: "…"
                ),
                modifier = modifier,
                releaseViewModel = releaseViewModel,
                progress = {
                    val progress = state.downloaded / state.outOf!!.toDouble()
                    progress.toFloat()
                }.takeIf { state.outOf != null }
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