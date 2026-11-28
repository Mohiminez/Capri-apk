# Changelog / سجل التغييرات

Newest first. Older notes were moved here from the README. / الأحدث أولاً؛ الملاحظات القديمة انتقلت من الـREADME لهنا.

## Graphics and settings
- perch/laydown clips (and their talk/music/sleep variants) rebuilt as 60 frames at 30 fps like the desktop; sprite cache enlarged (smaller on low-RAM phones).
- New settings under HER TONE: reply length (Short/Normal/Long/No limit), chat memory (8/20/32 messages), bubble time (Quick/Normal/Long). Logic in Limits.kt (tested).

## Newest: full audit pass

- All 96 unit tests that run without Android pass together; every source file compiles against a real android.jar (API 34), the only errors left are the third-party libraries and the Gradle-generated `R`.
- Manifest checked: every declared class exists, every component class is declared, every permission the code uses is declared. All `R.*` and `@res` references resolve. All 30 sprite sheets in sprites.json fit their frame grid, every asset path used in code exists, intros have their frames.
- Fixed: the short full-screen effects (startup glitch, cinematic intros, tendrils) could stay on screen if the service was stopped while one played; stopping her now sweeps them (`Leftovers`).
- Checked and fine: pref defaults are consistent, foreground-service types match the manifest, page and file text is never run through the safety-line check or the stop/status shortcut (only what the user types or says).

## Newest: the pill and the startup glitch, like the desktop

- **The tap pill is redrawn from capri-desktop's TalkPill (Talk.cs):** a black plate with cut corners, a soft glow, faint scan lines and a pink-to-cyan neon edge; left half a pencil with **TYPE**, right half the five-bar wave with **TALK**, a slanted seam between them, the pressed half washed cyan. It glitches in for four frames (slivers of the edge torn loose, the plate sliced sideways). Pencil opens the chat bar, wave starts listening.
- A tap now also makes her glitch or grin, like the desktop's click.
- **Startup glitch:** crimson tendrils tear out from behind her every time she starts, different each time (Tendrils.kt). Switch: HER > "Glitch in when she starts".
- The token line (HOME) and the new check-ins are in the previous round; they only show once this update is on GitHub and built.

## Newest: talk to her while she works

- While she is on a phone job or playing a game, a plain "stop" / "وقف" / "كافي" stops it at once, and "how's it going" / "وين وصلتي" / "وش تسوين" is answered from the last step and how long it has run, with no model call (Asides.kt, ported from the desktop and trimmed). Anything else goes to the brain as before.

## Newest: ported from capri-desktop (safety, check-ins, stopwatch, usage)

- **Safety line (Crisis.kt).** If you say you want to hurt yourself (English or Arabic, including Gulf phrasing), she answers herself with one short, warm, fixed line instead of the model: a well-known free number where one exists (US/CA 988, UK/IE 116 123, AU 13 11 14, NZ 1737) and findahelpline.com everywhere else, nothing guessed. Games and jokes ("kill myself in minecraft", "ابي اموت من الضحك") are not taken for real. Repeats at most once every 10 minutes.
- **Check-ins (Care.kt)** now match the desktop's: water, stretch, rest your eyes, bedtime (once per night), meals (noon windows), game break (a known game app in front for a long time) and bored (when you haven't talked to her). Timers start when you start using the phone and start over after 10 minutes away. Fixed canned lines in both languages, so they cost nothing. Switches and sliders under HER > LOOKING AFTER YOU.
- **Stopwatch.** "start the stopwatch / stop / how long", Arabic "شغل ساعة التوقيت / وقف". Survives restarts.
- **Usage line on HOME.** Her asks and approximate tokens for today and the last 7 days (an estimate of 4 characters per token, cloud brains only).
- The compile scan now runs against a real android.jar (API 34): remaining errors are only the libraries and the Gradle-generated `R`, none in new code.

## Newest: stacking skins from capri-desktop

- Import a `.capri-skin` (a zip with skin.json and PNG strips named like her animations) from MODS > "Import a mod, look or skin". Several skins **stack**: the bottom one is drawn whole, an accessory skin (`"accessory": true`, like cat ears or a hat) goes on top of whatever is under it. The list shows each skin's place in the stack ([1], [2]...). Tap a skin to turn it on or off (on puts it on top), hold it to remove it. Restart her to see changes.
- A strip is used only when it is exactly as tall as her frame (384 px) and a whole number of 116 px frames wide, with the same number of frames as her own strip; anything else is skipped. The strips are drawn onto her sheets once when a sheet loads, before her colours, so there is no extra cost while she animates and hair-coloured parts follow My Colors. Data only: only skin.json and plain PNG strips are read; folders, odd names and oversized files are refused (SkinStack.kt, Skins.kt, unit tested).
- Not done from the desktop spec: skins with a different number of frames than hers, moves of their own, per-strip speeds, and `_flipped` strips (she mirrors the normal strip).

