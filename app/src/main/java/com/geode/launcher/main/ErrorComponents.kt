package com.geode.launcher.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.geode.launcher.preferences.ApplicationLogsActivity
import com.geode.launcher.BuildConfig
import com.geode.launcher.R
import com.geode.launcher.UserDirectoryProvider
import com.geode.launcher.ui.theme.Typography
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils

data class LoadFailureInfo(
    val title: LaunchUtils.LauncherError,
    val description: String? = null,
    val extendedMessage: String? = null
)

@Composable
fun ErrorInfoBody(failureReason: LaunchUtils.LauncherError, modifier: Modifier = Modifier) {
    val headline = when (failureReason) {
        LaunchUtils.LauncherError.LINKER_FAILED -> stringResource(R.string.load_failed_link_error_description)
        LaunchUtils.LauncherError.CRASHED -> stringResource(R.string.load_failed_crashed_description)
        else -> stringResource(R.string.load_failed_generic_error_description)
    }

    val context = LocalContext.current
    val recommendations = when (failureReason) {
        LaunchUtils.LauncherError.LINKER_FAILED_STL -> {
            val isDeveloper = PreferenceUtils.get(context).getBoolean(PreferenceUtils.Key.DEVELOPER_MODE)

            buildList {
                add(stringResource(R.string.load_failed_recommendation_update))
                add(stringResource(R.string.load_failed_recommendation_report))

                if (isDeveloper)
                    add(stringResource(R.string.load_failed_recommendation_dev_stl))
            }
        }
        LaunchUtils.LauncherError.LINKER_FAILED -> listOfNotNull(
            stringResource(R.string.load_failed_recommendation_reinstall),
            stringResource(R.string.load_failed_recommendation_update),
        )
        LaunchUtils.LauncherError.CRASHED -> listOf(
            stringResource(R.string.load_failed_recommendation_safe_mode),
            stringResource(R.string.load_failed_recommendation_report)
        )
        else -> listOf(
            stringResource(R.string.load_failed_recommendation_reinstall),
            stringResource(R.string.load_failed_recommendation_update),
            stringResource(R.string.load_failed_recommendation_report)
        )
    }

    Column(modifier = modifier) {
        Text(headline)

        recommendations.forEach { r ->
            Text("\u2022\u00A0\u00A0$r")
        }
    }
}

@Composable
fun ErrorInfoTitle(failureReason: LaunchUtils.LauncherError) {
    val message = when (failureReason) {
        LaunchUtils.LauncherError.LINKER_NEEDS_64BIT,
        LaunchUtils.LauncherError.LINKER_NEEDS_32BIT,
        LaunchUtils.LauncherError.LINKER_FAILED_STL,
        LaunchUtils.LauncherError.LINKER_FAILED -> stringResource(R.string.load_failed_link_error)
        LaunchUtils.LauncherError.GENERIC -> stringResource(R.string.load_failed_generic_error)
        LaunchUtils.LauncherError.CRASHED -> stringResource(R.string.load_failed_crashed)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.icon_error),
            contentDescription = stringResource(R.string.launcher_error_icon_alt),
            modifier = Modifier.size(28.dp)
        )
        Text(message, style = Typography.headlineMedium)
    }

}

fun onShareCrash(context: Context) {
    val lastCrash = LaunchUtils.getLastCrash(context)
    if (lastCrash == null) {
        Toast.makeText(context, R.string.launcher_error_export_missing, Toast.LENGTH_SHORT).show()
        return
    }

    val baseDirectory = LaunchUtils.getBaseDirectory(context)

    val providerLocation = lastCrash.toRelativeString(baseDirectory)
    val documentsPath = "${UserDirectoryProvider.ROOT}${providerLocation}"

    val uri = DocumentsContract.buildDocumentUri("${context.packageName}.user", documentsPath)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "application/octet-stream"
    }

    try {
        context.startActivity(shareIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_activity_found, Toast.LENGTH_SHORT).show()
    }
}

fun onShowLogs(context: Context) {
    val launchIntent = Intent(context, ApplicationLogsActivity::class.java)
    context.startActivity(launchIntent)
}

