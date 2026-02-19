package com.geode.launcher.main

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.geode.launcher.InstallReceiver
import com.geode.launcher.R
import com.geode.launcher.main.LauncherUpdater.downloadUpdate
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.time.toJavaInstant

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

    context.filesDir.listFiles { _, name -> name.lowercase().endsWith(".apk") }?.forEach {
        it.delete()
    }

    preferenceUtils.setBoolean(PreferenceUtils.Key.CLEANUP_APKS, false)
}

// lazy singleton idc
object LauncherUpdater {
    enum class LauncherUpdateState {
        Began,
        Downloading,
        Active,
        Inactive,
        Finished;
    }

    private val _uiState = MutableStateFlow<LauncherUpdateState>(LauncherUpdateState.Inactive)
    val uiState = _uiState.asStateFlow()

    suspend fun resetState() {
        _uiState.emit(LauncherUpdateState.Inactive)
    }

    suspend fun downloadUpdate(context: Context): Result<File> {
        _uiState.emit(LauncherUpdateState.Downloading)

        return withContext(Dispatchers.IO) {
            ReleaseManager.get(context)
                .downloadLatestLauncherUpdate()
        }
    }

    suspend fun installPackage(context: Context, file: File) {
        _uiState.emit(LauncherUpdateState.Began)

        val installer = context.packageManager.packageInstaller

        installer.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onActiveChanged(sessionId: Int, active: Boolean) {
                if (active) {
                    _uiState.tryEmit(LauncherUpdateState.Active)
                } else {
                    _uiState.tryEmit(LauncherUpdateState.Began)
                }
            }

            override fun onBadgingChanged(sessionId: Int) {}

            override fun onCreated(sessionId: Int) {
                _uiState.tryEmit(LauncherUpdateState.Began)
            }

            override fun onFinished(sessionId: Int, success: Boolean) {
                _uiState.tryEmit(LauncherUpdateState.Finished)
            }

            override fun onProgressChanged(sessionId: Int, progress: Float) {
                _uiState.tryEmit(LauncherUpdateState.Active)
            }
        })

        withContext(Dispatchers.IO) { runInterruptible {
            file.inputStream().use { apkStream ->
                val length = file.length()

                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                )

                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("package", 0, length).use { sessionStream ->
                        apkStream.copyTo(sessionStream)
                        session.fsync(sessionStream)
                    }

                    val intent = Intent(context, InstallReceiver::class.java)
                    val pi = PendingIntent.getBroadcast(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    session.commit(pi.intentSender)
                }
            }
        }}
    }
}

suspend fun installLauncherUpdate(context: Context) {
    val launcherDownload = downloadUpdate(context)
        .getOrElse { e ->
            e.printStackTrace()
            Toast.makeText(
                context, context.getString(
                    R.string.launcher_update_download_failed,
                    e.cause
                ), Toast.LENGTH_SHORT
            ).show()
            return
        }

    if (!launcherDownload.exists()) {
        return
    }

    PreferenceUtils.get(context).setBoolean(PreferenceUtils.Key.CLEANUP_APKS, true)

    try {
        LauncherUpdater.installPackage(context, launcherDownload)
    } catch (e: Exception) {
        e.printStackTrace()
        val msg = context.getString(R.string.launcher_self_update_failed, e.message)
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

        LauncherUpdater.resetState()
    }
}

@Composable
fun LauncherUpdateDialog(isCancelling: Boolean, onDismiss: () -> Unit) {
    val updateState by LauncherUpdater.uiState.collectAsState()

    LaunchedEffect(updateState) {
        if (updateState == LauncherUpdater.LauncherUpdateState.Finished) {
            onDismiss()
        }
    }

    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.icon_update),
                contentDescription = null
            )
        },
        title = {
            if (isCancelling) {
                Text(stringResource(R.string.release_fetch_cancelling))
            } else if (updateState == LauncherUpdater.LauncherUpdateState.Downloading) {
                Text(stringResource(R.string.launcher_downloading_update))
            } else {
                Text(stringResource(R.string.launcher_self_update_progress))
            }
        },
        text = {
            LinearProgressIndicator()
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isCancelling) {
                Text(stringResource(R.string.message_box_cancel))
            }
        },
        onDismissRequest = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherUpdateInformation(onDismiss: () -> Unit) {
    val context = LocalContext.current

    val releaseManager = remember { ReleaseManager.get(context) }

    val nextReleaseName by releaseManager.availableLauncherUpdateTag.collectAsState()
    val nextRelease by releaseManager.availableLauncherUpdateDetails.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    var updateJob by remember { mutableStateOf<Job?>(null) }
    var cancellingUpdate by remember { mutableStateOf(false) }

    var beginUpdate by remember { mutableStateOf(false) }
    if (beginUpdate) {
        LauncherUpdateDialog(cancellingUpdate, onDismiss = {
            cancellingUpdate = true

            coroutineScope.launch {
                updateJob?.cancelAndJoin()

                beginUpdate = false
            }
        })
    }

    LaunchedEffect(Unit) {
        if (nextReleaseName != null && nextRelease == null) {
            coroutineScope.launch { withContext(Dispatchers.IO) {
                ReleaseManager.get(context)
                    .checkLauncherUpdate()
            }}
        }
    }

    val nextReleaseNameSaved = nextReleaseName
    if (nextReleaseNameSaved != null) {
        ModalBottomSheet(onDismissRequest = { onDismiss() }, sheetState = sheetState) {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(R.string.launcher_update_name, nextReleaseNameSaved),
                        style = Typography.headlineSmall,
                    )

                    val nextRelease = nextRelease
                    if (nextRelease != null) {
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
                                onClick = {
                                    updateJob = coroutineScope.launch {
                                        cancellingUpdate = false
                                        beginUpdate = true

                                        installLauncherUpdate(context)
                                    }
                                },
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

                        if (nextRelease.release.body != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Text(
                                stringResource(R.string.launcher_update_changelog),
                                style = Typography.titleLarge
                            )

                            // every new release of the markdown library adds another arg to it
                            CompositionLocalProvider(LocalBulletListHandler provides { _, _, _, _, _ -> "•  " }) {
                                Markdown(
                                    content = nextRelease.release.body.replace("\r", ""),
                                    typography = markdownTypography(
                                        textLink = TextLinkStyles(
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                textDecoration = TextDecoration.Underline,
                                                color = MaterialTheme.colorScheme.primary,
                                            ).toSpanStyle()
                                        ),
                                    ),
                                    flavour = GFMFlavourDescriptor(),
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator()
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
    val coroutineScope = rememberCoroutineScope()

    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        LauncherUpdateInformation {
            showInfoDialog = false
        }
    }

    var updateJob by remember { mutableStateOf<Job?>(null) }
    var cancellingUpdate by remember { mutableStateOf(false) }

    var beginUpdate by remember { mutableStateOf(false) }
    if (beginUpdate) {
        LauncherUpdateDialog(cancellingUpdate) {
            cancellingUpdate = true
            coroutineScope.launch {
                updateJob?.cancelAndJoin()
                updateJob = null

                beginUpdate = false
            }
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
                    onClick = {
                        updateJob = coroutineScope.launch {
                            cancellingUpdate = false
                            beginUpdate = true

                            installLauncherUpdate(context)
                        }
                    },
                ) {
                    Text(stringResource(R.string.launcher_install))
                }
            }
        }
    }
}
