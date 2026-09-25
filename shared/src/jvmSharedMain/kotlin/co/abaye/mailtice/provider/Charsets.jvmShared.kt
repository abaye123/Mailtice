package co.abaye.mailtice.provider

import java.nio.charset.Charset

actual fun decodeText(bytes: ByteArray, charset: String): String {
    val cs = runCatching { Charset.forName(charset.trim().trim('"')) }.getOrDefault(Charsets.UTF_8)
    return String(bytes, cs)
}
