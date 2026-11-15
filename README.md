# Capri for Android

A small, unofficial Android port of [Capri](https://github.com/lachydotmcg/capri-desktop): she floats over your apps, chats in her own personality, talks, walks around, and does things on your phone. English and Arabic.

## Build the APK on GitHub (no Android Studio)

1. Create a repository on GitHub and upload **everything in this folder** (keep the `.github` folder).
2. Open **Actions**, pick **Build APK**, press **Run workflow** (it also runs on every push).
3. After about 5 minutes download the **Capri-debug-apk** artifact, unzip it and install `app-debug.apk`.

This code has not been compiled by me. If the build fails, copy the red error lines from the failed step and send them back.

## First run

1. Allow *floating over apps*, the microphone and (for her hands and eyes) *accessibility*. On Android 13+ open **App info**, tap the three dots and choose **Allow restricted settings** first.
2. **Brain**: pick a provider, paste the key, leave Model empty. She looks up a working model name for you (or press *Find models for my key*). A wrong or retired name is fixed automatically.
3. **Offline brain**: download a model in the app (Qwen2.5 1.5B or Qwen3 0.6B, or paste any `.litertlm` link), then choose **On this phone**. Runs with Google's LiteRT-LM, no internet.
   A server on the phone (Termux with Ollama) or on your Wi-Fi also works: choose **Custom or local server** and use `http://127.0.0.1:11434/v1`.
4. **Voice**: the stock phone voice is basic. Choose *Gemini voice* (free, uses your Gemini key), *ElevenLabs* (what the Windows version uses) or an *OpenAI-style / Kokoro server*, then press *Test the voice*.
5. Press **Start Capri**. Drag or throw her, tap to type, hold to talk, tap while she works to stop a job.

## What she does

- Her real animations: idle, blinking, talking mouth, thinking glitch, napping, waking, grin, dancing to music, dangling when held, drifting around (walk), arriving and leaving
- Gravity: throw her and she bounces off the edges and the floor
- Chat with Gemini, NVIDIA, OpenRouter, OpenAI, DeepSeek, Claude, any OpenAI-compatible server, or the offline model
- Her personality and safety rules from the desktop version; editable; Arabic replies
- Memory (notes about you, a week of chat), reminders on dates, timers, alarms
- Open apps and sites, play music, pause and skip
- Looks at your screen on request, or comments while you play (optional)
- Does jobs by tapping, typing and scrolling with her own cursor, for example opening an app and finding something, or joining a Discord voice channel
- Themes and simple data-only mods (`{"name","accent","personality","nickname"}` in a `.json` file)

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
