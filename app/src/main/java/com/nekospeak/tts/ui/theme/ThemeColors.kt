/*
 * NekoSpeak Reader - Theme Colors
 * Color schemes for different themes
 * Adapted from Book-Story (GPL-3.0 reference)
 */

package com.nekospeak.tts.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ============ BLUE THEME ============
private val BluePrimaryLight = Color(0xFF485D92)
private val BlueOnPrimaryLight = Color(0xFFFFFFFF)
private val BluePrimaryContainerLight = Color(0xFFDAE2FF)
private val BlueBackgroundLight = Color(0xFFFAF8FF)
private val BlueOnBackgroundLight = Color(0xFF1A1B21)
private val BlueSurfaceLight = Color(0xFFFAF8FF)
private val BlueOnSurfaceLight = Color(0xFF1A1B21)
private val BlueTertiaryContainerLight = Color(0xFFFED7F9)

private val BluePrimaryDark = Color(0xFFB1C5FF)
private val BlueOnPrimaryDark = Color(0xFF172E60)
private val BluePrimaryContainerDark = Color(0xFF304578)
private val BlueBackgroundDark = Color(0xFF121318)
private val BlueOnBackgroundDark = Color(0xFFE2E2E9)
private val BlueSurfaceDark = Color(0xFF121318)
private val BlueOnSurfaceDark = Color(0xFFE2E2E9)
private val BlueTertiaryContainerDark = Color(0xFF5A3D59)

// Pure dark (OLED)
private val BlueSurfacePureDark = Color(0xFF000000)
private val BlueBackgroundPureDark = Color(0xFF000000)

fun blueColorScheme(isDark: Boolean, isPureDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = BluePrimaryDark,
            onPrimary = BlueOnPrimaryDark,
            primaryContainer = BluePrimaryContainerDark,
            background = if (isPureDark) BlueBackgroundPureDark else BlueBackgroundDark,
            onBackground = BlueOnBackgroundDark,
            surface = if (isPureDark) BlueSurfacePureDark else BlueSurfaceDark,
            onSurface = BlueOnSurfaceDark,
            tertiaryContainer = BlueTertiaryContainerDark
        )
    } else {
        lightColorScheme(
            primary = BluePrimaryLight,
            onPrimary = BlueOnPrimaryLight,
            primaryContainer = BluePrimaryContainerLight,
            background = BlueBackgroundLight,
            onBackground = BlueOnBackgroundLight,
            surface = BlueSurfaceLight,
            onSurface = BlueOnSurfaceLight,
            tertiaryContainer = BlueTertiaryContainerLight
        )
    }
}

// ============ GREEN THEME ============
private val GreenPrimaryLight = Color(0xFF3A6A3A)
private val GreenOnPrimaryLight = Color(0xFFFFFFFF)
private val GreenPrimaryContainerLight = Color(0xFFBCF0B4)
private val GreenBackgroundLight = Color(0xFFF7FBF1)
private val GreenOnBackgroundLight = Color(0xFF191D17)

private val GreenPrimaryDark = Color(0xFFA1D49A)
private val GreenOnPrimaryDark = Color(0xFF0B3910)
private val GreenPrimaryContainerDark = Color(0xFF225125)
private val GreenBackgroundDark = Color(0xFF111510)
private val GreenOnBackgroundDark = Color(0xFFDFE4D8)

fun greenColorScheme(isDark: Boolean, isPureDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = GreenPrimaryDark,
            onPrimary = GreenOnPrimaryDark,
            primaryContainer = GreenPrimaryContainerDark,
            background = if (isPureDark) Color.Black else GreenBackgroundDark,
            onBackground = GreenOnBackgroundDark,
            surface = if (isPureDark) Color.Black else GreenBackgroundDark,
            onSurface = GreenOnBackgroundDark
        )
    } else {
        lightColorScheme(
            primary = GreenPrimaryLight,
            onPrimary = GreenOnPrimaryLight,
            primaryContainer = GreenPrimaryContainerLight,
            background = GreenBackgroundLight,
            onBackground = GreenOnBackgroundLight,
            surface = GreenBackgroundLight,
            onSurface = GreenOnBackgroundLight
        )
    }
}

// ============ PURPLE THEME ============
private val PurplePrimaryLight = Color(0xFF6750A4)
private val PurpleOnPrimaryLight = Color(0xFFFFFFFF)
private val PurplePrimaryContainerLight = Color(0xFFE9DDFF)
private val PurpleBackgroundLight = Color(0xFFFEF7FF)
private val PurpleOnBackgroundLight = Color(0xFF1D1B20)

private val PurplePrimaryDark = Color(0xFFCFBCFF)
private val PurpleOnPrimaryDark = Color(0xFF381E72)
private val PurplePrimaryContainerDark = Color(0xFF4F378A)
private val PurpleBackgroundDark = Color(0xFF141218)
private val PurpleOnBackgroundDark = Color(0xFFE6E0E9)

