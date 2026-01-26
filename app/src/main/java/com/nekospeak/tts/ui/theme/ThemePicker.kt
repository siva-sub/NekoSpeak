/*
 * NekoSpeak - Theme Picker Components
 * Book-Story inspired theme selection UI
 */

package com.nekospeak.tts.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Theme picker component with mini preview cards.
 */
@Composable
fun ThemePicker(
    selectedTheme: String,
    isDarkMode: Boolean,
    isPureDark: Boolean,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val themes = listOf(
        "BLUE" to "Blue",
        "GREEN" to "Green",
        "PURPLE" to "Purple",
        "PINK" to "Pink",
        "AQUA" to "Aqua",
        "GRAY" to "Gray"
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Theme Color",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(themes, key = { it.first }) { (themeKey, themeName) ->
                ThemePreviewCard(
                    themeKey = themeKey,
                    themeName = themeName,
                    isDarkMode = isDarkMode,
                    isPureDark = isPureDark,
                    isSelected = selectedTheme == themeKey,
                    onClick = { onThemeSelected(themeKey) }
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    themeKey: String,
    themeName: String,
    isDarkMode: Boolean,
    isPureDark: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Explicitly call the color scheme functions in the same package
    val scheme = when (themeKey) {
        "BLUE" -> blueColorScheme(isDarkMode, isPureDark)
        "GREEN" -> greenColorScheme(isDarkMode, isPureDark)
        "PURPLE" -> purpleColorScheme(isDarkMode, isPureDark)
        "PINK" -> pinkColorScheme(isDarkMode, isPureDark)
        "AQUA" -> aquaColorScheme(isDarkMode, isPureDark)
        "GRAY" -> grayColorScheme(isDarkMode, isPureDark)
        else -> blueColorScheme(isDarkMode, isPureDark)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .background(scheme.surface, RoundedCornerShape(16.dp))
                .border(
                    width = 3.dp,
                    color = if (isSelected) scheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.height(12.dp).width(50.dp).background(scheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(6.dp)))
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = scheme.primary)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(50.dp).background(scheme.surfaceContainer, RoundedCornerShape(8.dp)))
                Row(
                    modifier = Modifier.fillMaxWidth().height(24.dp).background(scheme.surfaceContainer, RoundedCornerShape(8.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).background(scheme.primary, CircleShape))
                    Box(modifier = Modifier.size(12.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = themeName, style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DarkModePicker(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        "FOLLOW_SYSTEM" to "Auto",
        "LIGHT" to "Light",
        "DARK" to "Dark",
        "PURE_DARK" to "Black"
    )
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Dark Mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        val systemIsDark = isSystemInDarkTheme()

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(modes, key = { it.first }) { (modeKey, modeName) ->
                val isSelected = selectedMode == modeKey
                val isDark = when(modeKey) {
                    "DARK", "PURE_DARK" -> true
                    "LIGHT" -> false
                    else -> systemIsDark
                }
                val isPureDark = modeKey == "PURE_DARK"
                
                val backgroundColor = when {
                    isPureDark -> Color.Black
                    isDark -> Color(0xFF1C1C1E)
                    else -> Color(0xFFF2F2F7)
                }
                
                val content = if (isDark) Color(0xFF2C2C2E) else Color.White
                val accent = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onModeSelected(modeKey) }
                            .background(backgroundColor, RoundedCornerShape(12.dp))
                            .border(3.dp, accent, RoundedCornerShape(12.dp))
                            .padding(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.height(8.dp).width(35.dp).background((if(isDark) Color.White else Color.Black).copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(35.dp).background(content, RoundedCornerShape(6.dp)))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(16.dp).background(content, RoundedCornerShape(4.dp)),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Box(modifier = Modifier.size(8.dp).background((if(isDark) Color.White else Color.Black).copy(alpha = 0.2f), CircleShape))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = modeName, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