## Newest: tap her and the pencil and voice pill appears (like capri-desktop)

- On the desktop app, clicking her shows a small dark pill with a pencil (write to her) and a sound wave (talk to her). Here, tapping her opened the type bar at once. Now a pill appears under her (above her near the bottom of the screen) with the same two icons in the same cyan on dark, drawn in code (CapriPill.kt). Pencil opens the type bar, wave starts listening. Tap her again, tap outside, or wait 7 seconds to close it. HER LOOK > "Tap her to get the pencil and voice pill": turn it off to get the type bar at once like before.
- Checked skins: the app already imports the desktop's ".capri" looks (CustomLook) and has mods. Desktop's stacking accessory skins (a cat-ears layer on top of an outfit) are not here yet.

## Newest: no more merge conflicts when uploading

- CI no longer commits the refreshed README back to GitHub (that made GitSync report merge conflicts on every upload). The README is refreshed inside the zip instead. The zip now has the project files at its root (no extra "proj" folder).

## Newest: native tool calls (experimental) and a first slice out of CapriService

- **Native tool calls**, off by default (HOME > MODE): the request carries one function, act(name, arg), and the provider returns her actions as structured data instead of her writing [[open:spotify]] in the middle of a sentence. Each call is converted back into the same tag, so permissions, confirmations and the 6-per-reply cap are the same code as before. Settings and system names (security, set, chaos, dictate) are never offered to a model, and an argument can't break out of its tag. If a provider answers 400 to tools, that provider falls back to the normal way by itself for the rest of the session. Only your own chat uses it (the game player and background lookups do not), and the answer then appears at once instead of streaming. ToolCalls.kt.
- **CapriService, first slice:** the bubble's page splitting moved to BubbleText.kt (unit tested). The rest of the 2900 lines is left alone on purpose: it shares state, and moving it without building on a device risks breaking her.

## Newest: sturdier action tags (Tags.kt)

- The actions inside her replies ([[open:spotify]] and the rest) were read with a strict pattern that broke when a model wrote a space around the colon, a full-width colon, or a "]" inside the argument (a link with [1] in it). All of those now work.
- **Safety cap:** at most 6 different tags are acted on in one reply, and the same tag with the same argument only once. A web page, a message or a screenshot that tricks her into printing fifty actions now does almost nothing. The existing Guarded and Lockdown rules apply on top, as before.

## Newest: her answer appears while it is being written (streaming)

- **Before:** she waited for the whole answer, up to two minutes on a slow server, showing only a blinking cursor, and then typed it out.
- **Now:** chat answers are requested as a stream (Sse.kt). The bubble shows the words as they arrive (tags like [[open:...]] and thinking blocks are hidden while they arrive), and when the full answer lands the text stays on screen instead of being typed again. The actions and the voice still start when the answer is complete, so nothing changes about what she does, only how soon you see it. A server that ignores streaming answers in one piece as before. Only your own chat messages stream; jobs, the game player and background lookups do not.

## Newest: sturdier brains (ProviderHealth)

- She now remembers which brain failed lately. A provider that just failed (busy 503, network, rate limit) is tried last for 30 s, then 2 min, then 5 min while it keeps failing (a refused key or no credits: 10 min), and goes back to the front the moment it answers once. A brain that is already known to be struggling is not retried with waits, so a dead provider no longer costs every message 5 seconds. Nothing is dropped: if every brain is cooling, the order stays as you set it. Works for chat and for the game player (both go through Brain.complete).

## Newest: more of capri-desktop: cinematic intros and Simple settings

