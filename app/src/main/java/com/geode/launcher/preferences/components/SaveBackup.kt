package com.geode.launcher.preferences.components

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geode.launcher.R
import com.geode.launcher.utils.LaunchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.GzipSource
import okio.buffer
import okio.source
import org.xmlpull.v1.XmlPullParser
import java.io.File
import kotlin.experimental.xor
import kotlin.time.Duration.Companion.seconds

const val TAG = "com.geode.launcher.SaveBackup"

data class UserDetails(
    val userName: String,
    val gjp2: String,
    val accountId: Int,
    val uuid: Int,
    val udid: String,
    val binaryVersion: Int,
)

enum class BackupParseFailure {
    SAVE_MISSING,
    DECODE_FAILED,
    DETAILS_MISSING,
    SAVE_TOO_LARGE
}

class BackupParseException(val failureCode: BackupParseFailure) : Exception("parse error: $failureCode")

enum class BackupUploadFailure {
    GENERIC_ERROR,
    NETWORK_ERROR,
    INVALID_LOGIN
}

class BackupUploadException(val failureCode: BackupUploadFailure, val gjCode: Int = -1) : Exception("upload error: $failureCode")

suspend fun getAccountBackupUrl(backupClient: OkHttpClient, details: UserDetails): Result<HttpUrl> {
    return try {
        // accountID=%i&type=1&secret=Wmfd2893gb7
        val body = FormBody.Builder()
            .add("secret", "Wmfd2893gb7")
            .add("type", "1")
            .add("accountID", details.accountId.toString())
            .build()

        val request = Request.Builder()
            .url("https://www.boomlings.com/database/getAccountURL.php")
            .addHeader("User-Agent", "")
            .post(body)
            .build()

        val call = backupClient.newCall(request)
        call.executeAsync()
    } catch (e: Exception) {
        Log.w(TAG, "getAccountBackupUrl failed: ${e.stackTraceToString()}")
        return Result.failure(BackupUploadException(BackupUploadFailure.NETWORK_ERROR))
    }.use { response ->
        if (response.code != 200) {
            return Result.failure(BackupUploadException(BackupUploadFailure.GENERIC_ERROR))
        }

        val url = response.body.string().toHttpUrlOrNull()
            ?: return Result.failure(BackupUploadException(BackupUploadFailure.GENERIC_ERROR))

        Result.success(url)
    }
}

fun decryptFileToBuffer(file: File, buffer: Buffer) = file.source().buffer()
    .use { baseBuffer ->
        val key = 0xb.toByte()
        val nullByte = 0x0.toByte()

        while (!baseBuffer.exhausted()) {
            val b = baseBuffer.readByte()
            val d = b.xor(key)

            // gd sometimes encodes garbage after null byte, treat it like a c string
            if (d == nullByte)
                break

            buffer.writeByte(d.toInt())
        }
    }

fun generateSaveString(gameManager: File, localLevels: File): String = Buffer()
    .use { saveBuffer ->
        decryptFileToBuffer(gameManager, saveBuffer)
        saveBuffer.writeByte(';'.code)
        decryptFileToBuffer(localLevels, saveBuffer)

        saveBuffer.readUtf8()
    }

fun generateBackupRequest(details: UserDetails, url: HttpUrl, gameManager: File, localLevels: File): Request {
    val saveData = generateSaveString(gameManager, localLevels)

    // %s/database/accounts/backupGJAccountNew.php
    val backupUrl = url.newBuilder()
        .addPathSegments("database/accounts/backupGJAccountNew.php")
        .build()

    // gameVersion=22&binaryVersion=%i&dvs=2&udid=%s&uuid=%i&accountID=%i&gjp2=%s&secret=Wmfv3899gc9&saveData=%s
    val body = FormBody.Builder()
        .add("gameVersion", "22")
        .add("binaryVersion", details.binaryVersion.toString())
        .add("dvs", "2")
        .add("udid", details.udid)
        .add("uuid", details.uuid.toString())
        .add("accountID", details.accountId.toString())
        .add("secret", "Wmfv3899gc9")
        .add("gjp2", details.gjp2)
        .add("saveData", saveData)
        .build()

    return Request.Builder()
        .url(backupUrl)
        .addHeader("User-Agent", "")
        .post(body)
        .build()
}

