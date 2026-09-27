package co.abaye.mailtice.translate

/** Writing systems, which is all a message needs to tell whether it is in the reader's language. */
enum class Script { Hebrew, Arabic, Cyrillic, Greek, Cjk, Latin }

/** The script a language is written in; anything not listed here is taken as Latin. */
fun scriptOf(language: String): Script = when (language.substringBefore('-').lowercase()) {
    "he", "iw", "yi" -> Script.Hebrew
    "ar", "fa", "ur" -> Script.Arabic
    "ru", "uk", "bg", "sr", "be", "mk", "kk" -> Script.Cyrillic
    "el" -> Script.Greek
    "zh", "ja", "ko" -> Script.Cjk
    else -> Script.Latin
}

private fun scriptOf(c: Char): Script? = when (c) {
    in '֐'..'׿', in 'יִ'..'ﭏ' -> Script.Hebrew
    in '؀'..'ۿ', in 'ݐ'..'ݿ' -> Script.Arabic
    in 'Ѐ'..'ӿ' -> Script.Cyrillic
    in 'Ͱ'..'Ͽ' -> Script.Greek
    in '぀'..'ヿ', in '一'..'鿿', in '가'..'힯' -> Script.Cjk
    else -> if (c.isLetter() && c.code < 0x0250) Script.Latin else null
}

/**
 * Whether [text] reads as written in another language than [target], judged locally by its
 * letters so the offer shows at once, before anything is sent anywhere. A message counts as the
 * reader's own when at least a quarter of its letters are in the target's script: Hebrew mail is
 * full of English names, links and signatures. Within one script (French for an English reader)
 * it cannot tell; the reader's menu still offers a translation there.
 */
fun looksForeign(text: String, target: String): Boolean {
    val counts = IntArray(Script.entries.size)
    var letters = 0
    for (c in text.take(4_000)) {
        val script = scriptOf(c) ?: continue
        counts[script.ordinal]++
        letters++
    }
    if (letters < 20) return false
    return counts[scriptOf(target).ordinal] * 4 < letters
}

private val HebrewNames = mapOf(
    "en" to "אנגלית", "he" to "עברית", "iw" to "עברית", "ar" to "ערבית", "ru" to "רוסית", "fr" to "צרפתית",
    "de" to "גרמנית", "es" to "ספרדית", "it" to "איטלקית", "pt" to "פורטוגזית", "nl" to "הולנדית", "pl" to "פולנית",
    "uk" to "אוקראינית", "tr" to "טורקית", "fa" to "פרסית", "yi" to "יידיש", "am" to "אמהרית", "ro" to "רומנית",
    "hu" to "הונגרית", "cs" to "צ'כית", "el" to "יוונית", "zh" to "סינית", "ja" to "יפנית", "ko" to "קוריאנית",
    "hi" to "הינדי", "th" to "תאית", "vi" to "וייטנאמית", "sv" to "שוודית", "da" to "דנית", "no" to "נורווגית", "fi" to "פינית",
)

private val EnglishNames = mapOf(
    "en" to "English", "he" to "Hebrew", "iw" to "Hebrew", "ar" to "Arabic", "ru" to "Russian", "fr" to "French",
    "de" to "German", "es" to "Spanish", "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch", "pl" to "Polish",
    "uk" to "Ukrainian", "tr" to "Turkish", "fa" to "Persian", "yi" to "Yiddish", "am" to "Amharic", "ro" to "Romanian",
    "hu" to "Hungarian", "cs" to "Czech", "el" to "Greek", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
    "hi" to "Hindi", "th" to "Thai", "vi" to "Vietnamese", "sv" to "Swedish", "da" to "Danish", "no" to "Norwegian", "fi" to "Finnish",
)

/** A language code as a name in the interface language; the code itself when it is not listed. */
fun languageName(code: String, inHebrew: Boolean): String {
    val base = code.substringBefore('-').lowercase()
    return (if (inHebrew) HebrewNames else EnglishNames)[base] ?: code
}
