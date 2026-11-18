# Capri for Android

A small, unofficial Android port of [Capri](https://github.com/lachydotmcg/capri-desktop): she floats over your apps, chats in her own personality, talks, walks around, and does things on your phone. English and Arabic.

## Build the APK on GitHub (no Android Studio)

1. Create a repository on GitHub and upload **everything in this folder** (keep the `.github` folder).
2. Open **Actions**, pick **Build APK**, press **Run workflow** (it also runs on every push).
3. After about 5 minutes download the **Capri-debug-apk** artifact, unzip it and install `app-debug.apk`.

The Kotlin code has been compiled against Android stubs (no errors) and the command patterns were run on sample sentences, but I can't run the full Gradle build or the app here. CI also runs a few unit tests. If the build fails, copy the red error lines from the failed step and send them back.

## First run

1. Allow *floating over apps*, the microphone and (for her hands and eyes) *accessibility*. On Android 13+ open **App info**, tap the three dots and choose **Allow restricted settings** first.
2. **Brain**: pick a provider, paste the key, leave Model empty. She looks up a working model name for you (or press *Find models for my key*). A wrong or retired name is fixed automatically.
3. **Offline brain**: download a model in the app (Qwen2.5 1.5B or Qwen3 0.6B, or paste any `.litertlm` link), then choose **On this phone**. Runs with Google's LiteRT-LM, no internet.
   A server on the phone (Termux with Ollama) or on your Wi-Fi also works: choose **Custom or local server** and use `http://127.0.0.1:11434/v1`.
4. **Voice**: the stock phone voice is basic. Choose *Gemini voice* (free, uses your Gemini key), *ElevenLabs* (what the Windows version uses) or an *OpenAI-style / Kokoro server*, then press *Test the voice*.
5. Press **Start Capri**. Drag or throw her, tap to type, hold to talk, tap while she works to stop a job.

## Updating

Every build is now signed with the same key (`app/capri.keystore`), so a new APK installs over the old one. **Uninstall the old Capri once** (it was signed with a random key); after that, updates just install. The key is public in this repo on purpose: this is a personal sideload, not a store release.

## What she does

- **Looks and moves like the desktop version.** Her real animations; the body keeps moving while her mouth overlay flaps, and the mouth follows the loudness of her voice. She lies down to sleep, drifts with easing, glitch-teleports with her arrive and leave clips, dangles when held, bounces when thrown, and dances like the desktop does it: her idle with a head nod on the beat and floating notes (the old dance sheet was never what the desktop used)
- **Speaks like the desktop version.** A terminal-style speech box: the "> you:" line, "CAPRI:" in red, neon-blue text, a blinking block cursor, a tail to her head, a glitch when it opens, pages for long answers, Yes/No buttons, long-press to copy. Optional chimes
- **Interface in the desktop's Night City look** (red rim, clipped corners, wine glow), numbered setup, NORMAL / HIGHER / MAX brain tiers, and a real Arabic interface (RTL, the Tajawal font, hand-polished wording)
- **Her colors:** recolor hair, accents, eyes, edges and body, or just tell her ("change your hair color to cyan")
- **Hands** (accessibility): "open WhatsApp and type hi", "scroll down", "tap Send", "go back", "take a screenshot", in English or Arabic, with no AI needed. Launches go through the accessibility service so POCO / Xiaomi background limits don't block them. Per-tool switches, four security levels (Lockdown, Ask every time, Guarded, Trusted), a hands self-test, and a setup report you can paste when something breaks
- **From the desktop:** scroll for me (she watches and rates your TikTok / Shorts / Reels and swipes), doomscroll guard, head pats, jealousy of other AI apps, emotes (hearts, zzz, glitch), mini games, mischief mode, care nudges, bond meter, settings she changes herself (size, wander, voice, speed, volume, colors, theme), focus timer, hologram, "what can you do"
- **Chat** with Gemini, Groq, NVIDIA, OpenRouter, Cerebras, Mistral, OpenAI, Claude, DeepSeek, xAI, Together, SambaNova, Fireworks, Hugging Face, any OpenAI-compatible server, or an offline model on the phone. Each provider keeps its own key; if one fails or is rate-limited she switches to another. No-AI mode, tone styles, and a reply-language override
- **Voice:** Groq Orpheus (Saudi Arabic), Gemini, ElevenLabs, OpenAI/Kokoro; ears: phone recogniser or Whisper; volume, speed, pitch; conversation mode; dictation; tap her to cut her off
- Memory, reminders (they survive a restart), timers, alarms, weather, Wikipedia, what's playing, torch, volume, directions, search, clipboard
- "Ask Capri" in the text-selection menu and Share sheet, a quick settings tile, notification reading, start with the phone

## Privacy and safety

- API keys and tokens are stored encrypted with the Android Keystore
- A key is never sent over plain `http` to a public address; only to this phone or your own network
- Offline mode stays offline: it never falls back to an online brain, and an online "job brain" is never used for a chat that runs on the phone, unless you turn that on
- Security levels: Lockdown, Guarded (she asks before her own ideas; your direct orders just run) and Trusted
- Jobs refuse taps on risky labels (pay, delete, send...) and sending unless your request asked for it, and treat text on the screen as untrusted
- Downloaded models are checked for size; the Hugging Face token only goes to huggingface.co

## Ideas borrowed from other projects

CapriCore (setup report, per-tool permissions, tone styles, memory-fit hints, voice-off-at-launch) and a few desktop-pet projects (tap to interrupt, bubble timing, focus timer).

## What isn't possible on a phone

She can't hear other apps' audio, so she can't listen in on calls. Real-time games are too fast for tap-by-tap control. Code mods are left out on purpose. Offline on-device Kokoro voice is not included; use the Gemini or ElevenLabs voice, or run Kokoro-FastAPI on a PC and use the OpenAI-style option.

## Privacy

With an online brain, text from your screen (and screenshots if you turn that on) goes to that provider when she looks. Password fields are never read. The offline model keeps everything on the phone. Keys are stored only on the phone.

## بالعربي باختصار

١. ارفع كل محتوى المجلد على GitHub، شغّل Build APK من Actions ونزّل الـ APK.
٢. من التطبيق اختر اللغة العربية، الصق المفتاح واترك خانة الموديل فاضية، هي تختار موديل شغّال تلقائياً.
٣. للصوت الأحسن اختر Gemini (مجاني بنفس مفتاح Gemini) أو ElevenLabs ثم "جرّب الصوت".
٤. للشغل بدون نت نزّل الموديل المحلي واختر "On this phone".
٥. لو فشل البناء أرسل لي سطور الخطأ الحمراء.

## Credits and licence

Unofficial fan project, **non-commercial use only** (PolyForm Noncommercial 1.0.0, see `LICENSE.md`). Based on Capri by Lachy, https://github.com/lachydotmcg/capri-desktop. Her art and animations are by Capri's artist; the personality text comes from the desktop app. Not approved or endorsed by CD PROJEKT RED, the anime's rights holders or its voice actors. Don't sell it or put it in a paid product.

`tools/prepare_assets.py` regenerates the sprite sheets and persona text from a checkout of the desktop repo.
