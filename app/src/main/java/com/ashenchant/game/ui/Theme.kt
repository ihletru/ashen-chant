package com.ashenchant.game.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Paleta w klimacie Heretic: ciemne tło, bursztynowy akcent.
private val Amber = Color(0xFFE8A33D)
private val AmberDark = Color(0xFF8A5A12)
private val Parchment = Color(0xFFE8DCC0)
private val BloodRed = Color(0xFF8C2F2F)

/**
 * Zielony stanu „gotowe" w launcherze (dane silnika). Material nie ma roli
 * „sukces", a czerwień `error` znaczyłaby coś odwrotnego — stąd osobna stała.
 */
val ReadyGreen = Color(0xFF7CCB6A)

private val HereticDarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1206),
    primaryContainer = AmberDark,
    onPrimaryContainer = Parchment,
    secondary = BloodRed,
    onSecondary = Parchment,
    background = Color(0xFF0D0B08),
    onBackground = Parchment,
    surface = Color(0xFF171310),
    onSurface = Parchment,
    surfaceVariant = Color(0xFF241D16),
    onSurfaceVariant = Color(0xFFC9B99A),
    error = Color(0xFFCF6679),
)

/**
 * Motyw aplikacji. Zawsze ciemny — gra i tak startuje w pełnym ekranie,
 * a launcher ma nie razić jasnym tłem.
 */
@Composable
fun HereticTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HereticDarkColors,
        typography = HereticTypography,
        content = content,
    )
}

private val HereticTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = FontFamily.Serif,
            letterSpacing = 6.sp,
        ),
        // ⚠️ Tytuł launchera idzie tym stylem, NIE `displayLarge`: przy 57sp
        // „ASHEN CHANT" łamał się na dwa wiersze i zjadał 180 px wysokości,
        // przez co zawartość nie mieściła się nad przyciskiem „Play"
        // (Milosz 15.10.2026: „przycisk play nie będzie się chował").
        headlineLarge = base.headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            letterSpacing = 4.sp,
        ),
    )
}
