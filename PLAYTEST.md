# Lambda — 3-Player Playtest Notes (2026-06-10)

Played a live session with three fake entities — **Alice**, **Bob**, **Carol** — each on its own
telnet connection, to see what it's actually like to play *with* other people. Ran the multiplayer
turn-based movement, defrag combat, and the heap social loop (chat / pay / trade).

## TL;DR
The game has **two multiplayer modes that feel completely different**:
- **The heap (real-time social hub)** — chat, pay, trade. Lively, immediate, genuinely fun. This is
  where "playing together" works *today*.
- **The dice/turn board movement** — a cool board-game idea, but the **global turn rotation** couples
  every player's movement and makes it stall. This is the part that needs design work.

---

## What's fun / works ✅
- **Turn rotation actually works end-to-end.** Alice → Bob → Carol → back to Alice, each rolling and
  moving. The gate message is clear and friendly: *"⏳ Not your move — alice is up. You can still heap,
  trade, recurse, defrag, scan, repair."*
- **The hybrid design is the best idea here** — while you wait for your move turn you can still chat,
  trade, scan, and fight bots. You're never fully idle. Keep this.
- **Dice roll animation is satisfying** the first few times (two ASCII dice tumbling, then settling to
  Y/X budgets).
- **Defrag combat is the strongest single-player beat.** The proc-file is *randomized per bot* — one
  used a JSON `defrag_processes: [2201]` array, another a flat `defrag_process_id: 3133` — so the PID
  hunt is a real little puzzle, not a rote sequence. Killing a bot dropped **+39 bits and a Stealth
  Cloak**; that reward pop feels good.
- **The social loop is alive.** Chat broadcasts instantly to everyone; `pay` and the full trade
  `offer → accept` both work and notify the right person. Bob offered Alice a fragment for 5 bits and
  she accepted — smooth.

## What needs work 🔧 (roughly highest-impact first)

1. **Combat freezes the *entire* turn rotation.** Alice dice-moved into (3,0), got ambushed by a
   defrag bot, and her move turn froze mid-turn — so Bob and Carol were locked out of moving for the
   whole fight (~minutes), or until the 2-minute cap. With a global rotation, *one* player's combat
   (or AFK) stalls *everyone's* movement. This is the #1 thing that breaks "playing together."

2. **Global rotation doesn't scale and couples unrelated players.** Everyone online shares one
   rotation, even on different matrix levels. With 3 players a lap is slow; with 10 you'd wait through
   9 others to move one tile. **Per-level or per-"table"/game-session rotation** (the Stage-3 idea)
   would make it feel like a real board game — your group takes turns, not the whole server.

3. **Turn-based movement is slow for an exploration game.** Roll (4s animation) + two axis moves,
   then wait two full turns to move again. The 4s dice animation *every* turn gets tedious on repeat —
   consider a faster/instant roll after the first, or only turn-gating in an explicit competitive mode
   while keeping free `cc`-style movement otherwise.

4. **Name matching is case-sensitive and display-name-only.** `pay alice 10` → *"Entity 'alice' not
   found in heap"*, but `pay Alice 10` works. Players will type names every which way — make pay / pm /
   trade match case-insensitively and accept either username or display name.

5. **The defrag grep step is bypassable.** You can read the PID straight from `cat /proc/defrag/<id>`
   and `kill -9 <pid>` without doing the grep puzzle at all (the "PID not acquired" guard didn't stop a
   direct kill). It undercuts the intended skill — either require the grep-acquire, or lean in and make
   the PID genuinely hard to eyeball.

6. **Movement + combat collision is confusing.** Your dice "budget" sits frozen mid-turn while you
   fight; it's unclear the remaining axis is still owed to you (it does resume correctly after the
   kill — that part works — but nothing tells you that).

7. **Heap gives no prompt change.** Nothing in the prompt signals you're "in" the heap; the only cue is
   that `echo` works. A `[heap]` prompt marker would help.

8. **Auto-defrag pace vs. slow movement.** Even with merchant tiles now protected, coordinates wipe
   quickly; combined with slow turn-movement it can feel like the board erodes faster than you can
   explore it.

## Suggested next moves (design)
- **Make the rotation per-level or per-session**, and **don't let one player's combat freeze others'
  turns** — these two together fix most of the multiplayer friction.
- **Case-insensitive name resolution** for pay/pm/trade (quick win, high friction relief).
- Decide the intended **movement feel**: turn-based board game (lean into tables/sessions) vs. free
  real-time exploration (turn-gate only in a competitive mode). Right now it's halfway between.
- The **heap is the most fun part with others today** — consider making it more central (events,
  group goals) while the board-movement design firms up.
