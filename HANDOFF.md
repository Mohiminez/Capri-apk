# Capri Android — handoff notes (for a new chat / new contributor)

Owner: Mrrobot (Iraq, replies must be in Arabic Gulf dialect; likes ready-made single-command solutions). Repo: https://github.com/Mohiminez/Capri-apk (port of lachydotmcg/capri-desktop; reference CapriChooms/CapriCore).

## Deliver
`cd /home/claude && zip -qr /tmp/outzip/capri-android-final.zip proj -x "*/build/*" "*.nnue"` then SendUserFile (30 MiB limit; ~10.5 MB now).
Tests: `/tmp/runtests.sh [TestClass]` (kotlinc + stubbed android.jar; org.json is a stub, so tests must not use JSONObject). 0 "error" lines = compile OK; last run 197 tests OK. Gradle CI runs tests with cwd = app/ (tests use `assets()` helper).
Nothing has been run on a real device or with Gradle by the assistant.

## Layout (app/src/main/java/app/capri/mobile)
- CapriService.kt: overlay pet, state machine (idle/fall/walk, perch, lying, hang, dance, shake), playGame(), clips, Husk, browser, tools.
- Actions.kt: `[[tag:arg]]` actions, QuickAsks router (open/search/browse/play game, chained jobs), phonetic app matching (Arabic game names).
- Access.kt (CapriAccess): accessibility: shotBlocking/shotSee (grid + pixel frame), controlsList, fingers (multi-touch), ledges, labelAt.
- GamePilot.kt: game player. Loop: shot with yellow grid (cells A1..), real-button list, stuck detection, model JSON {state,say,learn,layout,do|policy|act}. Reflex policies run locally (Reflex.kt: blob/pixel/changed rules, tap/hold/swipe/joy/follow). Roam mode for survival games from saved layout. Never taps buy-like labels, stops on billing.
- FastCapture.kt: MediaProjection+ImageReader live frames (own consent activity; Android 14 allows one VirtualDisplay per projection, so it cannot share ClipService's).
- ClipRecorder.kt: last-30s clip pieces. Husk.kt: Claude Code hook server. BrowserActivity.kt, GameSkills.kt (.capri-skill, assets/gameskills/*), DocText.kt, ChessPilot + Stockfish.kt (SF11 asset, SF16 NNUE downloaded at runtime).
- TextTargets.kt (pure: which screen word is a safe button, find a label, storm purple share), ScreenText.kt (ML Kit wrapper), tools/update_readme.py (README blocks + release notes, run by CI).
- MusicApps.kt (play X on app Y parser), ScamCheck.kt (local scam/phishing scoring + ask parser), VoiceGain.kt (200% volume math): pure Kotlin with unit tests. Test shim: org.junit stubs + kotlinc is enough for these.
- MainActivity.kt: 7 tabs (HOME, BRAIN, VOICE, HER, GAMES, HANDS, MORE); rounded-card UI; settings search indexes all tabs.

## Decisions
- No aimbot / enemy targeting / game memory reading (cheating, ban risk). Fortnite skill is survival-only. YOLO detector and root input were deliberately not built (no trained model; root `input` is slower than gestures).
- Android allows ~1 accessibility screenshot/s; FastCapture removes that limit for local reflex rules.

## Unconfirmed on the owner's phone
ML Kit OCR (dependency com.google.mlkit:text-recognition:16.0.1 added, never built by the assistant), auto menu presses, "text" touches, storm hint, unstick policy, README auto-update step in CI. Music in Anghami/Deezer via MEDIA_PLAY_FROM_SEARCH (falls back to the website), LoudnessEnhancer above 100%, scam toast on share, perch_talk/laydown_talk clips, new HOME tiles and hero reactions. SF16 download on arm64, accessibility revive, new movements (pref pcMoves), game-name matching, clip recording, Husk link, new UI, fast eyes, Fortnite flow.

## Known gaps
ML Kit reads Latin letters only (no Arabic game menus). No trained detector model (YOLO) was added: nothing to ship offline. Chair emote frame-hold while replying, LAN Husk token is cleartext, DocText parses on UI thread (OOM risk on huge files).
