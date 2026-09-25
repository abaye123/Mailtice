package co.abaye.mailtice.provider

/** Decodes [bytes] in a MIME charset (UTF-8, windows-1255, iso-8859-8...), falling back to UTF-8. */
expect fun decodeText(bytes: ByteArray, charset: String): String
