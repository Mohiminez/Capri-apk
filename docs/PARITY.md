# Parity: Capri Android vs the two reference projects / المقارنة

Status: **Have** = built in, **Partial** = there but simpler, **PC-only** = needs a desktop/GPU, not possible on a phone, **No** = deliberately not done.

| Feature / الميزة | capri-desktop | CapriCore | Capri Android (this) |
|---|---|---|---|
| Animated overlay pet, drag, idle walks | yes | yes | Have |
| Perch on the screen edge | yes | yes | Have |
| Chat with several AI providers | yes | yes | Have (provider chain + fallback) |
| Screen reading / vision | yes | yes | Have |
| Voice in/out, wake word, talk mode | yes | yes | Have |
| Same voice in every language | partial | yes (Chatterbox) | Have: ONE VOICE (Gemini / ElevenLabs / OpenAI / Fish Audio) |
| Capri's own anime voice | no | yes (trained adapter, NVIDIA GPU) | PC-only / No: the voice actress's voice is not cloned. Use Fish Audio with a voice you own or are licensed to use |
| Memory of the user | yes | yes | Have + Notes manager (search, pin, edit, delete) |
| Skills / custom commands | yes | yes | Have (custom skills, action tags) |
| Bond / affection levels | yes | partial | Have (Charm) |
| Room / companion scene | no | partial | Have (Her room) |
| Modes | partial | no | Have: 7 modes |
| Specialist agents | no | yes | Have: 6 specialists |
| Chess with strong engine | no | no | Have: Stockfish 11 |
| In-app guide | no | no | Have |
| Security centre, encrypted library | no | no | Have |
| Local GPU models | yes | yes | PC-only |

Everything marked Have is covered by unit tests except what needs a real phone (overlay, microphone, Stockfish on-device).
