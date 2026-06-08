# Visual UI capture harness

The Spock integration suite proves game *behavior* but strips ANSI — it can't tell you whether the
screen *looks* right (colors, box-drawing alignment, the HUD's full-screen split layout). This
captures what a real player actually sees, as images.

## How it works
`ui_capture.sh` runs a real `telnet` session inside a fixed-size (120×40) `tmux` pane — so ANSI
colors, Unicode box-drawing, cursor positioning and HUD alt-screen rendering all happen exactly as in
a real terminal — drives a scripted play-through (character creation → status/scan/map/inventory/
help/symbols/entropy → HUD mode), and snapshots each screen with `tmux capture-pane -e` to
`uishots/NN_name.ans`. Each frame is then rendered to `uishots/NN_name.png` via `freeze`.

## Prerequisites
- `tmux`, `telnet` (built in)
- `freeze` — `brew install charmbracelet/tap/freeze` (NOT plain `brew install freeze`, which is an
  unrelated Amazon Glacier client)
- optional: `agg` (`brew install agg`) to turn an `asciinema` recording into an animated GIF

## Run
```bash
# 1) start the server on a non-privileged port
LAMBDA_TELNET_PORT=2323 ./gradlew bootRun        # wait for "GAME SERVER READY"
# 2) capture + render (unique username each run avoids "already exists")
tools/ui_capture.sh 2323
# 3) view uishots/*.png
```

Add screens by appending `send "<cmd>"; sleep 1.5; snap "NN_name"` lines in `ui_capture.sh`.
Generated `uishots/` artifacts are git-ignored.
</content>
