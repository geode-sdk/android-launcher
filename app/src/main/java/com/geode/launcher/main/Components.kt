package com.geode.launcher.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geode.launcher.R
import com.geode.launcher.utils.Constants

fun onDownloadGame(context: Context) {
    val appUrl = "https://play.google.com/store/apps/details?id=${Constants.PACKAGE_NAME}"
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(appUrl)
            setPackage("com.android.vending")
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_activity_found, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun GooglePlayBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Image(
        painter = painterResource(id = R.drawable.google_play_badge),
        contentDescription = stringResource(R.string.launcher_download_game),
        modifier = modifier
            .width(196.dp)
            .clip(AbsoluteRoundedCornerShape(4.dp))
            .clickable { onDownloadGame(context) }
    )
}

@Composable
fun StatusIndicator(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.padding(8.dp)) {
            icon()
            Spacer(Modifier.size(8.dp))
            text()
        }
    }
}

@Composable
fun InlineDialog(headline: @Composable () -> Unit, body: @Composable () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp).sizeIn(maxWidth = 512.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                headline()
            }

            body()
        }
    }
}