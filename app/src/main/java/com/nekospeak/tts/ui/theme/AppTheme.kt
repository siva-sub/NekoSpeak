/*
 * NekoSpeak Reader - AppTheme
 * Theme enum for selecting app color scheme
 * Adapted from Book-Story (GPL-3.0 reference)
 */

package com.nekospeak.tts.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import com.nekospeak.tts.R

/**
 * Available app themes.
 */
enum class AppTheme(
    val hasContrastOptions: Boolean,
    @StringRes val titleRes: Int
) {
    DYNAMIC(hasContrastOptions = false, titleRes = R.string.theme_dynamic),
    BLUE(hasContrastOptions = true, titleRes = R.string.theme_blue),
    GREEN(hasContrastOptions = true, titleRes = R.string.theme_green),
    PURPLE(hasContrastOptions = true, titleRes = R.string.theme_purple),
    PINK(hasContrastOptions = true, titleRes = R.string.theme_pink),
    AQUA(hasContrastOptions = true, titleRes = R.string.theme_aqua),
    GRAY(hasContrastOptions = false, titleRes = R.string.theme_gray);

    companion object {
        fun available(): List<AppTheme> {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> entries.toList()
                else -> entries.filter { it != DYNAMIC }
            }
        }
    }
}

/**
 * Theme contrast levels.
 */
enum class ThemeContrast {
    STANDARD,
    MEDIUM,
    HIGH
}

/**
 * Dark theme mode options.
 */
enum class DarkThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    PURE_DARK  // OLED black
}
