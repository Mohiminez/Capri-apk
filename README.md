# Capri for Android

A small, unofficial Android port of [Capri](https://github.com/lachydotmcg/capri-desktop): she floats over your apps, chats in her own voice and personality, talks out loud and does small things for you.

## Build the APK on GitHub (no Android Studio needed)

1. Create a new **private or public** repository on GitHub and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab, pick **Build APK** and press **Run workflow** (it also runs on every push).
3. When it finishes (about 3 to 5 minutes), open the run and download the **Capri-debug-apk** artifact. Unzip it and install `app-debug.apk` on your phone (allow "install unknown apps" for your browser or file manager).

If the build fails, open the failed step, copy the red error lines and send them to me.

## First run

1. Open **Capri**, allow *floating over apps* and the microphone.
2. Pick a provider, paste your API key (Gemini and NVIDIA keys are free) and press **Start Capri**.
3. Drag her anywhere, tap her to type, hold her to talk. The notification hides her or stops her.

## What works

- Floating Capri with her real animations: idle, blinking, talking, thinking glitch, napping, waking, grin, dangling when dragged
- Chat with any OpenAI-compatible provider (Gemini, NVIDIA, OpenRouter, OpenAI, DeepSeek, Claude, or your own URL)
- Her personality and safety rules, copied from the desktop version; editable personality
- Voice in (hold her) and voice out (Android text-to-speech), replies in your language
- Memory: she keeps notes about you and a week of chat, all on your phone
- "Check in on me" pop-ins
- Open apps and websites, play music, pause/skip media, timers and alarms

## What can't come to a phone

Screen watching, controlling other apps with her own cursor, joining Discord calls, playing games, clones and the Mods system. Android doesn't allow most of that without special accessibility or screen-capture setups.

## Credits and licence

Unofficial fan project, **non-commercial use only** (PolyForm Noncommercial 1.0.0, see `LICENSE.md`). Based on Capri by Lachy, https://github.com/lachydotmcg/capri-desktop. Her art and animations are by Capri's artist; the personality text comes from the desktop app. Not approved or endorsed by CD PROJEKT RED, the anime's rights holders or its voice actors. Don't sell this or put it in a paid product.

`tools/prepare_assets.py` regenerates the sprite sheets and persona text from a checkout of the desktop repo.
