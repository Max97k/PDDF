package com.max97k.pddf.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PddfNavigationTest {

    @Test
    fun navDestinations_haveCorrectRouteStrings() {
        assertEquals("decrypt_route", PddfNavDestination.Decrypt.route)
        assertEquals("saved_passwords_route", PddfNavDestination.SavedPasswords.route)
        assertEquals("settings_route", PddfNavDestination.Settings.route)
        assertEquals("viewer_route", PddfNavDestination.Viewer.route)
    }

    @Test
    fun navDestinations_areDistinct() {
        val routes = listOf(
            PddfNavDestination.Decrypt.route,
            PddfNavDestination.SavedPasswords.route,
            PddfNavDestination.Settings.route,
            PddfNavDestination.Viewer.route
        )
        assertEquals(routes.size, routes.distinct().size)
    }
}
