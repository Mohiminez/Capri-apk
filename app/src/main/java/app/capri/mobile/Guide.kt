package app.capri.mobile

// The in-app guide: what Capri is, how the app is arranged, and how to do each thing. Plain data, so it can be searched and tested.
class GuideTopic(
    val id: String, val icon: String,
    val en: String, val ar: String,
    val bodyEn: String, val bodyAr: String,
    val tryEn: List<String> = emptyList(), val tryAr: List<String> = emptyList()
) {
    fun title(ar: Boolean) = if (ar) this.ar else en
    fun body(ar: Boolean) = if (ar) bodyAr else bodyEn
    fun tries(ar: Boolean) = if (ar) tryAr else tryEn
}

object Guide {
    val topics: List<GuideTopic> = listOf(
        GuideTopic("start", "★", "What is Capri? Start here", "شنو كابري؟ ابدأ من هنا",
            "Capri is a small character that lives on your screen. She walks over other apps, chats in her own personality, speaks out loud and, if you allow it, opens apps, taps, scrolls and types for you.\n\n" +
                "First setup, in order:\n1. HOME tab: allow \"floating over apps\".\n2. BRAIN tab: pick a brain (a free online one with a key, or a model that runs on the phone) and press CHECK.\n3. Optional: allow the microphone to talk to her.\n4. Optional: turn on accessibility so she can see and use the screen. On Android 13+ first open App info > the three dots > Allow restricted settings.\n5. Press WAKE HER UP.\n\n" +
                "The green and red marks in the SETUP section on HOME tell you what is still missing.",
            "كابري شخصية صغيرة تعيش على شاشتك. تتمشى فوق التطبيقات، تسولف بشخصيتها، تتكلم بصوت، وإذا سمحت لها تفتح تطبيقات وتضغط وتسوي سكرول وتكتب بدالك.\n\n" +
                "أول إعداد بالترتيب:\n١. تبويب الرئيسية: اسمح بـ«الظهور فوق التطبيقات».\n٢. تبويب العقل: اختار عقل (أونلاين مجاني بمفتاح، أو موديل يشتغل على الجوال) واضغط فحص.\n٣. اختياري: اسمح للمايك عشان تحچي وياها.\n٤. اختياري: شغّل صلاحية الوصول عشان تشوف الشاشة وتتحكم بيها. أندرويد 13 وفوق: أول شي معلومات التطبيق ← النقاط الثلاث ← السماح بالإعدادات المقيّدة.\n٥. اضغط صحّيها.\n\n" +
                "علامات الصح والغلط بقسم التجهيز بالرئيسية تقلك شنو ناقص.",
            listOf("hello", "what can you do?"), listOf("هلا", "شنو تقدرين تسوين؟")),

        GuideTopic("map", "▦", "How the app is arranged", "كيف التطبيق مرتب",
            "The bottom bar has six tabs:\n" +
                "• HOME: wake or stop her, her bond level, the MODE switch, tiles for chess, games, her room, guide and security, and the setup checklist.\n" +
                "• BRAIN: which AI answers (provider, key, model), the no-AI mode, the offline model on the phone, the fast brain and the auto-pick, and a CHECK button.\n" +
                "• VOICE: her voice engine (phone, Kokoro offline, Arabic offline, cloud voices), effects, and her ears: microphone and wake word.\n" +
                "• BEHAVIOR: her look, colours, tone, personality, mods, and how often she wanders, talks or comments.\n" +
                "• HANDS: accessibility, which tools she may use, the four safety levels, watching the screen, notifications and music.\n" +
                "• MORE: her notes about you, her library, your own commands, WHAT SHE DID (the action log), backup and data, what is new, credits.\n\n" +
                "The magnifier at the top searches every setting in every tab and jumps to it. Titles with a + can be folded; tap them.",
            "الشريط تحت فيه ستة تبويبات:\n" +
                "• الرئيسية: تصحيها أو توقفها، مستوى العلاقة، مفتاح الوضع، أزرار الشطرنج والألعاب وغرفتها والدليل والأمان، وقائمة التجهيز.\n" +
                "• العقل: منو يجاوب (المزوّد والمفتاح والموديل)، وضع بدون ذكاء، الموديل على الجوال، العقل السريع والاختيار التلقائي، وزر فحص.\n" +
                "• الصوت: محرّك صوتها (الجوال، Kokoro بدون نت، العربي بدون نت، سحابي)، المؤثرات، وأذنها: المايك وكلمة النداء.\n" +
                "• السلوك: شكلها وألوانها وأسلوبها وشخصيتها والمودات، وكم تتمشى وتحچي وتعلّق.\n" +
                "• اليدين: صلاحية الوصول، الأدوات المسموحة، مستويات الأمان الأربعة، مراقبة الشاشة، الإشعارات والموسيقى.\n" +
                "• المزيد: ملاحظاتها عنك، مكتبتها، أوامرك الخاصة، «وش سوّت على جوالك» (سجل الأعمال)، النسخ الاحتياطي والبيانات، الجديد، الشكر.\n\n" +
                "العدسة فوق تبحث بكل إعدادات كل التبويبات وتودّيك لها. العناوين اللي بيها + تنطوي، اضغط عليها."),

        GuideTopic("talk", "♫", "Talking to her", "الحچي وياها",
            "Type in the chat bubble, or use the microphone. With Wake word on (VOICE tab) you can say \"Capri, ...\" in English or \"كابري ...\" in Arabic, or just her name and wait for her \"yes?\".\n\n" +
                "Simple jobs work with no AI at all: time, date (with Hijri), battery, flashlight, volume, timers, alarms, reminders, opening apps, scrolling. Anything else goes to her brain.\n\n" +
                "Select text in any app and choose Ask Capri to translate or explain it, or share a picture, PDF or text file to Ask Capri.",
            "اكتب بفقاعة الدردشة أو استخدم المايك. لو شغّلت كلمة النداء (تبويب الصوت) كول «كابري ...» أو «Capri ...»، أو بس اسمها وانتظر ردها «نعم؟».\n\n" +
                "الأشياء البسيطة تشتغل بدون ذكاء اصطناعي: الساعة، التاريخ (مع الهجري)، البطارية، الكشاف، الصوت، المؤقتات، المنبهات، التذكيرات، فتح التطبيقات، السكرول. أي شي ثاني يروح لعقلها.\n\n" +
                "حدد نص بأي تطبيق واختار Ask Capri تترجمه أو تشرحه، أو شارك صورة أو PDF أو ملف نصي لـ Ask Capri.",
            listOf("what time is it", "remind me in 10 minutes to drink water", "timer 5 minutes"), listOf("كم الساعة", "ذكرني بعد 10 دقايق أشرب مي", "مؤقت 5 دقايق")),

        GuideTopic("hands", "☞", "Hands: she uses your phone", "اليدين: تتحكم بجوالك",
            "Turn on accessibility (HANDS tab) and she can open apps, tap by label, scroll, go back or home, type and press Send for you.\n\n" +
                "Four safety levels: Lockdown (she touches nothing), Guarded (default: risky taps such as pay, delete or send are refused unless you asked), Trusted, and Ask always (she asks before every action). She never types into a password box and never reads one.\n\n" +
                "Bigger jobs show a short plan and wait for your yes. WHAT SHE DID in MORE lists every action she took; typed text is never stored in it.",
            "شغّل صلاحية الوصول (تبويب اليدين) وتقدر تفتح تطبيقات وتضغط على الأزرار بأسمائها وتسوي سكرول وترجع وتكتب وتضغط إرسال بدالك.\n\n" +
                "أربع مستويات أمان: إغلاق كامل (ما تلمس شي)، حذر (الافتراضي: ترفض الضغطات الخطرة مثل الدفع والحذف والإرسال إلا إذا انت طلبتها)، موثوق، واسألني دائماً (تسألك قبل كل عمل). ما تكتب بخانة كلمة مرور أبداً وما تقراها.\n\n" +
                "الشغلات الكبيرة تعرض خطة قصيرة وتنتظر موافقتك. «وش سوّت على جوالك» بتبويب المزيد يسجّل كل عمل سوته، والنص اللي تكتبه ما ينحفظ بيه.",
            listOf("open WhatsApp and type hi", "scroll down", "go back"), listOf("افتح واتساب واكتب هلا", "سكرول لتحت", "ارجع")),

        GuideTopic("screen", "◉", "Screen: watch, point, scroll for me", "الشاشة: مراقبة وإشارة وسكرول عني",
            "• Watching: \"tell me when the download finishes\". She checks the screen and alerts you. Say \"stop watching\" to cancel.\n" +
                "• Pointing: \"circle the send button\" draws a glowing ring on it.\n" +
                "• Scroll for me: she scrolls short videos for you, only when YOU ask. Each video she waits, looks at the actual screen, and comments on what she really sees. She stops when you touch the screen, and never flips a video within 20 seconds of your own swipe. It needs an online brain that can see pictures.\n" +
                "• Doomscroll guard: a nudge when you scroll for too long (BEHAVIOR tab).",
            "• المراقبة: «كوليلي لما ينتهي التحميل». تراقب الشاشة وتنبّهك. كول «وقفي المراقبة» تلغيها.\n" +
                "• الإشارة: «دوّري على زر الإرسال» ترسم حلقة مضيئة عليه.\n" +
                "• سكرول عني: تسوي سكرول للفيديوهات القصيرة بس لما انت تطلب. كل فيديو تنتظر، وتشوف الشاشة الفعلية، وتعلّق على اللي تشوفه فعلاً. توقف إذا لمست الشاشة، وما تقلب فيديو خلال 20 ثانية من سحبتك. تحتاج عقل أونلاين يشوف الصور.\n" +
                "• حارس السكرول: تنبيه إذا سكرولت وقت طويل (تبويب السلوك).",
            listOf("tell me when the download finishes", "circle the send button", "scroll for me"), listOf("كوليلي لما ينتهي التحميل", "دوري على زر الإرسال", "سكرولي الفيديوهات")),

        GuideTopic("chess", "♞", "Chess", "الشطرنج",
            "Play her: say \"let's play chess\" or press CHESS on HOME. Full rules (castling, en passant, promotion, check, mate, stalemate, repetition, fifty moves), undo and redo, play as white or black, hints, EXPLAIN (why this move), load a game from PGN or FEN.\n\n" +
                "Strength: she plays with the real Stockfish 11 engine when it is built into your copy of the app; otherwise with her own engine. Levels 1 and 2 are deliberately easy.\n\n" +
                "In another chess app (chess.com, Lichess...): \"play chess for me\" (she taps the pieces; use it for practice, bots or games where help is allowed, not rated games against people) or \"coach me\" (she only draws an arrow). The first time on a new board theme, open a NEW game and say \"learn my pieces\"; after that she can read any game by herself.",
            "تلعب وياها: كول «نلعب شطرنج» أو اضغط شطرنج بالرئيسية. كل القوانين (التبييت، الأخذ بالمرور، الترقية، الكش، المات، التعادل، التكرار، الخمسين نقلة)، تراجع وإعادة، العب أبيض أو أسود، تلميح، «اشرح» (ليش هالنقلة)، وتحميل لعبة من PGN أو FEN.\n\n" +
                "القوة: تلعب بمحرك Stockfish 11 الحقيقي إذا كان مبني بنسختك، وإلا بمحركها الخاص. المستويين 1 و2 سهلين قصداً.\n\n" +
                "بتطبيق شطرنج ثاني (chess.com، Lichess...): «العب عني شطرنج» (تلمس القطع هي؛ استخدمها للتدريب أو البوتات أو لعب يسمح بالمساعدة، مو اللعب المصنّف ضد ناس) أو «دربني» (ترسم سهم بس). أول مرة على ثيم جديد افتح لعبة جديدة وكول «تعلمي قطعي»، وبعدها تقرا أي لعبة لحالها.",
            listOf("let's play chess", "play chess for me", "coach me", "learn my pieces"), listOf("نلعب شطرنج", "العب عني شطرنج", "دربني", "تعلمي قطعي")),

        GuideTopic("modes", "\u25D1", "Modes and her room", "الأوضاع وغرفتها",
            "Modes change a whole bundle of settings with one tap (HOME > MODE, her menu, or by voice): Normal, Quiet (types only), Game (stays out of the way, light on battery), Study (silent, reminds you to drink and stretch), Battery saver, Companion (wake word and conversation mode, checks on you) and Party (max energy). A mode only changes its own switches.\n\n" +
                "Her room (HOME > HER ROOM): the window shows the real time of day, and a rug, a plant, a lamp and shelf, then string lights and a poster appear as your bond grows. Pet her, give her a snack (every 45 minutes), play, dance, let her sit or sleep. She also sometimes sits on the bottom edge of your screen on her own.",
            "الأوضاع تغيّر مجموعة إعدادات كاملة بضغطة (الرئيسية ← الوضع، أو قائمتها، أو بالصوت): عادي، هادئ (تكتب بس)، لعب (تبعد عن طريقك وخفيفة على البطارية)، دراسة (ساكتة وتذكّرك بالموية والحركة)، توفير بطارية، رفيقة (كلمة النداء ووضع المحادثة وتطمّن عليك)، وحفلة (أقصى طاقة). الوضع يغيّر مفاتيحه هو بس.\n\n" +
                "غرفتها (الرئيسية ← غرفتها): الشباك يبين الوقت الحقيقي، وتطلع سجادة ونبتة ولمبة ورف، وبعدها أضواء وبوستر كل ما تقربتوا. دلّعها، اعطيها اكلة (كل 45 دقيقة)، العب وياها، خليها ترقص أو تقعد أو تنام. وأحياناً تقعد لحالها على حافة شاشتك.",
            listOf("study mode", "game mode", "her room"), listOf("وضع الدراسة", "وضع اللعب", "غرفتك")),

        GuideTopic("games", "▣", "Games and fun", "ألعاب وتسلية",
            "Connect four, tic-tac-toe (she never loses on Hard) and rock-paper-scissors. Pet her, throw her, make clones, give her items, dance and sleep from her menu.",
            "أربعة بالصف، إكس أو (ما تخسر بالصعب) وحجر ورقة مقص. اربت عليها، ارميها، سوي نسخ منها، اعطيها أغراض، رقص ونوم من قائمتها.",
            listOf("play connect four", "make clones"), listOf("العب اربعة بالصف", "سوي نسخ")),

        GuideTopic("brain", "⚙", "The brain: online or on the phone", "العقل: أونلاين أو على الجوال",
            "BRAIN tab. Pick a provider (Gemini, Groq, OpenRouter, OpenAI-style, Claude...) and paste its key; leave Model empty to use the default. Keys are encrypted with the Android Keystore.\n\n" +
                "• Auto-pick: code, writing, long text, pictures and quick chat each go to the best provider you set up.\n" +
                "• Fast brain: short chat goes to a quick provider.\n" +
                "• On this phone: download a small model once; it works with no internet.\n" +
                "• No AI mode: only the built-in commands, nothing leaves the phone.\n" +
                "• If the internet drops, your message waits and is sent when it is back.",
            "تبويب العقل. اختار مزوّد (Gemini، Groq، OpenRouter، نمط OpenAI، Claude...) والصق مفتاحه، واترك الموديل فاضي للافتراضي. المفاتيح تنحفظ مشفرة بـ Android Keystore.\n\n" +
                "• الاختيار التلقائي: البرمجة والكتابة والنص الطويل والصور والسوالف السريعة كل واحد يروح لأفضل مزوّد عندك.\n" +
                "• العقل السريع: السوالف القصيرة تروح لمزوّد سريع.\n" +
                "• على الجوال: نزّل موديل صغير مرة وحدة وتشتغل بدون نت.\n" +
                "• وضع بدون ذكاء: بس الأوامر المدمجة، ولا شي يطلع من الجوال.\n" +
                "• إذا الانترنت انقطع رسالتك تنتظر وتنرسل لما يرجع."),

        GuideTopic("voice", "♪", "Her voice", "صوتها",
            "VOICE tab. Phone voice works at once. Kokoro (about 150 MB, once) is the Windows version's voice, offline, English. The Arabic voice (about 40 MB, once) works offline for Arabic. Cloud voices (Gemini, ElevenLabs, OpenAI-style) need a key. Speed, pitch, robot and glitch effects are there too. Press TEST VOICE on HOME to hear her.",
            "تبويب الصوت. صوت الجوال يشتغل فوراً. Kokoro (حوالي 150 ميجا مرة وحدة) هو صوت نسخة الويندوز، بدون نت، إنجليزي. الصوت العربي (حوالي 40 ميجا مرة وحدة) يشتغل بدون نت للعربي. الأصوات السحابية (Gemini، ElevenLabs، نمط OpenAI) تحتاج مفتاح. السرعة والنبرة والروبوت والتشويش موجودين بعد. اضغط تجربة الصوت بالرئيسية تسمعها."),

        GuideTopic("notes", "✎", "Notes, library and your own commands", "الملاحظات والمكتبة وأوامرك",
            "MORE tab.\n• Her notes about you: things she remembers. Say \"remember that...\"; you can edit or delete them.\n• Her library: add .txt or .md files; the best matching passages go with your question. Stored encrypted on the phone.\n• Your own commands: write phrase => answer [[tags]], with {x} placeholders. They run instantly with no AI.",
            "تبويب المزيد.\n• ملاحظاتها عنك: اللي تتذكره. كول «تذكّري إن...»؛ تقدر تعدّلها أو تمسحها.\n• مكتبتها: ضيف ملفات .txt أو .md وأفضل المقاطع المطابقة تروح وياه سؤالك. تنحفظ مشفرة بالجوال.\n• أوامرك الخاصة: اكتب العبارة => الجواب [[وسوم]] ويمكن تحط {x}. تشتغل فوراً بدون ذكاء اصطناعي.",
            listOf("remember that my sister's birthday is in May"), listOf("تذكري إن عيد ميلاد اختي بمايو")),

        GuideTopic("mods", "✦", "Looks and mods", "الأشكال والمودات",
            "BEHAVIOR tab > MODS. A gallery of mod cards (Bubblegum, Cyber Cyan, Royal, Toxic, Robo Capri, Gulf Gremlin, Study Buddy, Business Mode) and your own .json mods change colours, tone and personality lines. A .capri look made in the desktop editor changes her shape. One tap turns it on; one tap returns your old settings. Mods are data only, never code.",
            "تبويب السلوك > المودات. معرض بطاقات (Bubblegum، Cyber Cyan، Royal، Toxic، Robo Capri، Gulf Gremlin، Study Buddy، Business Mode) وملفات .json حقتك تغيّر الألوان والأسلوب وجمل الشخصية. ملف .capri من محرر الديسكتوب يغيّر شكلها. ضغطة تشغّله وضغطة ترجّع إعداداتك. المودات بيانات فقط، مو كود."),

        GuideTopic("privacy", "⛨", "Privacy and security", "الخصوصية والأمان",
            "• Privacy shield: card numbers, emails, phone numbers and codes are hidden from screen text before it goes to an online model.\n• Keys are encrypted with the Android Keystore; your library is encrypted too.\n• Password boxes are never read or typed into.\n• Risky taps are refused unless you asked.\n• Security Center (HOME tile) shows the state of all of this in one place, with fixes.",
            "• درع الخصوصية: أرقام البطاقات والايميلات والهواتف والأكواد تنخفي من نص الشاشة قبل لا يروح لموديل أونلاين.\n• المفاتيح مشفرة بـ Android Keystore؛ ومكتبتك مشفرة هم.\n• خانات كلمات المرور ما تنقرا ولا ينكتب بيها.\n• الضغطات الخطرة ترفضها إلا إذا انت طلبتها.\n• مركز الأمان (زر بالرئيسية) يعرض حالة كل هذا بمكان واحد وياه الحلول."),

        GuideTopic("fix", "⚠", "Something not working?", "في شي مو شغال؟",
            "• \"No such app\" or she can't open apps: Settings > Apps > Capri > Permissions > Installed apps (or Get installed apps) > Allow. Needed on Xiaomi, POCO, Oppo and some Samsung phones.\n• Accessibility turns itself off or is greyed out: App info > the three dots > Allow restricted settings, then turn it on again.\n• She stops after a while: let Capri ignore battery optimisation (HOME setup checklist).\n• No voice: pick another engine in the VOICE tab and press TEST VOICE.\n• No reply: BRAIN tab > CHECK shows why (wrong key, no internet...).\n• She doesn't react to \"play chess for me\": say it on the chess app's screen with the game open; she needs accessibility on.\n• A crash: MORE tab > SOMETHING WRONG? shows the last error you can copy and send.",
            "• «ما لقيت التطبيق» أو ما تقدر تفتح تطبيقات: الإعدادات ← التطبيقات ← Capri ← الأذونات ← التطبيقات المثبتة ← سماح. مطلوب على شاومي وبوكو وأوبو وبعض سامسونك.\n• صلاحية الوصول تطفي لحالها أو رمادية: معلومات التطبيق ← النقاط الثلاث ← السماح بالإعدادات المقيّدة، وبعدين شغّلها.\n• توقف بعد فترة: خلي كابري يتجاهل توفير البطارية (قائمة التجهيز بالرئيسية).\n• ما اكو صوت: اختار محرّك ثاني بتبويب الصوت واضغط تجربة الصوت.\n• ما ترد: تبويب العقل ← فحص يبين السبب (مفتاح غلط، ما اكو نت...).\n• ما تتفاعل مع «العب عني شطرنج»: كولها وانت بشاشة تطبيق الشطرنج واللعبة مفتوحة؛ وتحتاج صلاحية الوصول شغالة.\n• كراش: تبويب المزيد ← في مشكلة؟ يعرض آخر خطأ تنسخه وتبعثه."),

        GuideTopic("more", "☰", "Widget, plugin API and backup", "الودجت وواجهة الإضافات والنسخ الاحتياطي",
            "• Home-screen widget: shows awake or asleep and waiting reminders; tap opens her.\n• Plugin API (off by default, switch it on in MORE): other apps such as Tasker or MacroDroid can make her say or answer something.\n• Backup: save and load your settings to a file in MORE. Settings carry over when you install a new version on top of the old one.",
            "• ودجت الشاشة الرئيسية: يبين صاحية أو نايمة والتذكيرات المنتظرة؛ ضغطة تفتحها.\n• واجهة الإضافات (مطفية افتراضياً، شغّلها من المزيد): تطبيقات ثانية مثل Tasker وMacroDroid تخليها تحچي أو تجاوب.\n• النسخ الاحتياطي: احفظ وحمّل إعداداتك بملف من المزيد. الإعدادات تبقى إذا ثبّت نسخة جديدة فوق القديمة.")
    )

