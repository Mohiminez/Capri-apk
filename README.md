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

1. Open **[Releases](../../releases/latest)** and download `Capri-x.y.z.apk`. / افتح صفحة **Releases** ونزّل ملف الـAPK.
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
