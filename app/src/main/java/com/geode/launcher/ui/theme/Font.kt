package com.geode.launcher.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.geode.launcher.R

val robotoMonoFamily = FontFamily(
    Font(R.font.robotomono_medium, FontWeight.Medium),
    Font(R.font.robotomono_regular, FontWeight.Normal)
)

val quicksandFamily = FontFamily(
    Font(R.font.quicksand_semibold, FontWeight.SemiBold)
)

val launcherTitleStyle = TextStyle(
    fontFamily = quicksandFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 32.sp,
    letterSpacing = 0.sp
)