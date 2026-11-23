<div align="center">

# Capri for Android — كابري للأندرويد

**A small animated companion that floats over your apps, talks to you, plays chess with you, and can use your phone for you.**
**رفيقة صغيرة متحركة تطفو فوق تطبيقاتك، تسولف معك، تلعب شطرنج، وتقدر تستخدم جوالك بدالك.**

[⬇️ Download / تحميل](../../releases/latest) · [Guide / الدليل](#how-the-app-is-arranged--كيف-التطبيق-مرتب) · [Changelog](CHANGELOG.md) · [Mods](docs/MODS.md)

</div>

> Unofficial Android port of [Capri for Windows](https://github.com/lachydotmcg/capri-desktop) by Lachy. Free and non-commercial. / نسخة أندرويد غير رسمية من كابري للويندوز، مجانية وغير تجارية.

## Contents / المحتويات

1. [What is it](#what-is-it--وش-هو)
2. [Install](#install--التثبيت)
3. [First setup](#first-setup--أول-إعداد)
4. [How the app is arranged](#how-the-app-is-arranged--كيف-التطبيق-مرتب)
5. [What she can do](#what-she-can-do--وش-تسوي)
6. [Things to say](#things-to-say--جمل-جرّبها)
7. [Chess](#chess--الشطرنج)
8. [Modes, room and looks](#modes-room-and-looks--الأوضاع-والغرفة-والأشكال)
9. [Voice](#voice--الصوت)
10. [Privacy and security](#privacy-and-security--الخصوصية-والأمان)
11. [Troubleshooting](#troubleshooting--حل-المشاكل)
12. [Build it yourself](#build-it-yourself--البناء)
13. [Project map](#project-map--خريطة-المشروع)
14. [Credits and licences](#credits-and-licences--الشكر-والتراخيص)

---

## What is it? / وش هو؟

**EN:** Capri is a pet that lives on your screen. She walks over other apps, chats in her own personality (English or Arabic), speaks out loud and, if you allow it, opens apps, taps, scrolls and types for you. You choose the brain: a free online one (Gemini, Groq, …) or a small model that runs fully offline on the phone.

**AR:** كابري شخصية صغيرة تعيش على شاشة جوالك. تتمشى فوق التطبيقات، تسولف بشخصيتها (عربي أو إنجليزي)، تتكلم بصوت، وإذا سمحت لها تفتح تطبيقات وتضغط وتسوي سكرول وتكتب بدالك. أنت تختار «العقل»: مجاني أونلاين (Gemini، Groq…) أو موديل صغير يشتغل بدون نت على الجوال.

## Install / التثبيت

1. Open **[Releases](../../releases/latest)** and download `Capri-x.y.z-arm64.apk` (nearly every phone; if it will not install, use `-arm32`). / نزّل ملف الـAPK من صفحة Releases.
2. Install it (allow "install unknown apps" if asked). / ثبّته (واسمح بتثبيت التطبيقات المجهولة إذا طلب).
3. Open Capri and follow the numbered steps. / افتح كابري واتبع الخطوات.

Updates install over the old version and keep your settings. / التحديثات تتثبت فوق القديم وإعداداتك تبقى.

## First setup / أول إعداد

| Step | English | العربي |
|---|---|---|
| 1 | HOME tab: allow **floating over apps** | تبويب الرئيسية: اسمح بـ**الظهور فوق التطبيقات** |
| 2 | BRAIN tab: pick a brain, paste its key, press **CHECK**. Leave *Model* empty | تبويب العقل: اختار عقل والصق المفتاح واضغط **فحص**، واترك الموديل فاضي |
| 3 | *(optional)* Microphone, to talk to her | *(اختياري)* المايك عشان تحچي وياها |
| 4 | *(optional)* **Accessibility**, so she can see and use the screen. Android 13+: App info → ⋮ → *Allow restricted settings* first | *(اختياري)* **صلاحية الوصول**. أندرويد 13+: معلومات التطبيق ← ⋮ ← *السماح بالإعدادات المقيّدة* أول |
| 5 | Press **WAKE HER UP** | اضغط **صحّيها** |

The green and red marks in the SETUP section tell you what is still missing. Offline: download a model in the app and choose **On this phone**. / علامات الصح والغلط بقسم التجهيز تبين شنو ناقص. بدون نت: نزّل موديل واختار **On this phone**.

## How the app is arranged / كيف التطبيق مرتب

Six tabs along the bottom, and a magnifier at the top that searches every setting and jumps to it. Section titles with a `+` fold. The same text is inside the app: HOME → **GUIDE**.
ستة تبويبات تحت، وعدسة فوق تبحث بكل الإعدادات. نفس الشرح داخل التطبيق: الرئيسية ← **الدليل**.

| Tab | What is in it | شنو بيه |
|---|---|---|
| **HOME** | Wake/stop her, bond level, **mode**, tiles (chess, games, her room, guide, security, test voice/hands), setup checklist | تصحيها وتوقفها، العلاقة، **الوضع**، أزرار، قائمة التجهيز |
| **BRAIN** | Provider, key, model, auto-pick, **specialists**, fast brain, offline model, no-AI mode, CHECK | المزوّد والمفتاح، الاختيار التلقائي، **المتخصصين**، الموديل المحلي، فحص |
| **VOICE** | Voice engine, **ONE VOICE**, effects, microphone, wake word, conversation mode | محرّك الصوت، **صوت واحد**، المؤثرات، المايك، كلمة النداء، وضع المحادثة |
| **BEHAVIOR** | Look, colours, tone, personality, mods, how often she wanders, talks, comments | الشكل والألوان والأسلوب والمودات، وكم تتمشى وتحچي |
| **HANDS** | Accessibility, tools she may use, 4 safety levels, watching, notifications, music | صلاحية الوصول، الأدوات، مستويات الأمان، المراقبة، الإشعارات |
| **MORE** | Her notes about you, her library, your own commands, **WHAT SHE DID**, backup, what's new, credits | ملاحظاتها، مكتبتها، أوامرك، سجل الأعمال، النسخ الاحتياطي |

## What she can do / وش تسوي

| Area | Details |
|---|---|
| **Talk and move** | Real animations from the desktop version, speech bubble, voice, dance, sleep, perch on the screen edge, throw her around |
| **Hands** | Open apps, tap by label, scroll, back/home, type, press Send. Works without AI for simple commands |
| **Screen** | Watch for something and alert you; circle a button; **scroll for me** (comments on what she really sees; only when you ask) |
| **Chess** | Play her, hints, explain, PGN/FEN, play for you or coach you in other chess apps. Stockfish 16 (NNUE; Stockfish 11 as fallback on 32-bit phones) built in |
| **Modes** | Normal, Quiet, Game, Study, Battery saver, Companion, Party |
| **Her room** | Real time-of-day window; decor grows with the bond; pet, snack, play |
| **Specialists** | Coder, writer, translator, maths, tutor, planner: the right working style per question |
| **Everyday, no AI** | Time, date (Hijri), battery, flashlight, volume, timers, alarms, reminders that ring |
| **Games** | Connect four, tic-tac-toe, rock-paper-scissors |
| **Library and commands** | Add .txt/.md files (stored encrypted); write your own `phrase => answer [[tags]]` commands |
| **Share to her** | Share a picture, PDF or text to *Ask Capri*; select text anywhere → *Ask Capri* |
| **Around the phone** | Home-screen widget, quick-settings tile, plugin API for Tasker/MacroDroid (off by default) |

## Things to say / جمل جرّبها

| English | العربي |
|---|---|
| "open WhatsApp and type hi" · "scroll down" · "go back" | «افتح واتساب واكتب هلا» · «سكرول لتحت» · «ارجع» |
| "remind me in 10 minutes to drink water" · "timer 5 minutes" | «ذكرني بعد 10 دقايق أشرب مي» · «مؤقت 5 دقايق» |
| "tell me when the download finishes" · "circle the send button" | «كوليلي لما ينتهي التحميل» · «دوري على زر الإرسال» |
| "let's play chess" · "play chess for me" · "coach me" · "learn my pieces" | «نلعب شطرنج» · «العب عني شطرنج» · «دربني» · «تعلمي قطعي» |
| "study mode" · "game mode" · "party mode" | «وضع الدراسة» · «وضع اللعب» · «وضع الحفلة» |
| "her room" · "help" · "security center" | «غرفتك» · «مساعدة» · «مركز الأمان» |
| "scroll for me" · "stop watching" | «سكرولي الفيديوهات» · «وقفي المراقبة» |
| "remember that my sister's birthday is in May" | «تذكري إن عيد ميلاد اختي بمايو» |

## Chess / الشطرنج

- **In Capri:** full rules (castling, en passant, promotion, check, mate, stalemate, threefold repetition, fifty moves, insufficient material), undo/redo, play either colour, hints, **EXPLAIN** (why this move), load a game from **PGN** or **FEN**. Verified against the standard perft counts.
- **Strength:** the real **Stockfish 16 with NNUE** (C++, built by the NDK, 64-bit phones; **Stockfish 11** on 32-bit ones) at levels 3 and 4; if it cannot load, Capri's own search (PVS, null-move, LMR, quiescence, tapered evaluation, opening book) plays instead. Levels 1 and 2 are deliberately easy.
- **In other chess apps (chess.com, Lichess…):** *"play chess for me"* — she reads the board from the screenshot and taps or drags the pieces; or *"coach me"* — she draws a glowing arrow only. First time on a new board theme: open a **new** game and say *"learn my pieces"*; afterwards she reads any game of that theme herself, offline. If she is not sure of a piece she refuses instead of guessing.
- **Fair play:** use play-for-me for practice, bots and games where help is allowed, not rated games against people.

## Modes, room and looks / الأوضاع والغرفة والأشكال

- **Modes** (HOME → MODE, her menu, or by voice): *Normal, Quiet* (types only), *Game* (stays out of the way, light on battery), *Study* (silent; reminds you to drink and stretch), *Battery saver*, *Companion* (wake word and conversation mode, checks on you), *Party* (max energy). A mode changes only its own switches.
- **Her room** (HOME → HER ROOM): the window shows the real time of day; a rug, plant, lamp and shelf, then string lights and a poster appear as your bond grows. Pet her, give her a snack (every 45 minutes), play, dance, sit, let her sleep.
- **Looks and mods:** BEHAVIOR → MODS has a gallery of mod cards and imports your own `.json` mods or a `.capri` look from the desktop editor. Data only, never code. See [docs/MODS.md](docs/MODS.md).

## Voice / الصوت

- **Phone voice** works at once. **Kokoro** (about 150 MB, once) is the desktop voice, offline, English. The **Arabic voice** (about 40 MB, once) works offline for Arabic. **Cloud voices**: Gemini, ElevenLabs, OpenAI-style, Fish Audio.
- **ONE VOICE** (VOICE tab): every language — Arabic, English, … — in the same cloud voice (Gemini, ElevenLabs, OpenAI or Fish Audio). With an ElevenLabs *cloned* voice she sounds like the same person in every language. The desktop CapriCore does this with a local Chatterbox model on a PC GPU; on a phone the cloud voices are the way. Offline voices cannot do it: Kokoro has no Arabic.
- **Hands-free:** wake word (*"Capri…"* / *«كابري»*) and conversation mode keep listening after she answers.

See [docs/PARITY.md](docs/PARITY.md) for the comparison with capri-desktop and CapriCore. / المقارنة مع المشروعين الأصليين.

## Privacy and security / الخصوصية والأمان

- **Security Center** (HOME → SECURITY) shows everything below in one place, with a fix button for each.
- **Privacy shield:** card numbers, emails, phone numbers and codes are hidden from screen text before it goes to an online model.
- **Encrypted:** API keys and her library use the Android Keystore.
- **Four safety levels** (Lockdown, Guarded, Trusted, Ask always). Risky taps (pay, delete, send) are refused unless you asked. Password boxes are never read or typed into.
- **Action log** (MORE → WHAT SHE DID): never stores typed text, URL paths, emails, phone or card numbers.
- **Plain http** only to this phone or a private network; public addresses need https.
- **Signing key:** the repo ships a public test key; the Security Center warns when your build uses it. See [Signing](#signing--التوقيع).

## Troubleshooting / حل المشاكل

| Problem | Fix |
|---|---|
| "No such app" / she cannot open apps | Settings → Apps → Capri → Permissions → *Installed apps* → Allow (Xiaomi, POCO, Oppo, some Samsung) |
| Accessibility is greyed out or turns itself off | App info → ⋮ → *Allow restricted settings*, then turn it on again |
| She stops after a while | Let Capri ignore battery optimisation (HOME checklist) |
| No voice | VOICE tab: try another engine, press TEST VOICE |
| No answer | BRAIN tab → CHECK shows why (wrong key, no internet…) |
| "Play chess for me" does nothing | Say it on the chess app's screen with a game open; Accessibility must be on. On a new theme say "learn my pieces" in a new game first |
| Scrolling videos does nothing | Needs an online brain that can see pictures, and Accessibility on |
| A crash | MORE → **Something wrong?** → *Copy last error*, then open an Issue. Nothing is sent automatically |

## Build it yourself / البناء

Every push to `main` runs **GitHub Actions**: unit tests (a failing test stops the release), then a signed release APK published to **Releases** as `v0.5.<build number>`.

```bash
gradle testDebugUnitTest      # JDK 17 + Android SDK
gradle assembleRelease        # also builds Stockfish with the NDK
gradle assembleRelease -PwithStockfish=false   # without the native engine
```

In *Settings → Actions → General → Workflow permissions* choose **Read and write** if the release step is refused. The NDK and CMake are installed by Gradle on first use.

**Desktop test of Stockfish:** compile `app/src/main/cpp/**/*.cpp` into a shared library and run `StockfishNativeTest` with `-Dcapri.sf=/path/libstockfish.so`. Without that property the test is skipped.

### Signing / التوقيع

The repo contains a **public** test key (`capri.keystore`) so every build installs over the last one. Anyone can read its password, so for real use make your own:

```bash
keytool -genkeypair -v -keystore my.keystore -alias capri -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 my.keystore   # copy the output
```

GitHub → *Settings → Secrets and variables → Actions* → add `CAPRI_KEYSTORE_B64`, `CAPRI_KEYSTORE_PASSWORD`, `CAPRI_KEY_ALIAS`. Android will not install an update signed with a different key over the old app: uninstall once.

## Project map / خريطة المشروع

| Path (`app/src/main/java/app/capri/mobile/`) | Role |
|---|---|
| `CapriService.kt` | The floating character: movement, perch, speech, listening, actions |
| `SpriteView.kt` | Sprite sheets, emotes, dancing, glow |
| `MainActivity.kt`, `Ui.kt` | Settings app: tabs, sections, search |
| `Brain.kt`, `Router.kt`, `Specialists.kt`, `Knowledge.kt` | Providers, auto-pick, specialist briefs, her library |
| `Actions.kt` | `[[tag]]` actions and the no-AI quick commands |
| `Access.kt`, `Hands.kt` | Accessibility: tap, swipe, scroll, screenshots |
| `Voice.kt`, `Listen.kt`, `Kokoro*.kt` | Speech output and input |
| `Chess*.kt`, `FastBoard.kt`, `Stockfish.kt`, `BoardVision.kt`, `PieceBook.kt` | Rules, engines, screen reading, the pilot |
| `Modes.kt`, `Room.kt`, `Guide.kt`, `Screens.kt` | Modes, her room, the in-app guide, Security Center |
| `Shield.kt`, `Secure.kt`, `ActionLog.kt` | Privacy shield, key storage, action log |
| `app/src/main/cpp/` | Stockfish 16 + Stockfish 11 and their JNI bridges (the 40 MB network is in `assets/`) |
| `app/src/test/` | Unit tests (rules, perft, vision, pilot, modes, guide…) |
| `tools/prepare_assets.py` | Regenerates sprite sheets and persona text from the desktop repo |

## Credits and licences / الشكر والتراخيص

Based on **Capri** by Lachy — <https://github.com/lachydotmcg/capri-desktop>; art and animation by Capri's artist. The ONE VOICE idea comes from [CapriCore](https://github.com/CapriChooms/CapriCore). Capri is licensed **PolyForm Noncommercial 1.0.0** (`LICENSE.md`): do not sell it or use it in a paid product.

**Stockfish 16 and 11** (GPL-3, `app/src/main/cpp/stockfish16/COPYING.txt` and `stockfish/COPYING.txt`; network from official-stockfish/networks) are bundled. GPL-3 and PolyForm Noncommercial do not combine for a public release: for personal use it does not matter; before publishing the APK, either settle the licence or build with `-PwithStockfish=false`.

Fonts: Share Tech Mono, Barlow, Tajawal (SIL OFL). Not approved or endorsed by CD PROJEKT RED, the anime's rights holders or its voice actors.
