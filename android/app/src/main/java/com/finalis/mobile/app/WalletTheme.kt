package com.finalis.mobile.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF64766B),
    onPrimary = Color(0xFFFFFCF8),
    secondary = Color(0xFF948B7E),
    onSecondary = Color(0xFFFFFCF8),
    tertiary = Color(0xFF81978F),
    onTertiary = Color(0xFFFFFCF8),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF26231F),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF26231F),
    surfaceVariant = Color(0xFFF2ECE2),
    onSurfaceVariant = Color(0xFF6E675C),
    outline = Color(0xFFD1C6B8),
    outlineVariant = Color(0xFFEAE1D5),
    error = Color(0xFFB06B65),
    onError = Color(0xFFFFFBFA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6E2D6),
    onPrimary = Color(0xFF1F271F),
    secondary = Color(0xFFE0D6C7),
    onSecondary = Color(0xFF241F19),
    tertiary = Color(0xFFD0E1DD),
    onTertiary = Color(0xFF1E2624),
    background = Color(0xFF151412),
    onBackground = Color(0xFFF3EEE5),
    surface = Color(0xFF1B1A18),
    onSurface = Color(0xFFF3EEE5),
    surfaceVariant = Color(0xFF282520),
    onSurfaceVariant = Color(0xFFD0C6B8),
    outline = Color(0xFF7B7368),
    outlineVariant = Color(0xFF3B3832),
    error = Color(0xFFF0B4AD),
    onError = Color(0xFF341A18),
)

private val WalletTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.45.sp,
    ),
)

private val WalletShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
)

@Composable
fun FinalisWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WalletTypography,
        shapes = WalletShapes,
        content = content,
    )
}
