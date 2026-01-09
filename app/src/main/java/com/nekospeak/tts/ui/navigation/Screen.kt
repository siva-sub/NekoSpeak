package com.nekospeak.tts.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Onboarding : Screen("onboarding", "Onboarding")
    object Voices : Screen("voices", "Voices")
    object Models : Screen("models", "Models")
    object Settings : Screen("settings", "Settings")
}
