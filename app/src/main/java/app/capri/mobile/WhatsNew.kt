package app.capri.mobile

/**
 * What changed, shown once after an update (like capri-desktop's News) and kept under Settings > WHAT'S NEW.
 * [VERSION] is bumped by hand when there is real news, not on every build, so the build number never makes it pop up for nothing.
 * Pure, so it is unit tested.
 */
object WhatsNew {
    const val VERSION = 6

    class Item(val en: String, val ar: String)

    val items = listOf(
        Item("Chess coach: the review now has an eval graph and a \"Train my mistakes\" button that replays your worst positions as puzzles. Hint is gentler (first press names the piece, second shows the move). You can resign or offer a draw (she accepts only when she is not better).",
            "مدرّب الشطرنج: المراجعة فيها رسم بياني للتقييم وزر \"تدرّب على أخطائي\" يعيد أسوأ مواقفك كتمارين. التلميح صار ألطف (أول ضغطة تسمّي القطعة وتاني ضغطة تبيّن النقلة). وتقدر تستسلم أو تعرض تعادل (تقبل بس إذا ما هي أحسن)."),
        Item("Chess: REVIEW THE GAME grades every move (best, good, inaccuracy, mistake, blunder), gives you and Capri an accuracy percentage and lists the moves that cost the most with the better move. The board also names the opening (Ruy Lopez, Sicilian Najdorf, Queen's Gambit...).",
            "الشطرنج: زر \"راجع اللعبة\" يقيّم كل نقلة (ممتازة، كويسة، دقة ناقصة، غلطة، فادحة)، ويعطيك ولكابري نسبة دقة، ويعرض النقلات اللي كلّفت أكثر مع الأفضل. واللوحة تسمّي الافتتاحية (روي لوبيز، سيسيليان ناجدورف، قمبيت الملكة...)."),
        Item("Chess: five levels now (Easy, Medium, Club, Hard, Master). Hard used to be full strength, so the jump from Medium was huge; Club fills the gap and Hard is about 2000.",
            "الشطرنج: خمس مستويات (سهل، متوسط، نادي، صعب، خبير). الصعب كان بكامل قوة المحرك فكان فيه قفزة كبيرة من المتوسط، فالنادي يسدّ الفجوة والصعب صار تقريباً ٢٠٠٠."),
        Item("Mod browser: browse the community capri-mods list inside the app, search it, and install skins and settings mods with one press (Mods tab).",
            "متصفح المودات: تتصفح قائمة مودات الناس داخل التطبيق، تبحث، وتثبّت السكنات ومودات الإعدادات بضغطة وحدة (تبويب المودات)."),
        Item("Her tap pill (TYPE | TALK) is now drawn exactly like the PC version, with the same glitch-in, and tapping her reacts like the PC: she wakes if asleep, lets go if hanging, otherwise she glitches.",
            "قائمة الضغطة (كتابة | مايك) صارت مرسومة نفس البيسي بالضبط ومعها تشويش الظهور، والضغطة عليها تتصرف مثل البيسي: تصحى إذا نايمة، تنزل إذا معلّقة، وإلا تسوي glitch."),
        Item("App colours: Night City, Black, White and Grey, the same four Mainframe looks as the desktop (Settings > Her look).",
            "ألوان التطبيق: Night City وBlack وWhite وGrey، نفس أربعة ثيمات المين فريم في البيسي (الإعدادات > شكلها)."),
        Item("Sounds: the desktop's six chime sets (Glass, Pulse, Air, Shard, Burst, Crush) with volume and a switch for each chime.",
            "الأصوات: مجموعات التنبيه الست حق البيسي (Glass وPulse وAir وShard وBurst وCrush) مع مستوى الصوت ومفتاح لكل صوت."),
        Item("Pop-ins: Rarely, Sometimes or Often, or exact shortest and longest waits. Game comments: Off, Events only or Chatty.",
            "الإطلالات: نادراً أو أحياناً أو كثير، أو أقصر وأطول انتظار بالدقايق. تعليقها على الألعاب: طافي أو الأحداث بس أو كثيرة الكلام."),
        Item("Replies: choose how long her replies run, how much of the chat she remembers, and how long her bubbles stay up (Settings > Her tone).",
            "الردود: اختر طول ردودها، قد إيش تتذكر من المحادثة، وكم تبقى فقاعتها ظاهرة (الإعدادات > أسلوبها)."),
        Item("She looks after you: water, stretch, rest your eyes, bedtime, meals, game breaks. Say \"start the stopwatch\" and she times it. If you say something worrying, she answers herself with a warm line and a helpline.",
            "تهتم فيك: موية، تمطّط، راحة عيون، نوم، أكل، استراحة ألعاب. قل \"شغّل ساعة التوقيت\" وتحسبها لك. وإذا قلت شي يقلق، ترد بنفسها بكلمة دافية ورقم مساعدة."),
        Item("While she works on a job or plays a game for you, say \"stop\" to cancel it, or \"how's it going\" and she tells you where she is.",
            "وهي تشتغل على مهمة أو تلعب عنك، قل \"وقف\" عشان تلغيها، أو \"وين وصلت\" وتقول لك وين وصلت."),
        Item("Skins stack: turn on several .capri-skin files and they layer, newest on top. Her sitting and lying clips are now 60 frames at 30 fps like the PC. She glitches in with red tendrils when she starts.",
            "السكنات تتراكم: فعّل عدة سكنات وتتطبق فوق بعض، الأحدث فوق. حركات جلوسها واستلقائها صارت 60 إطار بسرعة 30 مثل البيسي. وتدخل بتشويش وخيوط حمراء لما تشتغل."),
        Item("Settings home shows roughly how many tokens you used today and this week.",
            "الرئيسية تعرض تقريباً كم توكن استخدمت اليوم وهالأسبوع.")
    )

    fun text(ar: Boolean): String = items.joinToString("\n\n") { "• " + (if (ar) it.ar else it.en) }

    /** Show it when the person has not seen this version of the news, except on a brand new install (nothing to have changed from). */
    fun due(seen: Int, fresh: Boolean): Boolean = !fresh && seen < VERSION
}
