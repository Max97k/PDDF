package com.max97k.pddf.util

object PageRangeParser {
    fun parse(rangeStr: String, totalPages: Int): List<Int> {
        if (rangeStr.isBlank()) return emptyList()
        val pages = mutableSetOf<Int>()
        val parts = rangeStr.split(",")

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                if (bounds.size != 2) return emptyList() // Invalid format

                val start = bounds[0].trim().toIntOrNull()
                val end = bounds[1].trim().toIntOrNull()

                if (start == null || end == null) return emptyList()
                if (start > end) return emptyList()
                if (start <= 0 || end > totalPages) return emptyList() // Out of bounds

                for (i in start..end) {
                    pages.add(i)
                }
            } else {
                val page = trimmed.toIntOrNull()
                if (page == null) return emptyList()
                if (page <= 0 || page > totalPages) return emptyList()

                pages.add(page)
            }
        }

        return pages.sorted()
    }
}