fun purpleColorScheme(isDark: Boolean, isPureDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = PurplePrimaryDark,
            onPrimary = PurpleOnPrimaryDark,
            primaryContainer = PurplePrimaryContainerDark,
            background = if (isPureDark) Color.Black else PurpleBackgroundDark,
            onBackground = PurpleOnBackgroundDark,
            surface = if (isPureDark) Color.Black else PurpleBackgroundDark,
            onSurface = PurpleOnBackgroundDark
        )
    } else {
        lightColorScheme(
            primary = PurplePrimaryLight,
            onPrimary = PurpleOnPrimaryLight,
            primaryContainer = PurplePrimaryContainerLight,
            background = PurpleBackgroundLight,
            onBackground = PurpleOnBackgroundLight,
            surface = PurpleBackgroundLight,
            onSurface = PurpleOnBackgroundLight
        )
    }
}

// ============ PINK THEME ============
private val PinkPrimaryLight = Color(0xFF984062)
private val PinkOnPrimaryLight = Color(0xFFFFFFFF)
private val PinkPrimaryContainerLight = Color(0xFFFFD9E3)
private val PinkBackgroundLight = Color(0xFFFFF8F8)
private val PinkOnBackgroundLight = Color(0xFF22191C)

private val PinkPrimaryDark = Color(0xFFFFB0C9)
private val PinkOnPrimaryDark = Color(0xFF5E1134)
private val PinkPrimaryContainerDark = Color(0xFF7B294A)
private val PinkBackgroundDark = Color(0xFF191114)
private val PinkOnBackgroundDark = Color(0xFFF0DEE2)

fun pinkColorScheme(isDark: Boolean, isPureDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = PinkPrimaryDark,
            onPrimary = PinkOnPrimaryDark,
            primaryContainer = PinkPrimaryContainerDark,
            background = if (isPureDark) Color.Black else PinkBackgroundDark,
            onBackground = PinkOnBackgroundDark,
            surface = if (isPureDark) Color.Black else PinkBackgroundDark,
            onSurface = PinkOnBackgroundDark
        )
    } else {
        lightColorScheme(
            primary = PinkPrimaryLight,
            onPrimary = PinkOnPrimaryLight,
            primaryContainer = PinkPrimaryContainerLight,
            background = PinkBackgroundLight,
            onBackground = PinkOnBackgroundLight,
            surface = PinkBackgroundLight,
            onSurface = PinkOnBackgroundLight
        )
    }
}

// ============ AQUA THEME ============
private val AquaPrimaryLight = Color(0xFF006874)
private val AquaOnPrimaryLight = Color(0xFFFFFFFF)
private val AquaPrimaryContainerLight = Color(0xFF9EEFFD)
private val AquaBackgroundLight = Color(0xFFF4FAFB)
private val AquaOnBackgroundLight = Color(0xFF161D1E)

private val AquaPrimaryDark = Color(0xFF4FD8EB)
private val AquaOnPrimaryDark = Color(0xFF00363D)
private val AquaPrimaryContainerDark = Color(0xFF004F58)
private val AquaBackgroundDark = Color(0xFF0E1415)
private val AquaOnBackgroundDark = Color(0xFFDDE4E5)

fun aquaColorScheme(isDark: Boolean, isPureDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = AquaPrimaryDark,
            onPrimary = AquaOnPrimaryDark,
            primaryContainer = AquaPrimaryContainerDark,
            background = if (isPureDark) Color.Black else AquaBackgroundDark,
            onBackground = AquaOnBackgroundDark,
            surface = if (isPureDark) Color.Black else AquaBackgroundDark,
            onSurface = AquaOnBackgroundDark
        )
    } else {
        lightColorScheme(
            primary = AquaPrimaryLight,
            onPrimary = AquaOnPrimaryLight,
            primaryContainer = AquaPrimaryContainerLight,
            background = AquaBackgroundLight,
            onBackground = AquaOnBackgroundLight,
            surface = AquaBackgroundLight,
            onSurface = AquaOnBackgroundLight
        )
    }
}

// ============ GRAY THEME ============
private val GrayPrimaryLight = Color(0xFF5D5E61)
private val GrayOnPrimaryLight = Color(0xFFFFFFFF)
private val GrayPrimaryContainerLight = Color(0xFFE2E2E6)
private val GrayBackgroundLight = Color(0xFFFCF8F8)
private val GrayOnBackgroundLight = Color(0xFF1C1B1B)

private val GrayPrimaryDark = Color(0xFFC6C6CA)
private val GrayOnPrimaryDark = Color(0xFF2F3033)
private val GrayPrimaryContainerDark = Color(0xFF474749)
private val GrayBackgroundDark = Color(0xFF141313)
private val GrayOnBackgroundDark = Color(0xFFE5E2E1)

fun grayColorScheme(isDark: Boolean, isPureDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = GrayPrimaryDark,
            onPrimary = GrayOnPrimaryDark,
            primaryContainer = GrayPrimaryContainerDark,
            background = if (isPureDark) Color.Black else GrayBackgroundDark,
            onBackground = GrayOnBackgroundDark,
            surface = if (isPureDark) Color.Black else GrayBackgroundDark,
            onSurface = GrayOnBackgroundDark
        )
    } else {
        lightColorScheme(
            primary = GrayPrimaryLight,
            onPrimary = GrayOnPrimaryLight,
            primaryContainer = GrayPrimaryContainerLight,
            background = GrayBackgroundLight,
            onBackground = GrayOnBackgroundLight,
            surface = GrayBackgroundLight,
            onSurface = GrayOnBackgroundLight
        )
    }
}
