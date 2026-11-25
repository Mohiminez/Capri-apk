# Changelog / سجل التغييرات

Newest first. Older notes were moved here from the README. / الأحدث أولاً؛ الملاحظات القديمة انتقلت من الـREADME لهنا.

## Newest: she reads the game screen, stronger Fortnite, README updates itself

- **Fix:** the CI test run failed on one test (PerchClipsTest) because the sprite list file had been rewritten in a different layout; restored, and the new clips are now checked by that test too.
- **Reads the words on a game's screen** on the phone (Google ML Kit, English letters; the model ships in the app, nothing is sent). The game player now (1) presses obvious safe buttons itself before asking the model: Continue, Play again, Skip, Ready, Start, Play, Claim, Collect, Close... never on a screen or line that looks like a store, a price, an ad, a login or an install; a label pressed 3 times in a row without the model being asked is dropped; (2) gives the model the list of words with their pixel positions, and lets it press a button by name: {"t":"text","text":"READY"}; (3) warns the model when much of the screen is storm purple. Two switches in GAMES: read words, press safe buttons herself.
- **Roaming no longer pushes into walls:** when the screen does not change, she sidesteps, jumps and turns (alternating sides).
- **Fortnite skill rewritten:** lobby and popups, where to land, loot priority, the storm and the circle, getting unstuck, what to say after elimination. The skill budget in the prompt was raised from 2600 to 6000 characters (a longer skill would have been dropped entirely).
- She still does not aim at or shoot other players: that is automated aiming, against Epic's rules and a ban risk for the account.
- **README updates itself:** tools/update_readme.py rewrites the version/counts/skill-pack block and the list of new source files on every push to main (committed back as "[skip ci]"), and the GitHub Release text is the newest CHANGELOG section. Nobody has to edit the README for an update.

## Before: music apps, scam check, louder voice, talking on the ledge

- **Play in a named app:** "play Blinding Lights on Anghami / Spotify / Deezer / YouTube Music / Apple Music / SoundCloud / YouTube" and the Arabic forms ("شغل كاظم الساهر على انغامي"). Opens the app's own search, or its website if the app is not installed. New MUSIC setting picks the app used when you do not name one (from capri-desktop 0.9.7, plus Anghami).
- **Scam check:** share a message from WhatsApp/SMS/Telegram to Capri, or ask "is this a scam: ..." / "هل هذي رسالة نصب: ...". Runs on the phone, Arabic and English: fake brand links, shortened/IP/punycode links, requests for codes or card details, prizes, threats, fees and recharge cards, "my new number", delivery tricks, APK links. Real OTP messages that say "never share" are left alone.
- **Voice up to 200%** (CapriCore 1.2.2): the volume slider now goes to 200; above 100 uses Android's loudness enhancer.
- **She talks while perched or lying down:** perch_talk and laydown_talk from the desktop art (she no longer stands up to speak), the glitchy blink when lying, and clips carry on from the same frame when she starts or stops talking.
- **HOME:** she reacts differently to each poke (squash and hop with hearts, grin, glitch, bounce with notes) and blinks; new quick chips for scam check and music; tiles split into PLAY WITH HER and TOOLS AND FIXES.
- 28 new unit tests (MusicAppsTest, ScamCheckTest, VoiceGainTest). Still not built with Gradle or run on a device here.

## Before: Fortnite and survival games

- **Fortnite skill pack** (lobby, bus, landing, looting, staying in the circle, leaving after elimination). It never aims at or shoots players: automated aiming is cheating and risks a ban.
- **Control layout memory:** she finds your on-screen stick, camera area, jump and pickup buttons once and remembers them per game.
- **Roam mode:** in survival games, while nothing needs a decision she walks, sweeps the camera, jumps and picks up loot by herself for ~6 s at a time without waiting for the model, then looks again.

## Before: tidier settings and a new look

- **Tabs reorganised (7):** HOME, BRAIN, VOICE, **HER** (character, mods, look, colours, tone, life), **GAMES** (new: playing for you, fast eyes, game minutes, clip recording, Claude Code link), HANDS, MORE. The old "BEHAVIOR" tab was split because it mixed everything.
- "Looking after you" (water, stretch, late-night, doomscroll guard, battery) is its own folded section.
- **New look:** rounded cards instead of the red edge bar, rounded buttons and tiles, cyan section titles with a fold marker, a pill highlight on the active tab, a cleaner header.

## Before: fast eyes for games

