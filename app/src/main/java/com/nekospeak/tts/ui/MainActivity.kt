package com.nekospeak.tts.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nekospeak.tts.data.PrefsManager
import com.nekospeak.tts.ui.theme.AppTheme
import com.nekospeak.tts.ui.theme.DarkThemeMode
import com.nekospeak.tts.ui.theme.NekoSpeakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThemedApp()
        }
    }
}

@Composable
fun ThemedApp() {
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    
    // Collect reactivity
    val counter by prefs.themeCounter.collectAsState(initial = 0)
    
    // Read and parse theme settings
    val appTheme = remember(counter) {
        try { AppTheme.valueOf(prefs.appTheme) } catch (e: Exception) { AppTheme.BLUE }
    }
    
    val darkMode = remember(counter) {
        try { DarkThemeMode.valueOf(prefs.darkMode) } catch (e: Exception) { DarkThemeMode.FOLLOW_SYSTEM }
    }
    
    NekoSpeakTheme(
        appTheme = appTheme,
        darkThemeMode = darkMode
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen()
        }
    }
}
