package com.max97k.pddf.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PddfNavigationTest {

    @Test
    fun navDestinations_areInstantiableAndNotNull() {
        assertNotNull(PddfNavDestination.Decrypt)
        assertNotNull(PddfNavDestination.SavedPasswords)
        assertNotNull(PddfNavDestination.Settings)
        assertNotNull(PddfNavDestination.Viewer)
    }

    @Test
    fun navDestinations_areDistinct() {
        val destinations = listOf(
            PddfNavDestination.Decrypt,
            PddfNavDestination.SavedPasswords,
            PddfNavDestination.Settings,
            PddfNavDestination.Viewer
        )
        assertEquals(destinations.size, destinations.distinct().size)
    }
}
