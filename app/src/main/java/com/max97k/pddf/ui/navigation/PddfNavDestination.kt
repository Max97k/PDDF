package com.max97k.pddf.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for PDDF Jetpack Compose navigation.
 */
sealed interface PddfNavDestination {
    @Serializable
    data object Decrypt : PddfNavDestination

    @Serializable
    data object SavedPasswords : PddfNavDestination

    @Serializable
    data object Settings : PddfNavDestination

    @Serializable
    data object Viewer : PddfNavDestination
}
