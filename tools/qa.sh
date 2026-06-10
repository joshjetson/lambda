# Interactive QA driver for the live Lambda telnet server.
# Source this in each Bash call:  source tools/qa.sh
# A long-lived tmux session named $SESS holds one telnet connection; drive it with `send`,
# read it with `grab` (raw text) or `shot` (ANSI capture -> PNG via freeze).
SESS="${SESS:-lambdaqa}"
PORT="${PORT:-23}"
OUT="${OUT:-uishots}"
TM() { tmux -f /dev/null "$@"; }

mkdir -p "$OUT"

qa_start() {           # qa_start [pane_w pane_h]
  local w="${1:-120}" h="${2:-40}"
  TM kill-session -t "$SESS" 2>/dev/null
  TM new-session -d -s "$SESS" -x "$w" -y "$h" -e TERM=xterm-256color "telnet localhost $PORT"
}
qa_start2() {          # second pane (horizontal split) for two-client tests
  local w="${1:-120}" h="${2:-40}"
  TM split-window -h -t "$SESS" -e TERM=xterm-256color "telnet localhost $PORT"
}
send()  { TM send-keys -t "${SESS}:0.${PANE:-0}" -- "$1"; sleep "${2:-0}"; }   # send TEXT (no Enter)
ent()   { TM send-keys -t "${SESS}:0.${PANE:-0}" "$1" Enter; sleep "${2:-1}"; } # send TEXT + Enter
key()   { TM send-keys -t "${SESS}:0.${PANE:-0}" "$1"; sleep "${2:-0.5}"; }     # raw key (Up/Down/C-c etc.)
grab()  { TM capture-pane -t "${SESS}:0.${PANE:-0}" -p; }                       # plain text of pane
grabe() { TM capture-pane -t "${SESS}:0.${PANE:-0}" -e -p; }                    # ANSI-preserving
shot()  { TM capture-pane -t "${SESS}:0.${PANE:-0}" -e -p > "$OUT/$1.ans"; freeze "$OUT/$1.ans" -o "$OUT/$1.png" >/dev/null 2>&1; echo "  -> $OUT/$1.png"; }
qa_stop() { TM kill-session -t "$SESS" 2>/dev/null; }

# Walk the create-character handshake: qa_login USER DISPLAY AVATAR
qa_login() {
  ent "new" 1.5; ent "$1" 1.5; ent "$2" 1.5; ent "$3" 2.5
}