suspend fun performBackupRequest(backupClient: OkHttpClient, details: UserDetails, url: HttpUrl, gameManager: File, localLevels: File): Result<Unit> {
    return try {
        // we split this into multiple functions to free up as many references as we can
        // go little gc go
        backupClient.newCall(
            generateBackupRequest(details, url, gameManager, localLevels)
        ).executeAsync()
    } catch (e: Exception) {
        Log.w(TAG, "performBackupRequest failed: ${e.stackTraceToString()}")
        return Result.failure(BackupUploadException(BackupUploadFailure.GENERIC_ERROR))
    }.use { response ->
        if (response.code != 200) {
            return Result.failure(BackupUploadException(BackupUploadFailure.GENERIC_ERROR))
        }

        val responseBody = response.body.string().toIntOrNull()
            ?: return Result.failure(BackupUploadException(BackupUploadFailure.GENERIC_ERROR))

        when (responseBody) {
            1 -> Result.success(Unit)
            -5 -> Result.failure(BackupUploadException(BackupUploadFailure.INVALID_LOGIN, responseBody))
            else -> Result.failure(BackupUploadException(BackupUploadFailure.GENERIC_ERROR, responseBody))
        }
    }
}

fun skip(parser: XmlPullParser) {
    if (parser.eventType != XmlPullParser.START_TAG) {
        throw IllegalStateException()
    }
    var depth = 1
    while (depth != 0) {
        when (parser.next()) {
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.START_TAG -> depth++
        }
    }
}

fun getNextValue(parser: XmlPullParser): String? {
    while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
        parser.next()
    }

    if (parser.eventType == XmlPullParser.START_TAG) {
        // we could support t/f here but we also don't have to
        if (parser.name != "s" && parser.name != "i") {
            return null
        }

        return parser.nextText()
    }

    return null
}

fun parseSaveFile(parser: XmlPullParser): Result<UserDetails> = try {
    var userName: String? = null
    var gjp2: String? = null
    var accountId: Int? = null
    var uuid: Int? = null
    var udid: String? = null
    var binaryVersion: Int? = null

    var inPlist = false
    var inDict = false

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                if (inDict) {
                    // ignore inner elements, just in case they conflict
                    if (parser.name != "k") {
                        skip(parser)
                    } else {
                        val keyName = parser.nextText()
                        when (keyName) {
                            "GJA_001" -> userName = getNextValue(parser)
                            "GJA_003" -> accountId = getNextValue(parser)?.toIntOrNull()
                            "GJA_005" -> gjp2 = getNextValue(parser)
                            "playerUserID" -> uuid = getNextValue(parser)?.toIntOrNull()
                            "playerUDID" -> udid = getNextValue(parser)
                            "binaryVersion" -> binaryVersion = getNextValue(parser)?.toIntOrNull()
                        }
                    }
                }

                // traverse to initial plist -> dict element
                if (!inPlist && parser.name == "plist") {
                    inPlist = true
                }
                if (!inDict && parser.name == "dict") {
                    inDict = true
                }
            }
        }

        eventType = parser.next()
    }

    if (userName == null || gjp2 == null || accountId == null || uuid == null || udid == null || binaryVersion == null) {
        Result.failure(BackupParseException(BackupParseFailure.DETAILS_MISSING))
    } else {
        Result.success(
            UserDetails(
                userName = userName,
                gjp2 = gjp2,
                accountId = accountId,
                uuid = uuid,
                udid = udid,
                binaryVersion = binaryVersion
            )
        )
    }
} catch (e: Exception) {
    Log.w(TAG, "parseSaveFile failed: ${e.stackTraceToString()}")
    Result.failure(BackupParseException(BackupParseFailure.DECODE_FAILED))
}

fun decryptFileToString(file: File) = Buffer()
    .use { decryptedBuffer ->
        decryptFileToBuffer(file, decryptedBuffer)
        decryptedBuffer.readUtf8()
    }

