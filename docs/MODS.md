# Making a Capri mod

A mod is one small `.json` file. It changes how she looks, sounds and talks. It is **data only**: it cannot carry code, keys, providers, tools or permissions, and anything unknown in it is ignored.

In the app: Behavior tab → **MODS** → *Copy a mod template*, edit it, save it as `something.json`, then *Import a mod or look*. Turn it on from its card. Turning it off puts your old settings back.

```json
{
  "capriMod": 2,
  "name": "My Mod",
  "name_ar": "مودي",
  "author": "me",
  "description": "What it does, in one line.",
  "description_ar": "وصف بسطر واحد",
  "accent": "#FF7AC6",
  "colors": { "hair": "#FF7AC6", "accent": "#7DF9FF", "eyes": "#B14CFF", "edges": "#FFFFFF", "body": "" },
  "personality": "Extra lines added to her personality (up to 2000 characters).",
  "nickname": "boss",
  "tone": 0,
  "voice": { "speed": 105, "pitch": 100, "robot": 0, "glitch": 6, "layers": 20, "arPitch": 130 },
  "settings": { "size": 140, "quiet": false, "sounds": true }
}
```

| Field | Meaning | Limits |
|---|---|---|
| `name` | required | 40 characters |
| `accent` | her glow / UI colour | `#RRGGBB` |
| `colors` | recolours hair, accents, eyes, edges, body (`""` = original) | `#RRGGBB` |
| `personality` | extra personality lines; her safety rules always stay after them | 2000 characters |
| `nickname` | what she calls you | 30 characters |
| `tone` | 0 normal, 1 calm, 2 professional, 3 wild | 0 to 3 |
| `voice` | speed, pitch (60 to 160), robot, glitch, layers (0 to 100), arPitch (80 to 160) | clamped |
| `settings` | only `size`, `theme`, `quiet`, `sounds`, `lang` | everything else is ignored |

Colours apply the next time she starts. Looks made in the desktop editor (`.capri` files) are imported with the same button.
