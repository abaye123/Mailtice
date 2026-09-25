package co.abaye.mailtice

import co.abaye.mailtice.domain.UiLanguage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every language must ship exactly the same keys with the same placeholders, so a string added in
 * one language and forgotten in another fails the build instead of showing a raw key in the UI.
 */
class StringResourcesTest {
    private val root = File("src/commonMain/composeResources")
    private val entry = Regex("""<string name="([a-z0-9_]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholder = Regex("""%\d+\$[sd]""")

    private fun load(dir: String): Map<String, String> =
        entry.findAll(File(root, "$dir/strings.xml").readText()).associate { it.groupValues[1] to it.groupValues[2] }

    private fun dirOf(language: UiLanguage) = if (language == UiLanguage.English) "values" else "values-${language.code}"

    @Test
    fun everyLanguageHasTheSameKeysAndPlaceholders() {
        val base = load("values")
        assertTrue(base.isNotEmpty(), "No strings found - wrong working directory?")
        UiLanguage.entries.filter { it != UiLanguage.English }.forEach { language ->
            val other = load(dirOf(language))
            assertEquals(base.keys.sorted(), other.keys.sorted(), "Keys differ in ${dirOf(language)}")
            base.forEach { (key, text) ->
                assertEquals(
                    placeholder.findAll(text).map { it.value }.toSet(),
                    placeholder.findAll(other.getValue(key)).map { it.value }.toSet(),
                    "Placeholders differ for '$key' in ${dirOf(language)}",
                )
            }
        }
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals(UiLanguage.English, UiLanguage.fromCode("fr-FR"))
        assertEquals(UiLanguage.Hebrew, UiLanguage.fromCode("iw_IL"))
    }
}
