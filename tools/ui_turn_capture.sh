#!/bin/bash
# Two-client move-turn capture: A connects first (holds the turn), B second. B tries to `dados` on
# A's turn (refused), then A rolls (allowed). Demonstrates that ONLY movement is turn-gated.
set -u
PORT="${1:-2323}"
A="tnA$RANDOM"; B="tnB$RANDOM"
SESS="lambdaturn"; OUT="uishots"
TM="tmux -f /dev/null"
mkdir -p "$OUT"; rm -f "$OUT"/turn_*.ans "$OUT"/turn_*.png

snapA() { $TM capture-pane -t "$SESS:0.0" -e -p > "$OUT/turn_$1.ans"; echo "  A -> turn_$1.ans"; }
snapB() { $TM capture-pane -t "$SESS:0.1" -e -p > "$OUT/turn_$1.ans"; echo "  B -> turn_$1.ans"; }
toA() { $TM send-keys -t "$SESS:0.0" "$1" Enter; }
toB() { $TM send-keys -t "$SESS:0.1" "$1" Enter; }

$TM kill-session -t "$SESS" 2>/dev/null
$TM new-session -d -s "$SESS" -x 240 -y 44 -e TERM=xterm-256color "telnet localhost $PORT"
echo "connecting A first (holds the turn)..."; sleep 7
toA "new"; sleep 1.5; toA "$A"; sleep 1.5; toA "TurnUIa"; sleep 1.5; toA "1"; sleep 2.5

# B joins second
$TM split-window -h -t "$SESS" -e TERM=xterm-256color "telnet localhost $PORT"; sleep 7
toB "new"; sleep 1.5; toB "$B"; sleep 1.5; toB "TurnUIb"; sleep 1.5; toB "1"; sleep 2.5

# B (not the turn-holder) tries to roll -> refused; but B can still scan (real-time)
toB "dados"; sleep 2; snapB "01_B_refused"
toB "scan"; sleep 1.5; snapB "02_B_scan_realtime"

# A (the turn-holder) rolls -> allowed
toA "dados"; sleep 6; snapA "03_A_rolls"

$TM kill-session -t "$SESS" 2>/dev/null
if command -v freeze >/dev/null 2>&1; then
  for f in "$OUT"/turn_*.ans; do freeze "$f" -o "${f%.ans}.png" >/dev/null 2>&1; done
fi
echo "DONE (A=$A B=$B). Turn frames in $OUT/turn_*.png"
ls -1 "$OUT"/turn_*.png 2>/dev/null
