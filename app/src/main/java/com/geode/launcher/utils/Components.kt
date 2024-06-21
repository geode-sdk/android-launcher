package com.geode.launcher.utils

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp

@Composable
fun LabelledText(label: String, icon: @Composable (() -> Unit)? = null, modifier: Modifier = Modifier) {
    if (icon != null) {
        val iconId = "inlineIcon"
        val paddingId = "paddingIcon"

        val labelText = buildAnnotatedString {
            appendInlineContent(iconId, "[x]")
            appendInlineContent(paddingId, " ")
            append(label)
        }

        val inlineContent = mapOf(
            Pair(iconId, InlineTextContent(
                Placeholder(
                width = 24.sp,
                height = 24.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
            ) {
                icon()
            }),
            Pair(paddingId, InlineTextContent(Placeholder(width = 6.sp, height = 24.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)) {
                Spacer(modifier = Modifier.fillMaxWidth())
            })
        )

        Text(text = labelText, inlineContent = inlineContent, modifier = modifier)
    } else {
        Text(label, modifier = modifier)
    }
}
