package com.geode.launcher.main

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geode.launcher.InstallReceiver
import com.geode.launcher.R
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.updater.ReleaseManager
import com.geode.launcher.updater.ReleaseViewModel
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
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
        Active,
        Inactive,
        Finished;
    }

    private val _uiState = MutableStateFlow<LauncherUpdateState>(LauncherUpdateState.Inactive)
    val uiState = _uiState.asStateFlow()

    suspend fun resetState() {
        _uiState.emit(LauncherUpdateState.Inactive)
    }

    suspend fun installPackage(context: Context, file: File) {
        if (!file.exists()) {
            _uiState.emit(LauncherUpdateState.Finished)
            return
        }

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

        withContext(Dispatchers.IO) {
            file.inputStream().use { apkStream ->
                val length = file.length()

                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }

                val sessionId = installer.createSession(params)
                val session = installer.openSession(sessionId)

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
                session.close()
            }
        }
    }
}


suspend fun installLauncherUpdate(context: Context) {
    val nextUpdate = ReleaseManager.get(context).availableLauncherUpdate.value
    val launcherDownload = nextUpdate?.getDownload()

    if (launcherDownload != null) {
        val outputFile = launcherDownload.filename
        val baseDirectory = context.filesDir

        val outputPathFile = File(baseDirectory, outputFile)
        if (!outputPathFile.exists()) {
            return
        }

        PreferenceUtils.get(context).setBoolean(PreferenceUtils.Key.CLEANUP_APKS, true)

        try {
            LauncherUpdater.installPackage(context, outputPathFile)
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = context.getString(R.string.launcher_self_update_failed, e.message)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

            LauncherUpdater.resetState()
        }
    } else {
        Toast.makeText(context, context.getString(R.string.release_fetch_no_releases), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun LauncherUpdateDialog(onDismiss: () -> Unit) {
    val updateState by LauncherUpdater.uiState.collectAsState()

    val cancellable = updateState != LauncherUpdater.LauncherUpdateState.Active

    LaunchedEffect(updateState) {
        if (updateState == LauncherUpdater.LauncherUpdateState.Finished) {
            onDismiss()
        }
    }

    if (cancellable) {
        return
    }

    Dialog(
        onDismissRequest = {
            if (cancellable) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                CircularProgressIndicator()

                Text(
                    stringResource(R.string.launcher_self_update_progress),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherUpdateInformation(onDismiss: () -> Unit) {
    val context = LocalContext.current

    val nextRelease = remember { ReleaseManager.get(context).availableLauncherUpdate.value }
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    var beginUpdate by remember { mutableStateOf(false) }
    if (beginUpdate) {
        LauncherUpdateDialog(onDismiss = {
            beginUpdate = false
        })
    }

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
                            onClick = {
                                coroutineScope.launch {
                                    installLauncherUpdate(context)
                                    beginUpdate = true
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

    var beginUpdate by remember { mutableStateOf(false) }
    if (beginUpdate) {
        LauncherUpdateDialog {
            beginUpdate = false
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
                        coroutineScope.launch {
                            installLauncherUpdate(context)
                            beginUpdate = true
                        }
                    },
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
                    painterResource(R.drawable.icon_refresh),
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
                if (outOf != null) stringResource(
                        R.string.release_fetch_downloading,
                        downloaded,
                        outOf
                ) else stringResource(
                    R.string.release_fetch_downloading_indeterminate,
                    downloaded,
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