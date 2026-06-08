#!/bin/bash
# Two-client trade play-through: drives entity A (left pane) and B (right pane) through the
# Phase-5 offer/accept flow and snapshots each side's screen so the trade UI can be eyeballed.
set -u
PORT="${1:-2323}"
A="trA$RANDOM"; B="trB$RANDOM"
SESS="lambdatrade"; OUT="uishots"
TM="tmux -f /dev/null"
mkdir -p "$OUT"; rm -f "$OUT"/trade_*.ans "$OUT"/trade_*.png

snapA() { $TM capture-pane -t "$SESS:0.0" -e -p > "$OUT/trade_$1.ans"; echo "  A -> trade_$1.ans"; }
snapB() { $TM capture-pane -t "$SESS:0.1" -e -p > "$OUT/trade_$1.ans"; echo "  B -> trade_$1.ans"; }
toA() { $TM send-keys -t "$SESS:0.0" "$1" Enter; }
toB() { $TM send-keys -t "$SESS:0.1" "$1" Enter; }

$TM kill-session -t "$SESS" 2>/dev/null
# 240 wide so each half-pane is ~119 cols (the game's full width)
$TM new-session -d -s "$SESS" -x 240 -y 44 -e TERM=xterm-256color "telnet localhost $PORT"
$TM split-window -h -t "$SESS" -e TERM=xterm-256color "telnet localhost $PORT"
echo "connecting both clients..."; sleep 7

# character creation (A then B)
toA "new"; toB "new"; sleep 1.5
toA "$A"; toB "$B"; sleep 1.5
toA "TraderUIa"; toB "TraderUIb"; sleep 1.5
toA "1"; toB "1"; sleep 2.5

# both into heap
toA "heap"; toB "heap"; sleep 2

# A opens a trade with B and views the menu
toA "trade TraderUIb"; sleep 2; snapA "01_menu"

# A makes an offer; B receives the incoming-offer notification
toA "offer F1 1 10"; sleep 2; snapA "02_offer_sent"; snapB "03_offer_received"

# B accepts; both see the result
toB "accept"; sleep 2; snapB "04_buyer_complete"; snapA "05_seller_notified"

$TM kill-session -t "$SESS" 2>/dev/null
if command -v freeze >/dev/null 2>&1; then
  for f in "$OUT"/trade_*.ans; do freeze "$f" -o "${f%.ans}.png" >/dev/null 2>&1; done
fi
echo "DONE (A=$A B=$B). Trade frames in $OUT/trade_*.png"
ls -1 "$OUT"/trade_*.png 2>/dev/null
