package com.beemdevelopment.aegis.desktop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

val AegisTypography = Typography()

// Monospace for the fixed advance widths: proportional digits shift sideways on every rotation.
val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 23.sp,
    letterSpacing = 1.sp,
    textAlign = TextAlign.Start,
)

val CompactCodeTextStyle = CodeTextStyle.copy(fontSize = 20.sp)

val SmallCodeTextStyle = CodeTextStyle.copy(fontSize = 17.sp)