    private fun norm(s: String): String = s.lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[\\u064B-\\u0652]"), "")

    /** Topics matching every word of [q] (title counts double), best first. Empty query returns all topics in order. */
    fun search(q: String, ar: Boolean): List<GuideTopic> {
        val words = norm(q).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return topics
        return topics.map { t ->
            val title = norm(t.en + " " + t.ar)
            val all = norm(t.en + " " + t.ar + " " + t.bodyEn + " " + t.bodyAr + " " + t.tryEn.joinToString(" ") + " " + t.tryAr.joinToString(" "))
            val hit = words.all { all.contains(it) }
            Pair(t, if (!hit) 0 else 1 + words.count { title.contains(it) } * 2)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }

    private val aboutApp = Regex("how do i|how to|where (?:is|do|can)|settings?|capri|this app|set ?up|كيف|شلون|وين|اعدادات|التطبيق|كابري|اشغل|افعل", RegexOption.IGNORE_CASE)

    /** When the question is about using Capri herself, the best-matching guide topic rides along so she answers from facts, not guesses. */
    fun context(q: String): String {
        if (!aboutApp.containsMatchIn(norm(q))) return ""
        val words = norm(q).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 }.toSet()
        if (words.size < 2) return ""
        val best = topics.map { t -> val all = norm(t.en + " " + t.ar + " " + t.bodyEn + " " + t.bodyAr); val title = norm(t.en + " " + t.ar)
                Triple(t, words.count { all.contains(it) }, words.count { title.contains(it) }) }
            .filter { it.second >= 3 }.map { Pair(it.first, it.second + 4 * it.third) }.maxByOrNull { it.second } ?: return ""
        return "\nFrom Capri's own guide (use only if the question is about using Capri):\n- " + best.first.en + ": " + best.first.bodyEn.take(700).replace('\n', ' ')
    }

    fun find(id: String): GuideTopic? = topics.firstOrNull { it.id == id }
}
