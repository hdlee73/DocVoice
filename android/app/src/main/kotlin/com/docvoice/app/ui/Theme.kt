package com.docvoice.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape

/** 데스크톱 앱과 같은 파스텔 팔레트 */
object Pastel {
    val Bg = Color(0xFFF7F5FF)
    val Card = Color(0xFFFFFFFF)
    val Border = Color(0xFFE6E1F5)
    val Ink = Color(0xFF4A4560)
    val Muted = Color(0xFF8E88A8)
    val Lavender = Color(0xFFA594F0)
    val LavenderDeep = Color(0xFF8A77E6)
    val LavenderSoft = Color(0xFFECE8FC)
    val Mint = Color(0xFF8FD5B8)
    val MintDeep = Color(0xFF4FAE8A)
    val MintSoft = Color(0xFFE4F6EE)
    val Rose = Color(0xFFFFC9DE)
    val RoseSoft = Color(0xFFFFEEF5)
    val RoseDeep = Color(0xFFD9638F)
}

private val scheme = lightColorScheme(
    primary = Pastel.Lavender,
    onPrimary = Color.White,
    primaryContainer = Pastel.LavenderSoft,
    onPrimaryContainer = Pastel.Ink,
    secondary = Pastel.Mint,
    onSecondary = Pastel.Ink,
    secondaryContainer = Pastel.MintSoft,
    onSecondaryContainer = Pastel.Ink,
    tertiary = Pastel.Rose,
    background = Pastel.Bg,
    onBackground = Pastel.Ink,
    surface = Pastel.Card,
    onSurface = Pastel.Ink,
    surfaceVariant = Pastel.LavenderSoft,
    onSurfaceVariant = Pastel.Muted,
    outline = Pastel.Border,
    outlineVariant = Pastel.Border,
    error = Pastel.RoseDeep,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pastel.Ink),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Pastel.Ink),
    bodyLarge = TextStyle(fontSize = 15.sp, color = Pastel.Ink),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Pastel.Ink),
    bodySmall = TextStyle(fontSize = 12.sp, color = Pastel.Muted),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun DocVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
