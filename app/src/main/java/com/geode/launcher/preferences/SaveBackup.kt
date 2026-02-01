package com.geode.launcher.preferences

import android.content.Context
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
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.parseSource
import com.fleeksoft.ksoup.parser.Parser
import com.geode.launcher.R
import com.geode.launcher.utils.LaunchUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
import java.io.File
import kotlin.collections.chunked
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.experimental.xor
import kotlin.time.Duration.Companion.seconds

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
        e.printStackTrace()
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
        while (!baseBuffer.exhausted()) {
            val b = baseBuffer.readByte()
            val d = b.xor(0xb)

            // gd sometimes encodes garbage after null byte, treat it like a c string
            if (d == 0.toByte())
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
        val request = generateBackupRequest(details, url, gameManager, localLevels)
        val call = backupClient.newCall(request)
        call.executeAsync()
    } catch (e: Exception) {
        e.printStackTrace()
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

fun parseSaveFile(file: Document): Result<UserDetails> = try {
    var userName: String? = null
    var gjp2: String? = null
    var accountId: Int? = null
    var uuid: Int? = null
    var udid: String? = null
    var binaryVersion: Int? = null

    val doc = file.child(0).child(0)
    doc.childNodes.chunked(2).forEach { (k, v) ->
        if (k.nodeName() == "k") {
            val keyName = k.nodeValue()
            when (keyName) {
                "GJA_001" -> userName = v.nodeValue()
                "GJA_003" -> accountId = v.nodeValue().toIntOrNull()
                "GJA_005" -> gjp2 = v.nodeValue()
                "playerUserID" -> uuid = v.nodeValue().toIntOrNull()
                "playerUDID" -> udid = v.nodeValue()
                "binaryVersion" -> binaryVersion = v.nodeValue().toIntOrNull()
            }
        }
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
    e.printStackTrace()
    Result.failure(BackupParseException(BackupParseFailure.DECODE_FAILED))
}

fun decryptFileToString(file: File) = Buffer()
    .use { decryptedBuffer ->
        decryptFileToBuffer(file, decryptedBuffer)
        decryptedBuffer.readUtf8()
    }

fun getUserDetails(gameManager: File): Result<UserDetails> {
    return try {
        val base64Decoded = run {
            // free decryptString after decoding
            val decryptString = decryptFileToString(gameManager)
            decryptString.decodeBase64()
        } ?: return Result.failure(BackupParseException(BackupParseFailure.DECODE_FAILED))

        // gzipSource will close the underlying buffer when closed
        val saveDoc = GzipSource(
        Buffer().write(base64Decoded)
        ).buffer().use { gzipSource ->
            Ksoup.parseSource(gzipSource, parser = Parser.xmlParser())
        }

        parseSaveFile(saveDoc)
    } catch (e: Exception) {
        e.printStackTrace()
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
    if (totalSize > 32.0) {
        println("Rejecting save file - calculated size of ${totalSize}mb")
        return Result.failure(BackupParseException(BackupParseFailure.SAVE_TOO_LARGE))
    }

    return withContext(Dispatchers.IO) {
        val userDetails = getUserDetails(gameManager).getOrElse {
            return@withContext Result.failure(it)
        }

        val backupClient = OkHttpClient.Builder()
            .writeTimeout(500.seconds)
            .build()

        val backupUrl = getAccountBackupUrl(backupClient, userDetails).getOrElse {
            return@withContext Result.failure(it)
        }

        performBackupRequest(backupClient, userDetails, backupUrl, gameManager, localLevels).getOrElse {
            return@withContext Result.failure(it)
        }

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
