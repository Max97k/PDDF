package com.max97k.pddf.ui.navigation

/**
 * Type-safe navigation destinations for PDDF Jetpack Compose navigation.
 */
sealed class PddfNavDestination(val route: String) {
    data object Decrypt : PddfNavDestination("decrypt_route")
    data object SavedPasswords : PddfNavDestination("saved_passwords_route")
    data object Settings : PddfNavDestination("settings_route")
    data object Viewer : PddfNavDestination("viewer_route")
}
