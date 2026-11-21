package app.capri.mobile

// Privacy shield: hides things that should never leave the phone from the screen text she sends to an online model.
object Shield {
    private val card = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phone = Regex("(?<![\\d])\\+?\\d[\\d ()-]{8,}\\d")
    private val otp = Regex("((?:code|otp|pin|password|passcode|token|verification|رمز|كود|الكود|كلمة المرور|التحقق)[^\\n\\d]{0,20})(\\d{4,8})", RegexOption.IGNORE_CASE)
    private val token = Regex("\\b[A-Za-z0-9_\\-]{32,}\\b")
    private val iban = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b")

    fun redact(s: String): String {
        var t = s
        t = otp.replace(t) { it.groupValues[1] + "[code]" }
        t = iban.replace(t, "[account]")
        t = email.replace(t, "[email]")
        t = card.replace(t) { if (it.value.count { c -> c.isDigit() } in 13..19) "[card]" else it.value }
        t = phone.replace(t, "[phone]")
        t = token.replace(t, "[token]")
        return t
    }
}
