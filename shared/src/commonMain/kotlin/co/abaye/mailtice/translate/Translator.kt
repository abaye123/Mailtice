package co.abaye.mailtice.translate

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import co.abaye.mailtice.provider.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Machine translation of a message, behind one interface so the engine can be swapped. */
interface Translator {
    /** Each of [texts] in [target] (a language code such as "he"), and the language they were written in. */
    suspend fun translate(texts: List<String>, target: String): Translation
}

@Immutable
data class Translation(val sourceLanguage: String, val texts: List<String>)

/** Whether the reader offers a translation, and into which language; provided at the root. */
@Immutable
data class TranslationOffer(val enabled: Boolean, val target: String)

val LocalTranslationOffer = staticCompositionLocalOf { TranslationOffer(enabled = false, target = "en") }

private const val GTX_URL = "https://translate.googleapis.com/translate_a/single"

/** Well under the few thousand characters the endpoint takes in one request. */
private const val MAX_CHUNK = 4_000

/**
 * Google Translate through the endpoint its own browser widgets call (`client=gtx`): no key and no
 * account, but also no contract - Google may slow it down or close it at any time, which surfaces
 * as a failed translation the user can retry. [Translator] keeps the official API one class away.
 *
 * Long text goes in blocks of whole lines, so line breaks survive and no block is too long.
 */
class GtxTranslator(private val http: HttpClient) : Translator {

    override suspend fun translate(texts: List<String>, target: String): Translation {
        var source = ""
        val out = texts.map { text ->
            val parts = mutableListOf<String>()
            for (block in blocks(text)) {
                if (block.isBlank()) {
                    parts += block
                } else {
                    val (language, translated) = request(block, target)
                    if (source.isEmpty()) source = language
                    parts += translated
                }
            }
            parts.joinToString("\n")
        }
        return Translation(source, out)
    }

    private suspend fun request(text: String, target: String): Pair<String, String> {
        // POST, so a long block does not have to fit in a URL.
        val response = http.submitForm(GTX_URL, parameters { append("q", text) }) {
            parameter("client", "gtx")
            parameter("sl", "auto")
            parameter("tl", target)
            parameter("dt", "t")
        }
        if (!response.status.isSuccess()) throw ProviderException.of(response.status.value, response.bodyAsText())
        return parseGtx(response.bodyAsText())
    }
}

/**
 * The endpoint answers with nested arrays: the translated sentences first, each as
 * [translation, original, ...], and the detected language at index 2.
 */
internal fun parseGtx(body: String): Pair<String, String> {
    val root = Json.parseToJsonElement(body).jsonArray
    val sentences = root.getOrNull(0) as? JsonArray
    val text = sentences.orEmpty().joinToString("") { sentence ->
        (sentence as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()
    }
    val language = (root.getOrNull(2) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
    return language to text
}

/** [text] cut at line breaks into blocks of at most [MAX_CHUNK] characters; an overlong line is cut at spaces. */
internal fun blocks(text: String, max: Int = MAX_CHUNK): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        out += current.toString()
        current.clear()
    }
    for (line in text.split('\n')) {
        val pieces = if (line.length <= max) listOf(line) else line.chunkedAtSpaces(max)
        for (piece in pieces) {
            if (current.isNotEmpty() && current.length + 1 + piece.length > max) flush()
            if (current.isNotEmpty()) current.append('\n')
            current.append(piece)
        }
    }
    flush()
    return out
}

private fun String.chunkedAtSpaces(max: Int): List<String> {
    val out = mutableListOf<String>()
    var rest = this
    while (rest.length > max) {
        val cut = rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
        out += rest.substring(0, cut)
        rest = rest.substring(cut).trimStart()
    }
    out += rest
    return out
}
