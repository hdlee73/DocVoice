package com.docvoice.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS 시스템 색 */
object Ios {
    val Bg = Color(0xFFF2F2F7)
    val Card = Color(0xFFFFFFFF)
    val Label = Color(0xFF000000)
    val Secondary = Color(0xFF8E8E93)
    val Tertiary = Color(0xFFC7C7CC)
    val Separator = Color(0xFFD1D1D6)
    val Fill = Color(0xFFE5E5EA)
    val SegmentTrack = Color(0x1F767680)
    val Blue = Color(0xFF007AFF)
    val Red = Color(0xFFFF3B30)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
}

private val scheme = lightColorScheme(
    primary = Ios.Blue,
    onPrimary = Color.White,
    background = Ios.Bg,
    onBackground = Ios.Label,
    surface = Ios.Card,
    onSurface = Ios.Label,
    outline = Ios.Separator,
    error = Ios.Red,
)

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 17.sp, color = Ios.Label),
    bodyMedium = TextStyle(fontSize = 15.sp, color = Ios.Label),
    bodySmall = TextStyle(fontSize = 13.sp, color = Ios.Secondary),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ios.Label),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun DocVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(14.dp)),
        content = content,
    )
}
