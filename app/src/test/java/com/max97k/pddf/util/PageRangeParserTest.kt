package com.max97k.pddf.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun testEmptyOrBlank() {
        assertEquals(emptyList<Int>(), PageRangeParser.parse("", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("   ", 10))
    }

    @Test
    fun testSinglePage() {
        assertEquals(listOf(1), PageRangeParser.parse("1", 10))
        assertEquals(listOf(5), PageRangeParser.parse(" 5 ", 10))
    }

    @Test
    fun testMultipleSinglePages() {
        assertEquals(listOf(1, 3, 5), PageRangeParser.parse("1,3,5", 10))
        assertEquals(listOf(1, 3, 5), PageRangeParser.parse("1, 3 , 5", 10))
    }

    @Test
    fun testValidRange() {
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("1-3", 10))
        assertEquals(listOf(2, 3, 4, 5), PageRangeParser.parse(" 2 - 5 ", 10))
    }

    @Test
    fun testMixedRangesAndSingles() {
        assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), PageRangeParser.parse("1-3, 5, 8-10", 10))
    }

    @Test
    fun testDeduplicationAndSorting() {
        assertEquals(listOf(1, 2, 3, 4, 5), PageRangeParser.parse("1-3, 2-5, 1", 10))
        assertEquals(listOf(1, 2, 3), PageRangeParser.parse("3, 2, 1", 10))
    }

    @Test
    fun testOutOfBounds() {
        // Out of bounds page should make the entire string invalid according to current logic
        assertEquals(emptyList<Int>(), PageRangeParser.parse("0", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("11", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("1-11", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("1, 15", 10))
    }

    @Test
    fun testInvalidFormat() {
        assertEquals(emptyList<Int>(), PageRangeParser.parse("a", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("1-a", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("1-3-5", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("1,-,3", 10))
        assertEquals(emptyList<Int>(), PageRangeParser.parse("3-1", 10)) // start > end
    }
}
