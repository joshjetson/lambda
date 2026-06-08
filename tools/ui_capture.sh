#!/bin/bash
# Drives a real telnet play-session inside a fixed-size tmux pane so ANSI colours,
# box-drawing and the HUD's full-screen rendering appear exactly as a player sees them,
# then snapshots each screen to an ANSI frame file (uishots/NN_name.ans).
# Render the frames to PNG separately (freeze) — this script is renderer-agnostic.
#
# Usage: tools/ui_capture.sh [PORT] [USER]
set -u
PORT="${1:-2323}"
USER="${2:-ui$RANDOM}"
SESS="lambdaui"
OUT="uishots"
COLS=120; ROWS=40
mkdir -p "$OUT"
rm -f "$OUT"/[0-9]*_*.ans

snap() {  # snap <name>
  tmux capture-pane -t "$SESS" -e -p > "$OUT/$1.ans"
  echo "  captured $1.ans"
}
send() { tmux send-keys -t "$SESS" "$1" Enter; }

# Ignore the user's tmux/shell config and run telnet AS the pane command (no interactive
# shell to eat keystrokes). Force a 256-colour UTF-8 terminal so colours/box-drawing render.
TM="tmux -f /dev/null"
$TM kill-session -t "$SESS" 2>/dev/null
$TM new-session -d -s "$SESS" -x $COLS -y $ROWS -e TERM=xterm-256color "telnet localhost $PORT"
snap() { $TM capture-pane -t "$SESS" -e -p > "$OUT/$1.ans"; echo "  captured $1.ans"; }
send() { $TM send-keys -t "$SESS" "$1" Enter; }
echo "connecting... (welcome animation)"; sleep 7
snap "00_welcome"

# --- character creation handshake ---
send "new";      sleep 1.5
send "$USER";    sleep 1.5
send "$USER";    sleep 1.5    # display name
send "1";        sleep 2.5    # avatar -> dashboard
snap "01_dashboard"

# --- classic-mode feature screens ---
for cmd in status:02_status scan:03_scan map:04_map inventory:05_inventory \
           help:06_help symbols:07_symbols "unlock_symbol water --decode":13_unlock invoke:14_invoke "entropy status":08_entropy; do
  c="${cmd%%:*}"; n="${cmd##*:}"
  send "$c"; sleep 1.5; snap "$n"
done

# --- Phase 10: dice roll (animated) + move ---
send "dados"; sleep 2; snap "17_dados_rolling"   # mid-animation (dice cycling)
sleep 4; snap "18_dados_settled"                 # after the ~4s animation settles
send "move east 2"; sleep 1.5; snap "19_move"

# --- HUD full-screen mode ---
send "hud"; sleep 3; snap "09_hud_enter"
send "scan"; sleep 2; snap "10_hud_scan"
# Phase 9: commands previously "not implemented in HUD mode" now work via shared dispatch
send "entropy status"; sleep 2; snap "15_hud_entropy"
send "symbols"; sleep 2; snap "16_hud_symbols"
send "exit"; sleep 2; snap "12_hud_exit"

$TM kill-session -t "$SESS" 2>/dev/null

# Render each ANSI frame to a PNG (viewable) if charmbracelet/freeze is present.
if command -v freeze >/dev/null 2>&1; then
  echo "rendering PNGs with freeze..."
  for f in "$OUT"/[0-9]*_*.ans; do freeze "$f" -o "${f%.ans}.png" >/dev/null 2>&1; done
fi
echo "DONE (user=$USER). Frames + PNGs in $OUT/"
ls -1 "$OUT"/[0-9]*_*.png 2>/dev/null || ls -1 "$OUT"/[0-9]*_*.ans
