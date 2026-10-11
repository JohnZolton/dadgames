# Brief for game-building agents

You are building one Android game for John's dad, replacing an ad-stuffed app he plays for hours a day.
The bar is the Euchre app in this repo: polished, simple, big, verified. "Beautifully simple."

## Read first
- `BEST_PRACTICES.md`: mandatory. Every rule applies to you.
- `research/README.md` and `research/<your game>.md`, plus the screenshots in `research/shots/`
  (view the PNGs). This is what he uses today and how he has it configured.
- `euchre/` is the reference implementation. Study it before writing code:
  `euchre/app/.../GameViewModel.kt` (bot/animation loop, saves, stats), `GameScreen.kt` (table,
  turn indicator, hand drag with seamless hand-off to the table, result panel), `MenuScreens.kt`
  (home, settings, stats), `euchre/core` (pure engine + tests + tournament).
- Shared modules you build on (don't edit them; see "Boundaries"): `ui/` (`DadTheme`, `GamePalette`,
  `P`, buttons, `ScreenScaffold`, `Section`, `Toggle`, `Choice`, `Stepper`, `LabeledSlider`,
  `TileRow`, `StatsHeadline`, `ResultPanel`, `ScorePill`, `BarAction`, `PlayingCard`, `CardBack`,
  `EmptySlot`, `centeredGlyph`, `Store`) and `cards/` (Card/Suit/Rank, `standardDeck`, `shuffledShoe`).

## Workspace and boundaries
- Work only in your git worktree (given in your task) on your branch. Gradle runs from the worktree root:
  `./gradlew :<game>:core:test :<game>:app:assembleRelease`.
- Edit only files under `<game>/`. **Do not edit `ui/`, `cards/`, `euchre/`, `settings.gradle.kts`,
  `gradle/`, or `BEST_PRACTICES.md`.** Other agents are working in parallel on other branches. If you
  need something the shared modules lack, write it inside your game module and list it under
  "Suggestions for shared modules" in your report.
- Keep `applicationId` `dadgames.<game>` and the debug signing config as scaffolded.
- Commit to your branch as you reach milestones (small, descriptive commits). Each commit message
  ends with: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push. Do not merge.

## Devices
- Your own emulator only: `~/dadgames/tools/emu up <your AVD> <your port>` (it's set to dad's
  screen, 1080x2340 @ 480 dpi, 3-button nav). Use `adb -s emulator-<port>` for every adb command.
  Shut it down at the end: `~/dadgames/tools/emu down <port>`.
- **Never** touch any other emulator or any real phone (dad's or John's). Don't install anything on them.
- No `sleep`: use `timeout N tail -f /dev/null` for short waits, or `waitfor` (see `~/bin`).
- Drive the UI with `adb shell uiautomator dump` (text + bounds) and `input tap/swipe`.
- Check animations with `adb shell screenrecord`, then ffmpeg frames tiled into a contact sheet
  (see BEST_PRACTICES #25). Note: the emulator renders in software, so a hitch there may not appear on
  a real phone; judge logic (start position, path, end position), not raw smoothness.

## Definition of done
1. `core`: complete rules/engine, fair randomness, `@Serializable` state, unit tests for every rule,
   a fuzzer (thousands of random games; invariants hold; always terminates), AI/solver tests if any.
2. `app`: every feature in the research notes for the modes he uses (minus all ads, shops, pop-ups,
   events), home screen, settings matching his current settings as defaults, stats seeded with his
   records from `research/README.md`, hint, undo where the original has it, save after every action
   and resume, end-of-game panel, a game-specific launcher icon (vector, replace the placeholder).
3. Release build installed on your emulator; every screen screenshotted into `<game>/shots/`
   (home, game start, mid-game, prompts/dialogs, end panel, settings, stats); you have looked at
   every screenshot for overlap, wrapping, clipping, off-centre glyphs, unreadable text.
4. Kill the app mid-game and relaunch it (release build): it resumes exactly.
5. `<game>/README.md`: features, rules/scoring details, how verified (test counts, screenshots).

## Report back (your final message)
- What you built (features list), what you verified and how (test counts, which screens you checked).
- Known gaps or anything you couldn't verify.
- Deviations from the original app and why.
- Suggestions for shared modules.
- Proposed additions to BEST_PRACTICES.md (only general lessons).
