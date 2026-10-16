package co.abaye.mailtice

import co.abaye.mailtice.domain.VerificationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerificationCodeTest {
    private fun code(subject: String, text: String = "") = VerificationCode.find(subject, text)

    @Test
    fun theUsualSentences() {
        assertEquals("482913", code("Your verification code is 482913"))
        assertEquals("482913", code("482913 is your Microsoft account security code"))
        assertEquals("4829", code("Sign-in", "Use this one-time code to sign in: 4829. It expires in 10 minutes."))
        assertEquals("73015488", code("Your login code", "Hi,\n\nYour code:\n\n73015488\n\nIf this wasn't you, ignore this mail."))
    }

    @Test
    fun hebrew() {
        assertEquals("482913", code("קוד האימות שלך: 482913"))
        assertEquals("5521", code("כניסה לחשבון", "הקוד החד פעמי שלך הוא 5521 והוא תקף ל-5 דקות"))
    }

    @Test
    fun groupedAndPrefixedCodesAreCopiedAsDigits() {
        assertEquals("482913", code("G-482913 is your Google verification code"))
        assertEquals("482913", code("Your verification code: 482 913"))
        assertEquals("48291305", code("Verification", "Code: 4829-1305"))
    }

    @Test
    fun lettersAndDigits() {
        assertEquals("X7K2PQ", code("Confirm your address", "Your verification code: X7K2PQ"))
        assertNull(code("Confirm your address", "Your verification code: please wait"))
    }

    @Test
    fun theSubjectWinsOverTheBody() {
        assertEquals("111222", code("Your OTP is 111222", "Order 999888 verification"))
    }

    @Test
    fun noCodeWords() {
        assertNull(code("Invoice 48291", "Your order 482913 has shipped"))
        assertNull(code("Meeting at 14:30", "Room 4012, see you there"))
    }

    @Test
    fun couponsAndPostalCodesAreNotSignIns() {
        assertNull(code("Save 20% today", "Use promo code 482913 at checkout"))
        assertNull(code("Delivery", "Zip code 90210"))
        assertNull(code("מבצע", "קוד הנחה 4829 לקנייה הבאה"))
    }

    @Test
    fun pricesDatesAndPhonesAreNotCodes() {
        assertNull(code("Verify your purchase", "Total: $1234.50 on 12/05/2026"))
        assertNull(code("Verify your number", "We will call you at 054-123-4567"))
        assertNull(code("Verify your number", "We will call you at 054 123 4567"))
        assertNull(code("Verify your email", "Copyright 2026 Example Inc."))
    }

    @Test
    fun aYearSaidOutrightToBeTheCodeCounts() {
        assertEquals("2024", code("Your verification code is 2024"))
    }

    @Test
    fun farFromTheWordsItIsNotTheCode() {
        val filler = "Lorem ipsum dolor sit amet. ".repeat(10)
        assertNull(code("Please verify your account", "$filler Reference 482913"))
    }
}