@Composable
fun ErrorInfoDescription(description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentRelease = remember {
        PreferenceUtils.get(context).getString(PreferenceUtils.Key.CURRENT_VERSION_TAG)
    }

    val gameVersion = remember {
        GamePackageUtils.getUnifiedVersionName(context.packageManager)
    }

    Column(modifier = modifier) {
        Text(
            stringResource(R.string.launcher_error_details),
            style = Typography.headlineSmall
        )

        Spacer(Modifier.size(8.dp))

        Text(
            description,
            fontFamily = FontFamily.Monospace,
            style = Typography.bodyMedium
        )

        Spacer(Modifier.size(4.dp))

        Text(
            stringResource(R.string.launcher_error_device_info,
                Build.MODEL,
                Build.VERSION.RELEASE,
                currentRelease ?: "unknown",
                gameVersion,
                LaunchUtils.applicationArchitecture,
                BuildConfig.VERSION_NAME
            ),
            style = Typography.labelMedium
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ErrorInfoActions(extraDetails: String?, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            12.dp, alignment = Alignment.CenterHorizontally
        )
    ) {
        if (extraDetails != null) {
            Button(onClick = { onShowLogs(context) }) {
                Icon(
                    painterResource(R.drawable.icon_description),
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.launcher_error_view_logs))
            }

            FilledTonalButton(onClick = {
                clipboardManager.setText(AnnotatedString(extraDetails))
            }) {
                Icon(
                    painterResource(R.drawable.icon_content_copy),
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.launcher_error_copy))
            }
        } else {
            Button(onClick = { onShareCrash(context) }) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.launcher_error_export_crash))
            }

            FilledTonalButton(onClick = { onShowLogs(context) }) {
                Icon(
                    painterResource(R.drawable.icon_description),
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.launcher_error_view_logs))
            }
        }
    }
}


@Composable
fun DownloadRecommendation(needsUniversal: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = BuildConfig.VERSION_NAME

    val showDownload = remember {
        !GamePackageUtils.isGameInstalled(context.packageManager) ||
                !GamePackageUtils.identifyGameLegitimacy(context.packageManager)
    }

    val downloadBase = "https://github.com/geode-sdk/android-launcher/releases/download/$version"
    val legacyDownloadUrl = "$downloadBase/geode-launcher-v$version-android32.apk"
    val universalDownloadUrl = "$downloadBase/geode-launcher-v$version.apk"

    val downloadUrl = if (needsUniversal)
        universalDownloadUrl else legacyDownloadUrl

    val downloadText = if (needsUniversal)
        stringResource(R.string.launcher_download_universal)
    else
        stringResource(R.string.launcher_download_32bit)

    val promptTitle = stringResource(R.string.load_failed_abi_error)
    val reinstallText = stringResource(R.string.load_failed_recommendation_reinstall)
    val recommendationText = if (needsUniversal)
        stringResource(R.string.load_failed_recommendation_switch_64bit_abi)
    else
        stringResource(R.string.load_failed_recommendation_switch_32bit_abi)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            12.dp, alignment = Alignment.CenterVertically
        )
    ) {
        Text(promptTitle)
        Text("\u2022\u00A0\u00A0$reinstallText")

        if (showDownload) {
            GooglePlayBadge()
        }

        Text("\u2022\u00A0\u00A0$recommendationText")

        Text(
            buildAnnotatedString {
                withLink(LinkAnnotation.Url(
                    downloadUrl,
                    TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        ),
                    )
                )) {
                    append(downloadText)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorInfoSheet(failureInfo: LoadFailureInfo, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = { onDismiss() }, sheetState = sheetState) {
        // second container created for scroll (before padding is added)
        Box(Modifier.verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier
                .padding(horizontal = 24.dp)
            ) {
                ErrorInfoTitle(failureInfo.title)

                Spacer(Modifier.size(16.dp))

                if (failureInfo.title.isAbiFailure()) {
                    DownloadRecommendation(
                        needsUniversal = failureInfo.title == LaunchUtils.LauncherError.LINKER_NEEDS_64BIT,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                    )
                } else {
                    ErrorInfoBody(failureInfo.title)

                    if (failureInfo.description != null) {
                        ErrorInfoDescription(
                            failureInfo.description,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    ErrorInfoActions(
                        extraDetails = failureInfo.extendedMessage ?: failureInfo.description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}

suspend fun showFailureSnackbar(
    context: Context,
    loadFailureInfo: LoadFailureInfo,
    snackbarHostState: SnackbarHostState,
    onActionPerformed: () -> Unit
) {
    val message = if (loadFailureInfo.title == LaunchUtils.LauncherError.CRASHED) {
        context.getString(R.string.launcher_crashed)
    } else {
        context.getString(R.string.launcher_failed_to_load)
    }

    val res = snackbarHostState.showSnackbar(
        message = message,
        actionLabel = context.getString(R.string.launcher_error_more),
        duration = SnackbarDuration.Indefinite,
    )

    when (res) {
        SnackbarResult.ActionPerformed -> {
            onActionPerformed()
        }
        SnackbarResult.Dismissed -> {}
    }
}
