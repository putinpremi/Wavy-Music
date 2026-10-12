package com.wavymusic.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WavyBrandTest {

    @Test
    fun brandIdentityMatchesProductSpecification() {
        assertEquals("Wavy Music", WavyBrand.NAME)
        assertEquals("Feel every frequency.", WavyBrand.TAGLINE)
        assertEquals("com.wavymusic.app", WavyBrand.APPLICATION_ID)
    }

    @Test
    fun brandPaletteMatchesProductSpecification() {
        assertEquals(0xFF080D19L, WavyBrand.BACKGROUND_ARGB)
        assertEquals(0xFF172235L, WavyBrand.SURFACE_ARGB)
        assertEquals(0xFF48E5FFL, WavyBrand.CYAN_ARGB)
        assertEquals(0xFF9272FFL, WavyBrand.VIOLET_ARGB)
    }
}