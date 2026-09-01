package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Tier 1 & Tier 4: Internationalization, Plurals & Multi-Locale Verification Tests.
 * Ensures translation completeness across supported locales, format argument safety,
 * XML integrity, and plural handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizationAndPluralsTest {

    private val resDir = File("src/main/res")

    private fun parseStringXml(file: File): Map<String, String> {
        val strings = mutableMapOf<String, String>()
        if (!file.exists()) return strings

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node is Element) {
                val name = node.getAttribute("name")
                val text = node.textContent
                strings[name] = text
            }
        }
        return strings
    }

    private fun parsePluralsXml(file: File): Map<String, Map<String, String>> {
        val plurals = mutableMapOf<String, MutableMap<String, String>>()
        if (!file.exists()) return plurals

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val pluralsNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralsNodes.length) {
            val node = pluralsNodes.item(i)
            if (node is Element) {
                val name = node.getAttribute("name")
                val items = mutableMapOf<String, String>()
                val itemNodes = node.getElementsByTagName("item")
                for (j in 0 until itemNodes.length) {
                    val itemNode = itemNodes.item(j)
                    if (itemNode is Element) {
                        val quantity = itemNode.getAttribute("quantity")
                        val text = itemNode.textContent
                        items[quantity] = text
                    }
                }
                plurals[name] = items
            }
        }
        return plurals
    }

    private fun parseLocalesConfig(file: File): List<String> {
        val locales = mutableListOf<String>()
        if (!file.exists()) return locales

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val localeNodes = doc.getElementsByTagName("locale")
        for (i in 0 until localeNodes.length) {
            val node = localeNodes.item(i)
            if (node is Element) {
                val name = node.getAttribute("android:name")
                locales.add(name)
            }
        }
        return locales
    }

    private fun extractFormatSpecifiers(str: String): List<String> {
        val regex = Regex("%(\\d+\\$)?[-#+ 0,(<]*[0-9]*(\\.[0-9]+)?([a-zA-Z])")
        return regex.findAll(str).map { it.value }.toList()
    }

    @Test
    fun defaultStrings_fileIsValidAndNonEmpty() {
        val defaultStringsFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist", defaultStringsFile.exists())
        val defaultStrings = parseStringXml(defaultStringsFile)
        assertTrue("Default strings.xml should contain at least 40 string keys", defaultStrings.size >= 40)
    }

    @Test
    fun localizedStrings_haveKeyParityAndMatchingFormatSpecifiers() {
        val defaultStringsFile = File(resDir, "values/strings.xml")
        val defaultStrings = parseStringXml(defaultStringsFile)

        val localeDirs = listOf("values-zh-rTW", "values-zh-rCN", "values-es", "values-ja", "values-de", "values-fr")

        for (localeDir in localeDirs) {
            val file = File(resDir, "$localeDir/strings.xml")
            assertTrue("Locale file $localeDir/strings.xml must exist", file.exists())
            val localizedStrings = parseStringXml(file)

            // Verify every default key is present in localized strings
            for ((key, defaultValue) in defaultStrings) {
                assertTrue(
                    "Locale $localeDir is missing string resource '$key'",
                    localizedStrings.containsKey(key)
                )

                val localizedValue = localizedStrings[key] ?: ""
                assertFalse(
                    "Locale $localeDir has empty translation for '$key'",
                    localizedValue.isBlank()
                )

                // Verify format specifiers match
                val defaultSpecifiers = extractFormatSpecifiers(defaultValue)
                val localizedSpecifiers = extractFormatSpecifiers(localizedValue)

                val defaultTypes = defaultSpecifiers.map { it.last() }.sorted()
                val localizedTypes = localizedSpecifiers.map { it.last() }.sorted()

                assertEquals(
                    "Locale $localeDir format specifiers for '$key' mismatch. Expected $defaultSpecifiers, got $localizedSpecifiers",
                    defaultTypes,
                    localizedTypes
                )
            }

            // Verify no unexpected extra keys in localized strings
            for (key in localizedStrings.keys) {
                assertTrue(
                    "Locale $localeDir contains orphan string key '$key' not present in default strings.xml",
                    defaultStrings.containsKey(key)
                )
            }
        }
    }

    @Test
    fun plurals_haveParityAndMatchingFormatSpecifiers() {
        val defaultFile = File(resDir, "values/strings.xml")
        val defaultPlurals = parsePluralsXml(defaultFile)

        assertTrue("Default plurals must contain selected_files_count", defaultPlurals.containsKey("selected_files_count"))
        assertTrue("Default plurals must contain processing_files_count", defaultPlurals.containsKey("processing_files_count"))

        val localeDirs = listOf("values-zh-rTW", "values-zh-rCN", "values-es", "values-ja", "values-de", "values-fr")

        for (localeDir in localeDirs) {
            val file = File(resDir, "$localeDir/strings.xml")
            assertTrue("Locale file $localeDir/strings.xml must exist", file.exists())
            val localizedPlurals = parsePluralsXml(file)

            for (pluralName in defaultPlurals.keys) {
                assertTrue(
                    "Locale $localeDir is missing plural '$pluralName'",
                    localizedPlurals.containsKey(pluralName)
                )

                val defaultItems = defaultPlurals[pluralName] ?: emptyMap()
                val localizedItems = localizedPlurals[pluralName] ?: emptyMap()

                assertTrue("Locale $localeDir plural '$pluralName' items must not be empty", localizedItems.isNotEmpty())

                // Check that all localized items have valid format specifiers matching %1$d or %d
                for ((quantity, text) in localizedItems) {
                    assertFalse("Locale $localeDir plural '$pluralName' ($quantity) is blank", text.isBlank())
                    val specifiers = extractFormatSpecifiers(text)
                    assertTrue(
                        "Locale $localeDir plural '$pluralName' ($quantity) must contain %d or %1\$d integer format specifier",
                        specifiers.any { it.endsWith("d") }
                    )
                }
            }
        }
    }

    @Test
    fun localesConfig_containsAllSupportedLocales() {
        val localesConfigFile = File(resDir, "xml/locales_config.xml")
        assertTrue("locales_config.xml must exist", localesConfigFile.exists())
        val locales = parseLocalesConfig(localesConfigFile)

        val requiredLocales = listOf("en", "zh-TW", "zh-CN", "ja", "es", "de", "fr")
        for (req in requiredLocales) {
            assertTrue("locales_config.xml must declare locale '$req'", locales.contains(req))
        }
    }

    @Test
    fun accessibilityContentDescriptions_arePresentAndNonEmpty() {
        val defaultStringsFile = File(resDir, "values/strings.xml")
        val defaultStrings = parseStringXml(defaultStringsFile)

        val contentDescKeys = defaultStrings.keys.filter { it.startsWith("content_desc_") }
        assertTrue("Must define accessibility content descriptions", contentDescKeys.isNotEmpty())

        for (key in contentDescKeys) {
            val value = defaultStrings[key]
            assertNotNull("Content description '$key' must not be null", value)
            assertTrue("Content description '$key' must not be blank", value!!.isNotBlank())
        }
    }

    @Test
    fun runtimeLocaleContextSwitching_resolvesLocalizedResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Test English (en)
        val enConfig = Configuration(context.resources.configuration)
        enConfig.setLocale(Locale.ENGLISH)
        val enContext = context.createConfigurationContext(enConfig)
        assertEquals("PDF Decryptor", enContext.getString(R.string.app_name))
        assertEquals("Select Encrypted PDFs", enContext.getString(R.string.btn_select_pdfs))
        assertEquals("1 file selected", enContext.resources.getQuantityString(R.plurals.selected_files_count, 1, 1))
        assertEquals("3 files selected", enContext.resources.getQuantityString(R.plurals.selected_files_count, 3, 3))
        assertEquals("Decrypting 1 file...", enContext.resources.getQuantityString(R.plurals.processing_files_count, 1, 1))
        assertEquals("Decrypting 4 files...", enContext.resources.getQuantityString(R.plurals.processing_files_count, 4, 4))

        // 2. Test Traditional Chinese (zh-TW)
        val twConfig = Configuration(context.resources.configuration)
        twConfig.setLocale(Locale.TRADITIONAL_CHINESE)
        val twContext = context.createConfigurationContext(twConfig)
        assertEquals("PDF 解密工具", twContext.getString(R.string.app_name))
        assertEquals("選擇加密的 PDF 檔案", twContext.getString(R.string.btn_select_pdfs))
        assertEquals("已選擇 5 個檔案", twContext.resources.getQuantityString(R.plurals.selected_files_count, 5, 5))
        assertEquals("正在解密 5 個檔案...", twContext.resources.getQuantityString(R.plurals.processing_files_count, 5, 5))

        // 3. Test Simplified Chinese (zh-CN)
        val cnConfig = Configuration(context.resources.configuration)
        cnConfig.setLocale(Locale.SIMPLIFIED_CHINESE)
        val cnContext = context.createConfigurationContext(cnConfig)
        assertEquals("PDF 解密工具", cnContext.getString(R.string.app_name))
        assertEquals("选择加密的 PDF 文件", cnContext.getString(R.string.btn_select_pdfs))
        assertEquals("已选择 5 个文件", cnContext.resources.getQuantityString(R.plurals.selected_files_count, 5, 5))
        assertEquals("正在解密 5 个文件...", cnContext.resources.getQuantityString(R.plurals.processing_files_count, 5, 5))

        // 4. Test Japanese (ja)
        val jaConfig = Configuration(context.resources.configuration)
        jaConfig.setLocale(Locale.JAPANESE)
        val jaContext = context.createConfigurationContext(jaConfig)
        assertEquals("暗号化されたPDFを選択", jaContext.getString(R.string.btn_select_pdfs))
        assertEquals("3 個のファイルを選択", jaContext.resources.getQuantityString(R.plurals.selected_files_count, 3, 3))
        assertEquals("3 個のファイルを復号化中...", jaContext.resources.getQuantityString(R.plurals.processing_files_count, 3, 3))

        // 5. Test Spanish (es)
        val esConfig = Configuration(context.resources.configuration)
        esConfig.setLocale(Locale("es"))
        val esContext = context.createConfigurationContext(esConfig)
        assertEquals("Seleccionar PDFs cifrados", esContext.getString(R.string.btn_select_pdfs))
        assertEquals("1 archivo seleccionado", esContext.resources.getQuantityString(R.plurals.selected_files_count, 1, 1))
        assertEquals("2 archivos seleccionados", esContext.resources.getQuantityString(R.plurals.selected_files_count, 2, 2))
        assertEquals("Descifrando 1 archivo...", esContext.resources.getQuantityString(R.plurals.processing_files_count, 1, 1))
        assertEquals("Descifrando 2 archivos...", esContext.resources.getQuantityString(R.plurals.processing_files_count, 2, 2))

        // 6. Test German (de)
        val deConfig = Configuration(context.resources.configuration)
        deConfig.setLocale(Locale.GERMAN)
        val deContext = context.createConfigurationContext(deConfig)
        assertEquals("Verschlüsselte PDFs auswählen", deContext.getString(R.string.btn_select_pdfs))
        assertEquals("1 Datei ausgewählt", deContext.resources.getQuantityString(R.plurals.selected_files_count, 1, 1))
        assertEquals("2 Dateien ausgewählt", deContext.resources.getQuantityString(R.plurals.selected_files_count, 2, 2))
        assertEquals("Entschlüssle 1 Datei...", deContext.resources.getQuantityString(R.plurals.processing_files_count, 1, 1))
        assertEquals("Entschlüssle 2 Dateien...", deContext.resources.getQuantityString(R.plurals.processing_files_count, 2, 2))

        // 7. Test French (fr)
        val frConfig = Configuration(context.resources.configuration)
        frConfig.setLocale(Locale.FRENCH)
        val frContext = context.createConfigurationContext(frConfig)
        assertEquals("Sélectionner des PDF chiffrés", frContext.getString(R.string.btn_select_pdfs))
        assertEquals("1 fichier sélectionné", frContext.resources.getQuantityString(R.plurals.selected_files_count, 1, 1))
        assertEquals("2 fichiers sélectionnés", frContext.resources.getQuantityString(R.plurals.selected_files_count, 2, 2))
        assertEquals("Déchiffrement de 1 fichier...", frContext.resources.getQuantityString(R.plurals.processing_files_count, 1, 1))
        assertEquals("Déchiffrement de 2 fichiers...", frContext.resources.getQuantityString(R.plurals.processing_files_count, 2, 2))
    }
}