- **Cinematic start-up intros** from capri-desktop's `assets/intros`: "Peek" (20 frames) and "Up close" (22 frames), full screen, touches pass through. Picked in HER LOOK > Intro style (Random, Peek, Up close, Glitch-in, None). The cinematic ones play at most once every 6 hours, so restarting her is never annoying; otherwise the old glitch-in plays (IntroPlan, unit tested). About 1.3 MB of frames, downsized to 768x432.
- **Simple settings** (HOME > MODE): hides the advanced sections (voice engines, cloud voices, ears, mods, tone, library, commands, tools, watching, extras, personality, data, credits...) like desktop's Simple and Advanced settings. One switch brings them back.
- Checked her clips against desktop's sprite folder: every clip desktop has is already here (the flipped ones are mirrors).

## Newest: audit pass

- Checked network timeouts, notification and alarm flags, receivers, cleanup on shutdown, foreground service types, chess draw rules, the update check and the browser. Nearly everything was already sound.
- Fixed: the Gemini voice list and the ElevenLabs voice list had no read timeout (a stalled server could hang that screen forever); "wake my PC" now asks first when it comes from a model reply instead of your own words.

## Newest: busy servers (HTTP 503) no longer break her or the game

- **Seen on a phone:** while playing, Gemini answered HTTP 503 "This model is currently experiencing high demand", she showed the whole JSON in her bubble and the game stopped ("ضاعت مني اللعبة").
- **Retry:** a busy answer (408, 429, 500, 502, 503, 504, 529) is retried twice after 1.5 s and 4 s. If it is still busy and the provider is Gemini, a lighter sibling is tried once (Pro, then Flash, then Flash-Lite) without changing your saved model. Her old "fix the model name" step is no longer triggered by a busy server (it could have swapped your model for nothing).
- **Short messages:** every error she shows or says is now one short line in your language (ApiErrors.kt), never JSON: busy, no internet, too slow, key refused, out of credits, rate limited.
- **The game keeps going:** if the model fails during a game she keeps roaming on her own, waits a little, and asks again; she only stops after 8 failures in a row.
- **Fewer model calls in matches:** while just roaming in a match she now roams twice by herself between each look at the model (about a third of the calls, so fewer busy errors and quicker movement). Storm, elimination and a stuck player still bring the model back at once.

## Newest: fix for the build that stopped at checkDebugAarMetadata

- **Fix:** the build failed because ML Kit (added for reading game screens) brings AndroidX libraries, and gradle.properties had android.useAndroidX=false. It is now true. The app itself uses no support-library code, so nothing else changes.

## Newest: compared with capri-desktop and CapriCore again

