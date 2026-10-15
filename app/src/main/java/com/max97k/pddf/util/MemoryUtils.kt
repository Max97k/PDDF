package com.max97k.pddf.util

object MemoryUtils {

    fun clearPdfBoxFontCache() {
        try {
            val mapper = com.tom_roush.pdfbox.pdmodel.font.FontMappers.instance()
            if (mapper.javaClass.name == "com.tom_roush.pdfbox.pdmodel.font.FontMapperImpl") {
                val fontCacheMethod = mapper.javaClass.getMethod("getFontCache")
                val fontCache = fontCacheMethod.invoke(mapper)
                if (fontCache != null) {
                    val cacheField = fontCache.javaClass.getDeclaredField("cache")
                    cacheField.isAccessible = true
                    val cacheMap = cacheField.get(fontCache) as? java.util.Map<*, *>
                    cacheMap?.clear()
                }
            }
        } catch (_: Exception) {
            // Ignore reflection errors if PDFBox internals change
        }
    }

    fun clearTemporaryBitmaps() {
        // Explicit hook for future expansion if a global bitmap cache is added.
        // Currently, Bitmaps are scoped to local composables (e.g. PdfPageItem) and garbage collected natively.
    }

    fun getOptimalMemorySetting(fileSizeBytes: Long): com.tom_roush.pdfbox.io.MemoryUsageSetting {
        val threshold = 50L * 1024L * 1024L // 50MB
        return if (fileSizeBytes > threshold) {
            com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
        } else {
            com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(threshold)
        }
    }

    fun wipe(chars: CharArray) {
        chars.fill('\u0000')
    }

    fun wipe(bytes: ByteArray) {
        bytes.fill(0)
    }

    fun wipe(builder: StringBuilder) {
        for (i in 0 until builder.length) {
            builder.setCharAt(i, '\u0000')
        }
        builder.setLength(0)
    }
}
