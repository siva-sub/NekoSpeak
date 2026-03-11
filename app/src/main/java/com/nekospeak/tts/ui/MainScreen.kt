package com.nekospeak.tts.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nekospeak.tts.data.PrefsManager
import com.nekospeak.tts.ui.navigation.Screen
import com.nekospeak.tts.ui.screens.SettingsScreen
import com.nekospeak.tts.ui.screens.VoicesScreen
import com.nekospeak.tts.ui.screens.VoiceRecorderScreen
import com.nekospeak.tts.ui.screens.OnboardingScreen
import com.nekospeak.tts.ui.screens.ModelManagerScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    
    val startDestination = if (prefs.isOnboardingComplete) Screen.Voices.route else Screen.Onboarding.route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // State for voice cloning callback (Path, Name, Transcript)
    var pendingVoiceCloneData by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(navController = navController)
            }
            
            composable(Screen.Voices.route) {
                VoicesScreen(
                    navController = navController,
                    pendingVoiceCloneData = pendingVoiceCloneData,
                    onVoiceCloneHandled = { pendingVoiceCloneData = null }
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            
            composable(Screen.VoiceRecorder.route) {
                VoiceRecorderScreen(
                    navController = navController,
                    onVoiceRecorded = { path: String, name: String, transcript: String ->
                        pendingVoiceCloneData = Triple(path, name, transcript)
                    }
                )
            }
            
            composable(Screen.ModelManager.route) {
                com.nekospeak.tts.ui.screens.ModelManagerScreen(navController = navController)
            }
        }
    }
}
