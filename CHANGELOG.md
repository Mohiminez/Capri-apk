# Changelog / سجل التغييرات

Newest first. Older notes were moved here from the README. / الأحدث أولاً؛ الملاحظات القديمة انتقلت من الـREADME لهنا.

## Newest
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
