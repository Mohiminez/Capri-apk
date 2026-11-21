package app.capri.mobile

// Privacy shield: hides things that should never leave the phone from the screen text she sends to an online model.
object Shield {
    private val card = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private fun luhn(d: String): Boolean { var sum = 0; var alt = false; for (c in d.reversed()) { var n = c - '0'; if (alt) { n *= 2; if (n > 9) n -= 9 }; sum += n; alt = !alt }; return sum % 10 == 0 }
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phone = Regex("(?<![\\d-])(?:\\+\\d[\\d ()-]{8,}\\d|0\\d{2,3}[ -]?\\d{3}[ -]?\\d{4})(?![\\d-])")
    private val otp = Regex("(\\b(?:code|otp|pin|password|passcode|token|verification|رمز|كود|الكود|كلمة المرور|التحقق)[^\\n\\d]{0,20})(\\d{4,8})", RegexOption.IGNORE_CASE)
    private val token = Regex("\\b[A-Za-z0-9_]{32,}\\b")
    private val iban = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b")

    fun redact(s: String): String {
        var t = s
        t = otp.replace(t) { it.groupValues[1] + "[code]" }
        t = iban.replace(t, "[account]")
        t = email.replace(t, "[email]")
        t = card.replace(t) { val d = it.value.filter { c -> c.isDigit() }; if (d.length in 13..19 && luhn(d)) "[card]" else it.value }
        t = phone.replace(t, "[phone]")
        t = token.replace(t, "[token]")
        return t
    }
}
