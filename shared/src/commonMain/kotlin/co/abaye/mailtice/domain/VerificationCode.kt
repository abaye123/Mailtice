package co.abaye.mailtice.domain

/**
 * Finds the one-time code in a sign-in or verification mail, so it can be copied without opening
 * the message ("Your verification code is 482 913", "G-482913 is your Google code",
 * "קוד האימות שלך: 482913").
 *
 * A number counts only near words that say it is such a code. Plain "code" or "PIN" must sit right
 * next to it and not belong to a coupon, a discount or a postal code. Prices, dates, times, phone
 * numbers and the parts of longer numbers never count.
 */
object VerificationCode {
    /** How far a code may sit from the words that announce it, in characters. */
    private const val NEAR = 120
    private const val NEAR_WEAK = 40
    private const val YEAR_NEAR = 6
    private val YEARS = 1900..2099

    /** Bodies put the code near the top; the rest is footer and fine print. */
    private const val READ_CHARS = 2_000

    private val strong = Regex(
        listOf(
            "verification", "verify", "one[- ]time", "\\botp\\b", "passcode", "security code", "log[- ]?in code",
            "sign[- ]?in code", "authentication", "auth code", "confirmation code", "access code", "\\b2fa\\b",
            "two[- ]factor", "2-step", "אימות", "חד[- ]פעמי", "קוד כניסה", "קוד הכניסה", "קוד זיהוי", "קוד אבטחה",
            "קוד האבטחה", "קוד גישה", "קוד אישור", "קוד האישור",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    private val weak = Regex("\\bcode\\b|\\bpin\\b|קוד", RegexOption.IGNORE_CASE)

    /** "code" that is about money or a place, not about signing in. */
    private val notASignIn = Regex(
        "promo|discount|coupon|voucher|gift|referral|zip|postal|post code|area code|qr|bar ?code|source code|" +
            "הנחה|קופון|שובר|מיקוד|מתנה|מבצע|קופה",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Digits alone (4 to 8) or in two groups ("482 913", "4829-1305"), optionally after a short
     * prefix ("G-"). Not part of a longer number, a price, a date, a time or a phone number.
     */
    private val numeric = Regex(
        "(?<![\\p{L}\\p{N}.,/:$€£₪#+\\-])(?<!\\d[ -])(?:[A-Z]{1,3}-)?(\\d{3,4}[ -]\\d{3,4}|\\d{4,8})(?![\\p{L}\\p{N}%₪$€£]|[.,/:\\-]\\p{N}|[ -]\\d)",
    )

    /** Letters and digits together, right after the announcing word: "code: X7K2PQ". */
    private val mixed =
        Regex("(?<!\\p{L})(?:code|otp|pin|קוד|סיסמה)[^\\p{L}\\p{N}\\n]{1,4}([A-Z0-9]{5,10})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    /** The code in the subject, or else in the text (a snippet or the body); null when there is none. */
    fun find(subject: String, text: String): String? = findIn(subject) ?: findIn(text.take(READ_CHARS))

    private fun findIn(text: String): String? {
        if (text.isBlank()) return null
        val strongAt = strong.findAll(text).map { it.range }.toList()
        val weakAt = weak.findAll(text).map { it.range }.filter { !aboutSomethingElse(text, it) }.toList()
        if (strongAt.isEmpty() && weakAt.isEmpty()) return null
        fun distance(range: IntRange, to: IntRange) = when {
            range.last < to.first -> to.first - range.last
            to.last < range.first -> range.first - to.last
            else -> 0
        }

        fun closeness(range: IntRange): Int? {
            val near = strongAt.minOfOrNull { distance(range, it) }?.takeIf { it <= NEAR }
            val nearWeak = weakAt.minOfOrNull { distance(range, it) }?.takeIf { it <= NEAR_WEAK }
            return listOfNotNull(near, nearWeak).minOrNull()
        }
        val candidates = numeric.findAll(text).mapNotNull { match ->
            val plain = match.groupValues[1].filter { it.isDigit() }
            // "2024" is a year unless the sentence says outright that it is the code.
            closeness(match.range)?.takeIf { plain.length != 4 || plain.toInt() !in YEARS || it <= YEAR_NEAR }?.let { plain to it }
        } + mixed.findAll(text).mapNotNull { match ->
            val code = match.groupValues[1]
            val looksLikeCode = code.any { it.isDigit() } && code.any { it.isLetter() } && code == code.uppercase()
            if (looksLikeCode) closeness(match.groups[1]!!.range)?.let { code to it } else null
        }
        return candidates.minByOrNull { it.second }?.first
    }

    /** A "code" next to "coupon", "zip" and the like. */
    private fun aboutSomethingElse(text: String, at: IntRange): Boolean {
        val around = text.substring((at.first - 16).coerceAtLeast(0), (at.last + 17).coerceAtMost(text.length))
        return notASignIn.containsMatchIn(around)
    }
}
