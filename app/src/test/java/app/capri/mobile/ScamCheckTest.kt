package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ScamCheckTest {
    private fun lvl(s: String) = ScamCheck.analyze(s).level

    @Test fun fakeBrandLinkWithThreat() {
        assertEquals(2, lvl("Your PayPal account is suspended. Verify now: http://paypal-secure-login.xyz/verify"))
        assertEquals(2, lvl("حسابك في فيسبوك سيتم ايقافه خلال 24 ساعة. ادخل هنا https://facebook.com.verify-account.top"))
    }

    @Test fun realBrandLinkIsNotFlagged() {
        assertEquals(0, lvl("Meet me here: https://www.google.com/maps/place/Baghdad"))
        assertEquals(0, lvl("see https://www.facebook.com/groups/123 and http://fb.me/abc"))
        assertEquals(0, lvl("افتح https://zaincash.iq لتشحن"))
    }

    @Test fun otpMessagesAreNotScams() {
        assertEquals(0, lvl("Your verification code is 482913. Never share it with anyone."))
        assertEquals(0, lvl("رمز التحقق الخاص بك 482913. لا تشارك هذا الرمز مع احد."))
        assertEquals(0, lvl("Do not give your password or PIN to anyone, even bank staff."))
    }

    @Test fun askingForTheCodeIsAScam() {
        assertTrue(lvl("Please send your verification code to confirm your account") >= 1)
        assertEquals(2, lvl("مبروك ربحت جائزة 5000 دولار! ارسل رمز التحقق الان لاستلامها"))
        assertEquals(2, lvl("Congratulations, you have won a prize! Reply with your card number and CVV to claim your reward."))
    }

    @Test fun arabicDigitsAndPunctuation() {
        assertEquals(2, lvl("عاجل: بطاقتك ستغلق خلال ٢٤ ساعة، ارسل رقم البطاقة والرمز السري حالا"))
    }

    @Test fun apkFromALink() {
        assertTrue(lvl("نزل التطبيق من هنا http://bank-update.com/app.apk") >= 1)
        assertTrue(ScamCheck.analyze("Install the app from the link below http://x.co/a.apk").reasons.isNotEmpty())
    }

    @Test fun shortLinkAloneIsOnlyALittleSuspicious() {
        val v = ScamCheck.analyze("check this out https://bit.ly/3xYz")
        assertEquals(1, v.score); assertEquals(0, v.level)
    }

    @Test fun ipAndPunycodeAndAt() {
        assertTrue(lvl("login http://192.168.4.5/bank") >= 1)
        assertTrue(lvl("http://xn--pypal-4ve.com/signin") >= 1)
        assertTrue(lvl("https://paypal.com@evil.example.xyz/login") >= 1)
    }

    @Test fun deliveryTrick() {
        assertTrue(lvl("Your parcel is on hold at customs. Pay the fee: https://post-track.top/pay") >= 1)
        assertTrue(lvl("شحنتك معلقة في الجمارك، ادفع رسوم التوصيل http://dhl-iq.click/pay") >= 1)
    }

    @Test fun newNumberTrick() {
        assertTrue(ScamCheck.analyze("هلا ماما هذا رقمي الجديد ضاع تلفوني، ارسلي لي رصيد").reasons.isNotEmpty())
    }

    @Test fun normalChatIsQuiet() {
        for (s in listOf("see you at 5pm at the cafe", "شلونك؟ اليوم نلعب كرة", "my score was 5.5 e.g. not bad", "اجتماع الساعة 3 بالمكتب", "send me the photos please", "ارسل لي الصور")) assertEquals(s, 0, lvl(s))
    }

    @Test fun linksExtraction() {
        val ls = ScamCheck.links("go to www.example.com/a, or http://test.org/x?y=1. And e.g. 3.5 or file.txt")
        assertEquals(listOf("www.example.com", "test.org"), ls.map { it.host })
    }

    @Test fun sayIsInTheRightLanguage() {
        val v = ScamCheck.analyze("مبروك ربحت جائزة! ارسل رمز التحقق الان")
        assertTrue(ScamCheck.say(v, true).contains("نصب"))
        assertTrue(ScamCheck.say(v, false).contains("scam"))
    }

    @Test fun askParsing() {
        assertEquals("", ScamCheck.ask("is this a scam?"))
        assertEquals("http://Bad.Example.xyz/Login", ScamCheck.ask("is this a scam: http://Bad.Example.xyz/Login"))
        assertEquals("", ScamCheck.ask("هل هذي رسالة نصب؟"))
        assertTrue(ScamCheck.ask("هل هذي نصب: مبروك ربحت جائزة")!!.contains("مبروك"))
        assertNull(ScamCheck.ask("what is the weather"))
        assertNull(ScamCheck.ask("play lofi"))
    }
}