- **"Why is my phone slow?"** (also "ليش جوالي بطيء"): she reads free memory, free storage, battery, temperature, battery saver and thermal state on the phone and says what is wrong and what to do (PhoneHealth.kt). Nothing is sent anywhere. (Desktop got "why slow?" and a CPU/RAM panel.)
- **Wake my PC** ("wake my PC", "شغل الكمبيوتر"): sends a Wake-on-LAN magic packet over the Wi-Fi. Paste the PC's MAC in MORE > Wake my PC (WakeOnLan.kt). The PC needs Wake-on-LAN on in its BIOS and network card. (Desktop's Capri Remote does the same.)
- **Budgeted memory recall** (idea from CapriCore's tagged memory): when her notes grow past about 1500 characters, the prompt gets all pinned notes plus the ones that share words with what you just said, instead of everything (Memory.recall). Small lists are unchanged.
- Already in the app and left as is: rate-limit vs no-credits messages, custom OpenAI-compatible provider, "open my browser", Deezer and YouTube Music.

## Newest: Fortnite reads which screen it is, mode tile, mode line on HOME

- **Fortnite / shooters:** the phone now tells which screen it is on from the words it reads (lobby, searching or loading, battle bus, in the match, eliminated, victory) (GameScreens.kt). It waits out loading and searching by itself (no model call, up to about a minute), presses "Return to lobby" / "Leave match" after elimination or a win (never Spectate, never a line with a price), and gives the model the screen name. The Fortnite skill now mentions it and suggests Zero Build for a first match. Still no aiming or shooting at players.
- **Quick Settings tile "Capri: mode":** shows the current mode and moves to the next one on every tap.
- **HOME** shows the current mode (and "automatic" when she switched it herself).

## Newest: automatic modes, driving and meeting modes

- **Two new modes:** DRIVING (hands-free: she talks aloud, wake word and conversation on, no wandering, no pop-ins) and MEETING (completely silent and still). Say "driving mode" or "وضع الاجتماع".
- **Automatic modes** (switch on HOME > MODE, off by default): once a minute she checks the app in front, the battery and the clock. Maps/Waze/Careem open gives Driving; Zoom/Meet/Teams/Classroom gives Meeting; battery 15% or lower and not charging gives Battery saver; 23:00 to 06:00 gives Night. When that ends she puts back the mode you were in. A mode you pick by hand is never overridden, and Game/Meeting/Driving are not replaced by Night or Battery saver. Decision logic is AutoModes.kt (unit tested).

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

- Fixed: "stop watching" was caught by the stopwatch matcher (CI failure); matcher now needs whole word.

## Mod browser
- New "MOD BROWSER (capri-mods)" in the Mods tab: loads the same capri-mods index the desktop browses, search + filter, one-press Install/Reinstall (skins stack on her, settings mods are added to the list). Code mods, skills, tools and companions are hidden (PC only). The file extension decides the kind because the index's own "kind" is unreliable.
- ModDirectory.kt (pure parse/filter + download), ModJson.kt (JSON reader moved out of Mods.kt), ModDirectoryTest.

## Copied from the desktop
- Tap on her is the desktop's React: asleep -> wakes (nothing else); hanging -> lets go; otherwise the glitch clip, in the lying/perched versions when she is lying/perched. No squash, no random grin. The pill stays up while she is touched.
- Mainframe looks: Night City, Black, White, Grey (the desktop's 30-colour palettes, Neon.cs) for the whole app, her pill and her bubble. Settings > HER LOOK > App colours (restarts the screen).
- Chimes: the desktop's six synthesized sets (Glass, Pulse, Air, Shard, Burst, Crush) x sent/heard/listening, volume curve, 300/700 ms gaps (Chimes.kt, ChimePlayer.kt). Settings > SOUNDS: master switch, each chime, set, volume, play.
- Pop-ins: Rarely 20-40 / Sometimes 8-20 / Often 3-8 minutes, plus exact shortest/longest wait (Limits.popRangeMs). Modes that set popMin reset the preset.
- Game comments: Off / Events only / Chatty (pref gameTalk): Off silences screen comments while a game is in front; Chatty comments on most of it and checks twice as often.
- Blink clips: she already blinks with idle/perch/laydown blink strips (+ glitch blinks); the desktop's blink.png/blink_glitch.png are the Quest build's, not the PC's.
- Pill redrawn from Talk.cs line by line: square-cut pencil at 45 degrees with two gaps, the wave is five rounded bars (not square segments), labels placed by their ink bounds in the ShareTechMono font at 8.5 px x scale, the two edge slivers always drawn, and the glitch-in is the desktop's tear (bands slip sideways and take a pink/cyan tint). Follows the Mainframe look. Wave and TALK light in voice chat.

## What's new + theme-aware UI
- WhatsNew.kt: after an update a "What's new" dialog shows once (bump WhatsNew.VERSION by hand when there is news; never on a fresh install). Same text under Settings > WHAT'S NEW (replaces the old hard-coded list). The update check now shows the GitHub release notes in its dialog.
- Settings screen, speech bubble and neon frame colours now come from Night, so the White/Grey/Black looks no longer show white text on a white background.

- Test fix: ChessPilotE2ETest.promotionTakesTheExtraTap failed once on CI (timing: the quick level ran out of time on a busy runner); it now uses a slower level and checks any a7a8 promotion.

## Chess
- Five levels instead of four: EASY, MEDIUM, CLUB (new, Stockfish skill 8, about a club player), HARD (skill 15, about 2000 instead of full strength) and MASTER (full). Before, "Hard" was already superhuman, so there was a huge jump from Medium. A saved level moves up one so its meaning stays (Hard -> Hard, Master -> Master); the pilot keeps its think times.
- Engine.analyse takes a skill; her own search (no Stockfish) looks less far ahead at a lower skill.
- Game review (Review.kt + ChessActivity.review): the engine (220 ms a position) grades every move by how much it lost against its best, lichess-style win-chance accuracy per side, the worst moves with the better move; copyable. Forced moves are skipped.
- Opening names (Openings.kt, ~90 lines: Ruy Lopez, Italian, Sicilian with Najdorf/Dragon, French, Caro-Kann, Queen's Gambit, Indian defences, London, English...) shown under the board's status.
- Chess coach: eval graph in the review (EvalGraph), "Train my mistakes" (your worst five mistakes replayed as puzzles: two tries, then the move is shown), two-step hint (piece first), RESIGN, OFFER A DRAW (she accepts when her score is <= +0.3 and 10+ moves were played). Coach.kt is pure and tested.
