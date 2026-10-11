# Brief for review agents

You are reviewing one game built for John's dad (see `research/README.md` for who he is and what he
plays). You did not build it; your job is to find what's wrong before he sees it. Be concrete and skeptical.
Don't fix anything; report.

## Read
- `BEST_PRACTICES.md` (the standard), `tools/BUILDER_BRIEF.md` (what the builder was asked for),
  `research/<game>.md` + `research/shots/` (the app it replaces and his settings), the game's
  `README.md`, then the code under `<game>/`.

## Check
1. **Rules correctness**: read the core engine against the real rules of the game. Look for wrong edge
   cases (scoring, legality, end conditions, payouts, uniqueness...). Write and run extra throwaway
   tests if it helps (in /tmp or a scratch test file you delete afterwards; leave the tree clean).
2. **Feature parity** with the original (for the modes he uses) and with the builder brief. List gaps.
3. **BEST_PRACTICES compliance**, rule by rule where relevant.
4. **Play it for real** on your emulator (release build): `./gradlew :<game>:app:assembleRelease`,
   install, play at least one complete game start to finish through the UI (use hints/automation
   via `uiautomator dump` + `input`), visit every screen, rotate through settings, kill and relaunch
   mid-game. Screenshot anything wrong into `/tmp/review-<game>/` and view the images.
   Look hard at: overlap, clipping, wrapping, off-centre glyphs, tiny text, unclear prompts, dead ends,
   anything confusing for an older player, animations that jump (screenrecord + contact sheet).
5. Robustness: rapid taps, back button everywhere, app backgrounded mid-animation, end-of-game edge cases.

## Devices
Your own emulator only (given in your task): `~/dadgames/tools/emu up <AVD> <port>`, `adb -s emulator-<port>`.
Never touch other emulators or real phones. No `sleep` (use `timeout N tail -f /dev/null`). Shut the
emulator down when finished.

## Report (final message)
A ranked list of findings, most severe first. For each: severity (blocker / major / minor / polish),
where (file:line or screen), what's wrong, how to reproduce, evidence (screenshot path or test output),
suggested fix. Then: what you checked and found fine (so we know coverage), and any general lessons
worth adding to BEST_PRACTICES.md.
