#!/bin/sh
# One command: makes Claude Code tell Capri what it is doing (Termux on the phone, or a PC on the same Wi-Fi).
# usage: sh capri-husk.sh TOKEN [HOST] [PORT]      (HOST defaults to 127.0.0.1, PORT to 8765)
# Remove it again with:  sh capri-husk.sh remove
TOKEN="$1"; HOST="${2:-127.0.0.1}"; PORT="${3:-8765}"
command -v node >/dev/null 2>&1 || { echo "needs node (Claude Code already has it)"; exit 1; }
node - "$TOKEN" "$HOST" "$PORT" <<'JS'
const fs = require('fs'), os = require('os'), path = require('path');
const [token, host, port] = process.argv.slice(2);
const f = path.join(os.homedir(), '.claude', 'settings.json');
fs.mkdirSync(path.dirname(f), { recursive: true });
let s = {}; try { s = JSON.parse(fs.readFileSync(f, 'utf8')); } catch (e) {}
s.hooks = s.hooks || {};
const MARK = 'capri-husk';
for (const ev of ['UserPromptSubmit', 'PreToolUse', 'Notification', 'Stop', 'SubagentStop']) {
  s.hooks[ev] = (s.hooks[ev] || []).filter(g => !JSON.stringify(g).includes(MARK));
  if (token === 'remove') { if (!s.hooks[ev].length) delete s.hooks[ev]; continue; }
  const cmd = `curl -s -m 2 -X POST -H "X-Capri-Token: ${token}" --data-binary @- http://${host}:${port}/hook >/dev/null 2>&1 || true # ${MARK}`;
  s.hooks[ev].push({ matcher: '', hooks: [{ type: 'command', command: cmd }] });
}
fs.writeFileSync(f, JSON.stringify(s, null, 2));
console.log(token === 'remove' ? 'Capri link removed.' : 'Capri link installed. Restart Claude Code.');
JS