- When she starts a game she asks Android's screen-capture permission once and then **watches the game live (20-60 frames a second)** instead of one screenshot a second. Her local rules react to what is happening now. Frames stay in memory, never saved or sent; the capture stops when she stops playing. Switch off with pref `fastEyes`.

## Before: she plays games better

Techniques taken from recent game-agent research ([PORTAL](https://arxiv.org/abs/2503.13356), [GameSense](https://arxiv.org/abs/2503.21263), Set-of-Mark prompting):
- **Grid on the screenshot** (cells A1, B1 ...): the model points at a cell instead of guessing pixels, so taps land where they should.
- **Real buttons list** from the accessibility tree is sent with every look, so menus are tapped by their actual position.
- **Stuck detection:** if the screen doesn't change after her actions, she is told and must try something else.
- **Fast policies:** for quick games the model writes small rules ("tap the lowest dark tile", "hold fire while a red patch is visible", "push the stick toward the target") and the phone runs them locally several times a second without asking the model, then reports what happened so she can improve the rules. Limit: Android allows about one screenshot per second, so conditions refresh at ~1 Hz; blind rules (spam a button) run faster.

## Before: the desktop's perch, for real

- She now moves like the PC version: walks to the bottom edge and **arrives** onto it, sits with her legs hanging, blinks (sometimes with a glitch), now and then glitches, **lies back along the ledge and naps**, sits up, and **leaves** with the proper leave clip. Music makes her sway on the ledge (perch_music).
- **Smaller APK (~40 MB less):** the Stockfish 16 network is no longer bundled; it downloads once in the background (Stockfish 11 plays until it is there).
- **Explore while I'm away:** menu item or "I'm away": she scrolls the open page and comments, stops when you tap her, the screen turns off or after 10 minutes.
- **Game skill packs** (`.capri-skill`, the desktop's format): six built in (runners, match-3, 2048, tower defense, idle/clicker, action/shooters) and you can import more in HER LIBRARY. She reads the ones that fit when she plays.
- **Library takes more:** `.docx` and `.html` files besides text.
- **Daily briefing:** "ملخص اليوم" / "briefing": weather for your city (set in Settings), today's reminders, and the top news, told in her words.
- **Deep thinking** toggle: she reasons harder on maths, code and decisions.
- **She researches by herself:** when she isn't sure (news, prices, scores, anything that may have changed) she searches the web, reads the results and the top page, and answers from them. "ابحثلي عن ..." / "search for ..." does it directly. Switch off in Settings.
- **Her own browser** (tile "HER BROWSER", or "open the browser", "go to example.com"): sites and searches, plus SUM UP / ASK CAPRI so she reads the page for you.
- **Chained jobs:** "open Spotify and search for X and play it" / "افتح يوتيوب وابحث عن ... وشغل اول نتيجة" run as one job.
- **She plays games for you:** "العبلي فورت نايت" / "play fortnite for me" opens the game and plays it: screenshot, decide, real multi-finger touches (joystick + fire button at once), repeat. She remembers each game's controls, never taps anything that looks like buying, stops when you tap her chip. Needs a brain that can see (Gemini etc.); good at puzzles, runners, idle and turn-based games, slow at twitch shooters.
- **She sits on other apps:** on the top edges of toolbars, cards, pictures and list rows in the app you have open (accessibility), and falls off when you scroll or switch app. Menu: "Sit on this screen".
- **Claude Code link (Husk mode):** Claude Code (in Termux on the phone, or on a PC on your Wi-Fi) reports its hooks to a tiny local server; she reacts to prompts, tools, notifications and when it finishes, and answers "what is Claude doing?". One-line setup is copied from Settings; `tools/capri-husk.sh`. She only watches.
- **Game clips:** keeps your last ~30 s of screen in small pieces; "clip that" / "سجلي المقطع" joins them into Movies/Capri. Asks Android's recording permission once.
- **Her chair emote** from the desktop (Qewiw's drawings): menu "Chair" or say "chair"/"كرسي"; tap it to close. 151 small WebP frames (~1.8 MB, `tools/make_chair.py`).
- **Shake her** and she gets dizzy and glitches. **Headphones** in/out, **unplugging the charger** and the first charge get a reaction.
- **Hanging:** she climbs to the top edge, dangles by her hands, then drops with real gravity. **PARTY** mode now mixes dance bursts, hanging, teleports and walks; new **NIGHT** mode (ledge, quiet, early naps).
- New mode **LEDGE**: she mostly sits and lies on the edge. Mode icons are plain glyphs now, no emoji.
- New sheets (from capri-desktop, `tools/add_pc_clips.py`): perch_arrive, perch_leave, perch_glitch, perch_blink_glitch, perch_music, idle_blink_glitch, laydown, laydown_blink, laydown_glitch, laydown_leave.

## Before that
- **More brains:** 11 new providers, added at the end of the list so saved settings keep working: Zhipu GLM, GitHub Models, SiliconFlow (free tiers), Perplexity, Moonshot Kimi, Qwen, Cohere, DeepInfra, Hyperbolic, plus Ollama and LM Studio under OWN SERVER (DeepSeek, Mistral, OpenAI, Claude, Grok, Together, Fireworks, SambaNova, Hugging Face, Gemini, Groq, NVIDIA, OpenRouter, Cerebras were already there: 27 in all).
- **Room camera:** the CAMERA button in her room shows your real room behind her, like the Room in the official Android app (no ARCore plane tracking). Asks for the camera permission only when you press it.
- **Angular look:** panels, inputs and chips use the desktop's sharp-cornered style instead of pills.
- **Back to her identity (cyberpunk, not a kids' app):** her room is now a rain-lit night-city apartment (skyline window, flickering CAPRI neon, terminal, cables, scanlines, data bits) instead of the pastel one; removed the emoji from menus, modes, chess chips, room buttons; lines are in her teasing-gremlin voice ("Hands. Watch the hands."); little pixel lights instead of sparkles.
- **MAINFRAME band on Home** (BRAIN / VOICE / CAPRI) like the desktop control panel.
- **KEYS:** long-press the launcher icon for Talk, Chess, Quiet, Type; three new Quick Settings tiles (Capri: talk / quiet / chess) next to the existing one; notification buttons stay.
- New tone: ROMANTIC (next to Capri, Calm, Pro, Wild).
- **"Accessibility currently unavailable":** Android marks the service dead after it is killed and ignores the switch. Capri now revives it herself (watchdog every 30 s, and a FIX ACCESSIBILITY tile) once she has the one-time permission `WRITE_SECURE_SETTINGS` (`adb shell pm grant app.capri.mobile android.permission.WRITE_SECURE_SETTINGS`, or the ROOT button). Without it she tries the older component-toggle trick.
- **Green mic dot:** the wake word keeps the mic open. It now listens only for N minutes (default 3, setting in the voice section, 0 = always) after you last touched or talked to her, so the dot is off the rest of the time. Companion mode turns the wake word on; switch mode or the toggle to stop it.
- **Looks:** little drifting lights around Capri that grow with the bond level.
- **Stockfish 16 with its neural network (NNUE)** on 64-bit phones, about 300 Elo above Stockfish 11. The 40 MB network is downloaded by the CI build (or `tools/get_net.sh` for local builds), ships inside the app and is verified on first use; if anything is wrong she silently uses Stockfish 11. APK is about 40 MB bigger.
- **Chess pilot never gives up mid-game:** when it loses track it re-reads the board from the pieces it learned and keeps trying (it used to stop and wait for you). A stuck move is retried instead of stopping. Capri steps off the board while playing instead of flickering.
- **Chess stuck on "thinking":** a second screenshot right after the first is refused by Android; the pilot treated that as "the board changed" and re-thought forever. It now retries the screenshot, and after 4 tries acts on the first look. Capri also no longer vanishes at every screenshot: she hides only when she is actually on the board, so she stays draggable.
- **Chess without words:** HOME tab has CHESS: PLAY FOR ME (starts the auto-player directly) and CHESS CHECK (tests overlay permission, accessibility, screenshot, board reading, engine, and names the broken step). The safety question no longer blocks the start.
- **In-app chess board:** pieces slide to their square (captured ones fade), drag and drop with your finger, soft shadows, richer colours, a haptic tick on every move, and she reacts when you take her piece or she takes yours.
- **"Play instead of me":** «العب مكاني» (and variants) now starts the auto-player; before it fell through to the chat model.
- **Play chess for me:** a live chip at the top of the screen shows what she is doing (looking for the board, board found, thinking, playing e2e4, waiting for their move; tap it to stop). If no board is visible she tells you after 4 seconds instead of staying silent. The safety question is asked only once, not every time (it used to expire unanswered, which looked like she did nothing).
- **Home:** greeting by time of day, day streak, her rotating thoughts, a box to talk to her straight from the app, one-tap phrases, sections fade in.
- **Her room:** wallpaper, curtains that sway, sunbeam/moonlight, desk with a flickering screen, cushion, neon heart (bond 2+), floating dust, vignette.

## Latest round
- **Modes:** seven one-tap modes (Normal, Quiet, Game, Study, Battery saver, Companion, Party), by voice («وضع الدراسة» / "study mode") or from the HOME tab or her menu.
- **One voice:** the ONE VOICE switch makes every language use the same cloud voice (Gemini, ElevenLabs or OpenAI).
- **Her room:** a scene that follows the real time of day and grows with the bond (rug, plant, lamp, shelf, string lights, poster). Pet, snack, play, dance, sit, sleep.
- **Perch:** she sometimes walks to the bottom of the screen and sits with her legs hanging over the edge (the desktop's perch art).
- **Specialists:** coder, writer, translator, maths, tutor and planner briefs for the matching questions; ordinary chat is untouched.
- **Guide, Security Center, encrypted library, safer action log, https-only for public addresses.**
- **Stockfish 11** built in (NDK), with the old engine as fallback. "Play chess for me" works in any word order.
- **Notes manager:** search, pin (★ she never forgets), edit and delete what she remembers (MORE tab).
- **Fish Audio** added as a voice engine and as an ONE VOICE option; docs/PARITY.md compares us with the two reference projects.
- Build fix: removed `abiFilters` (it clashes with the per-ABI APK split).

## Earlier
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

## Chess, round 3: reads any game by itself, like CapriCore (and without its AI model)
- **Learns your chess app's pieces** from the opening position (per board theme, saved in a small file) and from then on reads any game under way from a screenshot, on the phone, no brain and no network. Shape matching of each square against the learned silhouettes, works for white or black at the bottom, and the last-move highlight tells whose move it is. If any piece is uncertain she refuses instead of guessing (then falls back to the online vision brain, if one is chosen).
- Commands: "learn my pieces" / "scan the board" / "تعلمي قطعي" / "شوفي الرقعة" say what she sees and learn the theme if the opening position is on screen.
- Coach mode draws a neon **arrow** over the other app's board (pref `chessArrow`, default on); `chessFrame` draws an outline around the board.
- Board grid is now aligned to the real square boundaries (sub-pixel), which also helps the tracker.
- In her own chess screen: **PGN import** (paste a whole game from chess.com/Lichess), **UNDO/REDO** to walk through it, **EXPLAIN** (engine finds the line, her brain says why).

## Chess, round 4: real Stockfish

- `app/src/main/cpp/` holds **Stockfish 11** (C++, GPL-3, see `stockfish/COPYING.txt`) plus `sf_jni.cpp`, a small bridge that runs Stockfish's normal UCI loop on an in-memory queue. Gradle builds it with the NDK into `libstockfish.so` (arm64 and arm32).
- `Stockfish.kt` loads it lazily. `Engine.analyse` (used by the hint, play-for-me, auto mode and the pilot at levels 3 and 4) asks Stockfish first and checks its move is legal; if the library is missing or answers nothing, Capri's own search plays instead. Levels 1 and 2 stay on the built-in weak engine on purpose.
- Build without it: `gradle assembleRelease -PwithStockfish=false`.
- Licence note: Stockfish is GPL-3, Capri is PolyForm Noncommercial. Shipping them together publicly needs the licence question settled, or build with `-PwithStockfish=false`.
- Test against a desktop build: compile the .cpp files into a shared library and run the tests with `-Dcapri.sf=/path/libstockfish.so` (`StockfishNativeTest`).
- "العب عني شطرنج" in any word order (and "play chess for me") now starts auto-play; before, only a few exact phrasings matched.

## Round 5: guide, security centre, tighter privacy
- **GUIDE** (HOME tile, or say "help" / «الدليل»): what Capri is, how the six tabs are arranged, and how to do each thing, in English and Arabic, with search and "try it" phrases that send the phrase to her. `Guide.kt` holds the text; her brain also gets the matching topic when you ask how to use her.
- **SECURITY CENTER** (HOME tile, or «مركز الأمان»): signing key (warns when the app is signed with the public key from the repo), safety level, privacy shield, library encryption, plugin API, accessibility, action log; each with a fix button.
- **Her library is now encrypted** (Android Keystore). An older plain-text library is locked the first time she reads it.
- **Action log** never keeps URL paths/queries, emails, phone numbers, card numbers or codes, and reminder/note/call text is not logged.
- **Plain http** is only used for this phone or a private network (keys or not); public addresses need https. The manifest still allows cleartext so a home server works; the app is the gate.
- Still not built: multi-agent, a voice-conversation mode, a companion room, splitting `MainActivity`/`CapriService`.
