<div align="center">

# Capri for Android — كابري للأندرويد

**A small animated companion that floats over your apps, talks to you, and can use your phone for you.**
**رفيقة صغيرة متحركة تطفو فوق تطبيقاتك، تسولف معك، وتقدر تستخدم جوالك بدالك.**

[⬇️ Download the APK / تحميل التطبيق](../../releases/latest)

</div>

> Unofficial Android port of [Capri for Windows](https://github.com/lachydotmcg/capri-desktop) by Lachy. Free, non-commercial. / نسخة أندرويد غير رسمية من كابري للويندوز، مجانية وغير تجارية.

---

## What is it? / وش هو؟

**EN:** Capri is a pet that lives on your screen. She walks around on top of other apps, chats in her own personality (English or Arabic), speaks out loud, and — if you allow it — can open apps, tap buttons, scroll and type for you. You choose the AI brain: a free online one (Gemini, Groq, …) or a small model that runs fully offline on the phone.

**AR:** كابري شخصية صغيرة تعيش على شاشة جوالك. تتمشى فوق التطبيقات، تسولف معك بشخصيتها (عربي أو إنجليزي)، تتكلم بصوت، وإذا سمحت لها تفتح تطبيقات وتضغط وتسوي سكرول وتكتب بدالك. أنت تختار "العقل": مجاني أونلاين (Gemini، Groq…) أو موديل صغير يشتغل بدون نت على الجوال.

## Install in 1 minute / التثبيت بدقيقة

1. Open **[Releases](../../releases/latest)** and download `Capri-x.y.z-arm64.apk` (almost every phone; if it says it can't install, use `-arm32`). / افتح صفحة **Releases** ونزّل ملف الـAPK.
2. Install it (allow "install unknown apps" if asked). / ثبّته (واسمح بتثبيت التطبيقات المجهولة إذا طلب).
3. Open Capri and follow the numbered steps. / افتح كابري واتبع الخطوات المرقمة.

Updates install over the old version and keep your settings. / التحديثات تتثبت فوق القديم وإعداداتك تبقى. (Settings → *Check for updates*)

## First setup / أول إعداد

| Step | EN | AR |
|---|---|---|
| 1 | Allow **floating over apps** | اسمح بـ**الظهور فوق التطبيقات** |
| 2 | Pick a **brain** and paste its key (free keys: Gemini, Groq…). Leave *Model* empty | اختر **العقل** والصق المفتاح، واترك خانة الموديل فاضية |
| 3 | *(optional)* Microphone for talking to her | *(اختياري)* المايك عشان تكلمها |
| 4 | *(optional)* **Accessibility** so she can see and use the screen. On Android 13+: App info → ⋮ → *Allow restricted settings* first | *(اختياري)* **إمكانية الوصول** عشان تشوف الشاشة وتتحكم فيها. أندرويد 13+: معلومات التطبيق ← ⋮ ← *السماح بالإعدادات المقيّدة* أول |
| 5 | Press **Start Capri** | اضغط **Start Capri** |

Offline: download a model inside the app and choose **On this phone**. / بدون نت: نزّل موديل من داخل التطبيق واختر **On this phone**.

## What she can do / وش تسوي

- **Talk & move** — real animations from the desktop version, speech bubble, voice (phone voice, Gemini, ElevenLabs, OpenAI-style), dance, sleep, throw her around.
- **Hands** — *"open WhatsApp and type hi"*, *"scroll down"*, *"tap Send"*, *"go back"*. Works without AI for simple commands.
- **Watches your screen** — *"tell me when the download finishes"*; she alerts you (notification too). Say *"stop watching"* to cancel.
- **Points at things** — *"circle the send button"* draws a glowing ring on it.
- **Clones** — *"make clones"*; tap one to pop it.
- **Share to her** — share a picture, PDF or text file to *Ask Capri*; or select text anywhere → *Ask Capri* (translate / explain).
- **Say her name** — turn on *Wake word* (Voice tab): *"Capri, open WhatsApp"* or just *"Capri"* and wait for her *"yes?"*. Works in English and Arabic (*"كابري"*).
- **Her desktop voice (Kokoro), offline on the phone** — Voice tab → *Download the Kokoro voice* (about 150 MB, once). It is the same model the Windows version uses, English only; Arabic text uses the phone voice. Pick a voice, speed, pitch, robot and glitch to taste.
- **Offline Arabic voice, no key** — Voice tab → *Download the Arabic voice* (about 40 MB, once). Meta MMS-TTS Arabic running on the phone. Clear Standard-Arabic reading (dialect words sound like a news reader); it is not the PC voice, which has no Arabic. Used automatically for Arabic when no Groq/Gemini key is set. A pitch slider lifts the narrator voice (default 130) so she sounds young and playful, without changing the speed.
- **Reminders that ring** — say "remind me in 10 minutes to…" / «ذكرني بعد 10 دقايق…» / «ذكرني الساعة 5…» (no AI needed). They use a clock-style alarm that phones don't delay, and timers use the same system.
- **Chess** — *"let's play chess"* (or the *Play chess with her* button): a full board with every rule, three levels, undo, play as white or black, and she comments on the game.
- **Looks (.capri)** — import a look made in the desktop editor (Mods → *Import a mod or look*) and she changes shape. *Remove my mod* brings the original back.
- **Scroll for me**, doomscroll guard, head pats, reminders, timers, weather, memory, quick-settings tile.
- **Mods** — small JSON files (accent colour, personality, look settings) or `.capri` looks. Data only, no code.
- **Safe by design** — 4 security levels, risky taps (pay/delete/send) are refused unless you asked, keys are encrypted with the Android Keystore, password fields are never read.

## If the app crashes / إذا التطبيق كراش

Capri now saves the reason of any error. Open the app → **Something wrong?** → *Copy last error* and send it to the developer (open an Issue). Nothing is sent automatically.

كابري الحين تحفظ سبب أي خطأ. افتح التطبيق ← **في مشكلة؟** ← *انسخ آخر خطأ* وارسله للمطوّر (افتح Issue). ما يرسل شي تلقائي.

## Build it yourself / البناء

Everything is automatic: every push to `main` runs **GitHub Actions**, builds a signed release APK and publishes it to **Releases** as `v0.5.<build number>`.

```bash
# locally (JDK 17 + Android SDK)
gradle assembleRelease
```

Put the whole repo (with the `.github` folder) on GitHub; in *Settings → Actions → General → Workflow permissions* choose **Read and write** if the release step is refused.

## Signing / التوقيع

The repo contains a **public** test key (`capri.keystore`) so every build installs over the last one. Anyone can read its password, so for real use make your own and give it to GitHub once (run on any PC with Java):

```bash
keytool -genkeypair -v -keystore my.keystore -alias capri -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 my.keystore   # copy the output
```

GitHub → repo → *Settings → Secrets and variables → Actions* → add `CAPRI_KEYSTORE_B64` (that output), `CAPRI_KEYSTORE_PASSWORD`, `CAPRI_KEY_ALIAS`. Builds then use your key automatically. Note: Android won't install an update signed with a different key over the old app; uninstall the old one once.

## Credits & licence

Based on **Capri** by Lachy — <https://github.com/lachydotmcg/capri-desktop>. Art and animations are by Capri's artist. Licensed **PolyForm Noncommercial 1.0.0** (see `LICENSE.md`): do not sell it or use it in a paid product. Fonts: Share Tech Mono, Barlow, Tajawal (SIL OFL). Not approved or endorsed by CD PROJEKT RED, the anime's rights holders or its voice actors.

`tools/prepare_assets.py` regenerates sprite sheets and persona text from a checkout of the desktop repo.


## Troubleshooting: "no such app" / scrolling videos does nothing

Android 11+ hides installed apps. Capri now asks for the installed-apps permission and also tries well-known package names, but on Xiaomi/POCO/HyperOS, Oppo and some Samsung phones you may have to allow it once: Settings > Apps > Capri > Permissions > Installed apps (or "Get installed apps") > Allow. The report in the app shows how many apps Capri can see.


## New in this update

- **More games with her:** Connect four, tic-tac-toe (she never loses on Hard) and rock-paper-scissors, next to chess. Say "play connect four", «نلعب اكس او», «العب حجر ورقة مقص».
- **Works with no AI at all:** «كم الساعة»، «اي يوم اليوم» (with the Hijri date), «كم البطارية», «شغل الكشاف», «ارفع الصوت», «مؤقت 5 دقايق», «صحيني الساعة 6», and the English versions.
- **Reminders that ring** (see above), a tidier Voice tab with a one-button offline setup, voice layers, and a fix for "no such app".

- **New look:** a bottom navigation bar, sections you can fold (tap a title; advanced ones start folded), tap-tiles for chess / games / test voice / test hands, an AWAKE / ASLEEP badge, and a setup checklist that also checks notifications and whether she can see your apps.
- **Mainframe look:** flat sections with the red rule and yellow `TITLE >` labels, pill buttons with cyan edges, a neon frame around every screen, and colour-set cards (Bubblegum, Cyber Cyan, Royal, Toxic) like the desktop mod cards.
- **Mini music player** like the desktop's (title, artist, progress, prev / play / next) while music plays; needs notification access. A **glitch-in intro** when she wakes up. A **search box** (the magnifier at the top) that finds any setting and jumps to it. Both can be switched off in Behavior.
- **Mods, rebuilt:** a gallery of mod cards (built in: Bubblegum, Cyber Cyan, Royal, Toxic, Robo Capri, Gulf Gremlin, Study Buddy, Business Mode) plus your own `.json` mods. One tap turns a mod on, one tap puts your old settings back. Colours, a few personality lines, nickname, tone and voice effects are all supported, and mods are data only (see [docs/MODS.md](docs/MODS.md)). A **WHO SHE IS** card describes her and shows your bond with her.
- **Smaller download:** the release now has `-arm64` and `-arm32` APKs (instead of one huge file with every CPU type), and her animation images are compressed with no quality loss.

## Chess commentary (new)
Capri now comments while you play chess, like CapriCore's chess skill: game start, captures, check, castling,
promotion, checkmate, draws, and short remarks about the opening / queen / endgame. Lines never repeat until the pool is used.
On the chess screen: **COMMENTS** picks which events she reacts to, **VOICE** makes the pet speak them aloud (needs the pet running).
Code: `ChessReactions.kt` (pure logic, tested in `ChessReactionsTest`).

## Pet motion polish (new)
Soft ground shadow, slow breathing, squash-and-stretch on landings and pets, a lean while she walks, a "!" when you pick her up,
new emotes (star / surprise / question / angry) and a Sparkle item in her menu. Chess shows captured pieces and the material lead.

Her AI brain can now float reactions (heart, star, surprise, question, angry) with the [[emote:x]] tag.

## Safety pass (new)
- She never types into a password box (`typeText` refuses), whoever asks.
- "Submit" is allowed for an explicit send/post/reply goal; a search goal may only submit a search box, not a chat or comment box.
- **WHAT SHE DID** (More tab): a log of every phone action she takes. What she types or copies is never stored in it.
- Signing key: releases are signed with `capri.keystore`; set `CAPRI_KEYSTORE_FILE` / `CAPRI_KEYSTORE_PASSWORD` / `CAPRI_KEY_PASSWORD` in CI for your own key (changing it later means users must reinstall once).

## Listening fix + new features
- **Mic / wake word**: pauses up to ~2s no longer cut you off; she shows what she hears live; real error messages (no internet, mic busy, language not installed...) instead of "didn't catch that"; automatic quiet retry when the recogniser is busy; wake word answers as soon as she hears her name and you pause (no waiting for the end of the sentence); falls back from offline to normal recognition if the offline language isn't installed.
- **Privacy shield** (Settings > Tools): card numbers, emails, phone numbers and codes are hidden from screen text before it goes to an online model.
- **Your own commands** (More tab): `phrase => answer [[tags]]`, with `{x}` placeholders. Runs instantly, no AI.
- **Plan first**: bigger phone jobs show a short plan and wait for your yes.
- Not built yet from the review: model router, offline queue, multi-agent, knowledge base, sync, widgets, plugin API.

## More from the review (new)
- **Fast brain** (Brain tab): short, simple chat goes to a quick provider; harder or longer questions and anything with a picture stay with the main brain. The job brain already existed.
- **Offline queue**: a message that fails for lack of internet is kept (up to 5) and sent when the connection is back.
- **Her library** (More tab): add .txt/.md files; the best matching passages go along with your question. Stored on the phone only.
- **Home-screen widget** (Capri): awake/asleep and waiting reminders; tap opens her.
- **Plugin API** (off by default, switch on in More): `adb shell am broadcast -n app.capri.mobile/.PluginReceiver -a app.capri.mobile.PLUGIN --es cmd say --es text "Hello"` (cmd `say` or `ask`). Works with Tasker/MacroDroid.
- Still not built: multi-agent review, cloud sync (use COPY MY SETTINGS), a visual skills studio.

## Auto brain + review fixes
- **Auto-pick brain** (Brain tab, on by default): code/maths, writing/translation, long text, pictures and quick chat each go to the best provider you have set up; the main brain is kept when it already fits. Offline stays offline. Short-chat "fast brain" still overrides if you set one.
- Bigger jobs: plan -> a second reviewer pass warns about risky steps -> your yes. Planner/reviewer calls are not saved in chat history.
- Backup file save/load (More tab) and one-tap command templates.
- Fixes from an independent code review: network-state permission (offline queue), validated-network check, wake word must open the sentence, mic mute no longer sticks, stale recogniser callbacks ignored, privacy shield no longer eats dates/timestamps (cards need a valid Luhn check), backups can't switch on the plugin API.

## Chess like CapriCore (new)
- **Real engine**: iterative deepening with a clock, transposition table, killer moves, check extensions, quiescence and a smarter evaluation. Levels: Easy / Medium / Hard / **Master** (thinks up to 6 s). Tested on mate-in-1, hanging queen and fork positions.
- **HINT** (arrow + best line in algebraic notation), **PLAY FOR ME** (she plays your move), **AUTO** (she plays your side every turn), live **eval bar**, last moves in notation, **FEN** copy/paste (take a position from chess.com or Lichess and play on from it).
- She **judges your moves** (blunder / mistake, and "mate in N" warnings) when she has searched properly. Toggle under COMMENTS.
- **Board on your screen**: say "what's the best move" while a chess app is open: she screenshots, a vision model reads the board as FEN, her engine finds the best move. Needs accessibility on and an online brain that sees images. A screenshot is read by an AI, so it can misread a piece: she says so, and you can paste the FEN on her chess screen to be sure. She does not tap the moves for you on other apps.

## Playing inside other chess apps (new)
Say **"play chess for me"** (or العبي عني) with a chess app open, **"coach me"** to only get pointers, **"stop chess"** to end it.
- She finds the board by its checkerboard pattern and reads which squares hold white or black pieces **from your screenshot, on the phone, with no AI and no network** (`BoardVision.kt`, tested on synthetic screenshots: both orientations, different board colours, move dots, highlighted squares).
- She follows the game from the opening position by working out which legal move explains each change (`ChessTracker`), also when the opponent replied between two looks. A game already under way needs an online vision brain once, to read its position.
- Her engine (`Engine`) picks the move; in play mode she taps the piece and the square (promotion: taps the destination again for the queen); in coach mode she slides her pointer to the from/to squares and says the move.
- Needs Android 11+ and her accessibility access. It stops itself if it loses the board or a move does not go through.
- **Fair play:** use it for practice, bots, or games where engine help is allowed. Using it in rated games against people breaks chess.com / Lichess rules and can get the account banned. She asks you to confirm before she starts playing.
- Not tested on a real device or real chess apps: piece themes, board borders, pop-ups and animations differ, so expect to tune thresholds in `BoardVision.kt` after trying it.

### Chess pilot hardening
- Move dots (even large ones), last-move tint, check glow, board frames, blur/noise, fractional cell sizes, small boards and four board colour themes are covered by tests (`BoardVisionRealisticTest`).
- `ChessPilotE2ETest` runs the whole loop against a pretend chess app (draws screenshots, accepts taps, has an engine opponent): she plays White and Black, follows a flipped board, handles promotion (extra tap), coach mode taps nothing, stops politely when the board is gone or a game under way can't be read.
- Before every tap she takes a fresh screenshot and checks the board is exactly where and how it was; one automatic re-read if she gets lost; stops after 10 idle minutes or when a move won't go through.

## Chess: full rules, a much stronger engine, sturdier pilot
- Rules: castling rights, en passant (with the pinned-pawn case), underpromotion, stalemate, insufficient material (incl. same-colour bishops), 50-move rule, threefold repetition, FEN half-move clock. Verified against the standard perft counts (6 positions) with both the rules code and the fast search board.
- Engine: new mutable board, PVS, transposition table, null-move, late-move reductions, killers/history, tapered evaluation (piece-square tables, mobility, pawn structure, king shelter, mop-up), repetition-aware search, ~100-move opening book. Depth ~11 in 1.5 s on a laptop. Levels: Easy/Medium are shallow with noise; Hard 1.8 s; Master 5 s. Default level is now Hard.
- Pilot: hides her bubble/body/cursor while taking the screenshot (they used to cover the board and stall her), taps then drags then slow taps if a move doesn't go through, looser board detection for wood/textured themes, 30 s limit on the online position reading, draws by repetition/50 moves/insufficient material.

## Scroll-for-me
- Reacts to what is actually on screen (needs an online vision brain; says SKIP when unclear), lets each video play ~28 s (setting `scrollDwell`), waits while you scroll yourself, and can only be started by your own words or the menu, never by a model reply.
