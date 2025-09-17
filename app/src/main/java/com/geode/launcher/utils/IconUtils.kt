package com.geode.launcher.utils

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.geode.launcher.R

// https://gist.github.com/tkuenneth/ddf598663f041dc79960cda503d14448?permalink_comment_id=4660486#gistcomment-4660486
@Composable
fun adaptiveIconPainterResource(@DrawableRes id: Int): Painter {
    val context = LocalContext.current
    val res = LocalResources.current
    val theme = context.theme

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val adaptiveIcon = remember(id) {
            ResourcesCompat.getDrawable(res, id, theme) as? AdaptiveIconDrawable
        }
        if (adaptiveIcon != null) {
            remember(id) {
                BitmapPainter(adaptiveIcon.toBitmap().asImageBitmap())
            }
        } else {
            painterResource(id)
        }
    } else {
        painterResource(id)
    }
}

enum class ApplicationIcon {
    DEFAULT,
    GEODE,
    PRIDE,
    TRANS;

    companion object {
        fun fromId(id: String) = when (id) {
            "default" -> ApplicationIcon.DEFAULT
            "geode" -> ApplicationIcon.GEODE
            "pride" -> ApplicationIcon.PRIDE
            "trans" -> ApplicationIcon.TRANS
            else -> ApplicationIcon.DEFAULT
        }
    }

    fun toId() = when (this) {
        ApplicationIcon.PRIDE -> "pride"
        ApplicationIcon.GEODE -> "geode"
        ApplicationIcon.DEFAULT -> "default"
        ApplicationIcon.TRANS -> "trans"
    }
}

data class ApplicationIconDetails(
    val id: String,
    val component: String,
    @param:DrawableRes val iconId: Int,
    @param:StringRes val nameId: Int,
)

object IconUtils {
    fun getIconDetails(id: ApplicationIcon) = when (id) {
        ApplicationIcon.DEFAULT -> ApplicationIconDetails(
            id = id.toId(),
            component = "MainActivity",
            iconId = R.mipmap.ic_launcher,
            nameId = R.string.application_icon_default
        )
        ApplicationIcon.GEODE -> ApplicationIconDetails(
            id = id.toId(),
            component = "MainActivityGeode",
            iconId = R.mipmap.ic_launcher_geode,
            nameId = R.string.application_icon_geode
        )
        ApplicationIcon.PRIDE -> ApplicationIconDetails(
            id = id.toId(),
            component = "MainActivityPride",
            iconId = R.mipmap.ic_launcher_pride,
            nameId = R.string.application_icon_pride
        )
        ApplicationIcon.TRANS -> ApplicationIconDetails(
            id = id.toId(),
            component = "MainActivityTrans",
            iconId = R.mipmap.ic_launcher_trans,
            nameId = R.string.application_icon_trans
        )
    }
}