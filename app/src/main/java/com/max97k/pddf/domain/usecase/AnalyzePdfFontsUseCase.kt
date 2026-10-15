package com.max97k.pddf.domain.usecase

import com.max97k.pddf.PdfFontInfo
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import javax.inject.Inject

class AnalyzePdfFontsUseCase @Inject constructor() {
    data class FontDiagnosticResult(
        val fonts: List<PdfFontInfo>,
        val hasMissingNonEmbeddedSystemFonts: Boolean
    )

    fun analyze(document: PDDocument): FontDiagnosticResult {
        val fonts = mutableMapOf<String, PdfFontInfo>()
        var hasMissingNonEmbeddedSystemFonts = false

        for (page in document.pages) {
            val resources = page.resources ?: continue
            val fontNames = try {
                resources.fontNames
            } catch (e: Exception) {
                continue
            }
            for (fontName in fontNames) {
                val font = try {
                    resources.getFont(fontName)
                } catch (e: Exception) {
                    continue
                } ?: continue

                val name = font.name ?: "Unknown"
                if (!fonts.containsKey(name)) {
                    val subType = font.subType ?: "Unknown"
                    val isEmbedded = font.isEmbedded
                    val isSubset = name.contains("+")

                    val isStandard14 = font is PDType1Font && font.isStandard14
                    if (!isEmbedded && !isStandard14) {
                        hasMissingNonEmbeddedSystemFonts = true
                    }

                    fonts[name] = PdfFontInfo(name, subType, isEmbedded, isSubset)
                }
            }
        }

        return FontDiagnosticResult(fonts.values.toList(), hasMissingNonEmbeddedSystemFonts)
    }
}