suspend fun getUserDetails(gameManager: File): Result<UserDetails> {
    return try {
        val base64Decoded = run {
            // free decryptString after decoding
            val decryptString = decryptFileToString(gameManager)
            decryptString.decodeBase64()
        } ?: return Result.failure(BackupParseException(BackupParseFailure.DECODE_FAILED))

        // gzipSource will close the underlying buffer when closed
        GzipSource(
        Buffer().write(base64Decoded)
        ).buffer().use { gzipSource ->
            runInterruptible {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(gzipSource.inputStream(), null)

                parseSaveFile(parser)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "getUserDetails failed: ${e.stackTraceToString()}")
        return Result.failure(BackupParseException(BackupParseFailure.DECODE_FAILED))
    }
}

const val MB_TO_BYTES = 1048576 // 2^20

suspend fun backupSaveToCloud(context: Context): Result<Unit> {
    val baseDirectory = LaunchUtils.getBaseDirectory(context)

    val gameManager = File(baseDirectory,"/save/CCGameManager.dat")
    val localLevels = File(baseDirectory, "/save/CCLocalLevels.dat")

    if (!gameManager.isFile || !localLevels.isFile) {
        return Result.failure(BackupParseException(BackupParseFailure.SAVE_MISSING))
    }

    // hardcode 32mb max size
    // this is how the game checks it, which probably isn't the best
    val totalSize = (gameManager.length() + localLevels.length()) / MB_TO_BYTES.toDouble()
    println("Backup - calculated size of %.2fmb".format(totalSize))

    if (totalSize > 32.0) {
        return Result.failure(BackupParseException(BackupParseFailure.SAVE_TOO_LARGE))
    }

    return withContext(Dispatchers.IO) {
        val userDetails = getUserDetails(gameManager).getOrElse {
            return@withContext Result.failure(it)
        }

        println("Backup - performing as ${userDetails.userName}, accountID=${userDetails.accountId}")

        val backupClient = OkHttpClient.Builder()
            .writeTimeout(500.seconds)
            .build()

        val backupUrl = getAccountBackupUrl(backupClient, userDetails).getOrElse {
            return@withContext Result.failure(it)
        }

        performBackupRequest(backupClient, userDetails, backupUrl, gameManager, localLevels).getOrElse {
            return@withContext Result.failure(it)
        }

        println("Backup completed!")

        Result.success(Unit)
    }
}

@Composable
fun BackupConfirmDialog(onDismiss: () -> Unit, onEnable: () -> Unit) {
    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.icon_cloud_alert),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.preference_backup_confirm_title)) },
        text = { Text(stringResource(R.string.preference_backup_confirm_description)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.message_box_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.message_box_continue))
            }
        },
        onDismissRequest = onDismiss
    )
}

fun messageFromResult(context: Context, result: Result<Unit>): String {
    val exception = result.exceptionOrNull()
        ?: return context.getString(R.string.preference_backup_success)

    return when (exception) {
        is BackupParseException -> when (exception.failureCode) {
            BackupParseFailure.SAVE_MISSING -> context.getString(R.string.preference_backup_save_missing)
            BackupParseFailure.DECODE_FAILED -> context.getString(R.string.preference_backup_save_decode_failed)
            BackupParseFailure.DETAILS_MISSING -> context.getString(R.string.preference_backup_save_details_missing)
            BackupParseFailure.SAVE_TOO_LARGE -> context.getString(R.string.preference_backup_save_too_large)
        }
        is BackupUploadException -> when (exception.failureCode) {
            BackupUploadFailure.GENERIC_ERROR -> context.getString(R.string.preference_backup_failed, exception.gjCode)
            BackupUploadFailure.NETWORK_ERROR -> context.getString(R.string.preference_backup_failed_network)
            BackupUploadFailure.INVALID_LOGIN -> context.getString(R.string.preference_backup_failed_invalid_login)
        }
        else -> context.getString(R.string.preference_backup_failed_generic)
    }
}

@Composable
fun BackupDialog(onDismiss: () -> Unit, isCancelling: Boolean) {
    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.icon_cloud_upload),
                contentDescription = null
            )
        },
        title = {
            if (isCancelling) {
                Text(stringResource(R.string.preference_backup_cancelling))
            } else {
                Text(stringResource(R.string.preference_backup_in_progress))
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

@Composable
fun BackupButton(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current

    var showBackupDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var backupInProgress by remember { mutableStateOf(false) }
    var backupJob by remember { mutableStateOf<Job?>(null) }

    OptionsButton(
        title = stringResource(R.string.preference_backup_cloud),
        icon = {
            Icon(
                painterResource(R.drawable.icon_cloud_upload),
                contentDescription = null
            )
        },
    ) {
        showBackupDialog = true
    }

    if (showBackupDialog) {
        BackupConfirmDialog(onDismiss = {
            showBackupDialog = false
        }) {
            showBackupDialog = false

            backupJob = coroutineScope.launch {
                backupInProgress = true
                val responseStatus = backupSaveToCloud(context)
                backupInProgress = false

                snackbarHostState.showSnackbar(messageFromResult(context, responseStatus))
            }
        }
    }

    var isCancelling by remember { mutableStateOf(false) }

    if (backupInProgress) {
        BackupDialog(onDismiss = {
            coroutineScope.launch {
                isCancelling = true

                backupJob?.cancelAndJoin()

                isCancelling = false
                backupInProgress = false
            }
        }, isCancelling)
    }
}
