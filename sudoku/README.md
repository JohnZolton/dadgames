# Sudoku

A calm, ad-free classic Sudoku for Dad, replacing Sudoku.com (which opens with full-screen ads, a
fake "Skip" that opens the Play Store, and a chain of event pop-ups). App id `dadgames.sudoku`,
palette `GamePalette.Paper` (dark ink on cream).

## Features
- **Four levels, graded by how much reasoning a puzzle needs**, matching what Sudoku.com players
  expect from the names. A human-style solver (`core/Logic.kt`) always uses the simplest technique
  that makes progress; the grade counts the steps it needed at each level (`Difficulty.classify`):
  - Easy: singles only (hidden singles in box, row or column, and naked singles), 30-34 clues.
  - Medium: up to three pointing / box-line / pair steps.
  - Hard: more of those, or any triple or X-wing.
  - Expert: needs wings (XY, XYZ), swordfish, quads, simple colouring or XY-chains.

  Distribution of 200 generated puzzles per level (from `DistributionReport`; % of puzzles whose
  solve uses each technique; the solver prefers box singles, so row/column and naked singles only
  count when no box single was available):

  | Level | Clues (avg, range) | Non-single steps (avg) | box single | row/col single | naked single | pointing/box-line | pairs | triples | X-wing | wings | swordfish/quad | colouring/chains |
  |---|---|---|---|---|---|---|---|---|---|---|---|---|
  | Easy | 32.3 (30-34) | 0.0 | 100% | 43% | 2% | 0% | 0% | 0% | 0% | 0% | 0% | 0% |
  | Medium | 28.4 (27-32) | 1.8 | 100% | 98% | 76% | 96% | 14% | 0% | 0% | 0% | 0% | 0% |
  | Hard | 27.1 (24-33) | 5.4 | 100% | 98% | 79% | 98% | 57% | 6% | 9% | 0% | 0% | 0% |
  | Expert | 24.6 (22-28) | 8.2 | 100% | 98% | 82% | 93% | 65% | 8% | 22% | 65% | 2% | 78% |
- **Unique solutions, always.** Clues are removed in symmetric pairs (Expert also tries single squares
  afterwards, for a leaner grid) and a removal is kept only if the logical solver can still finish
  without going past the target level (every step is a valid deduction, so finishing proves the
  answer is unique). Tests re-check uniqueness with a brute-force solver.
- **Fast on the phone.** JVM timings: Easy ~2 ms, Medium ~60 ms, Hard ~120 ms, Expert ~150 ms per
  puzzle. The app also keeps two ready-made puzzles per level in the background, so New game is
  instant.
- **Board** sized from his 360 dp screen width (~38 dp squares, digits two thirds of the square,
  sized in dp so font scale can't overflow them). Givens bold dark ink, his numbers deep blue
  (#1A4CA6: 5.5:1 on the picked square, 7.9:1 on plain), wrong numbers red. Picking a square shades
  its row, column and box (16% blue) and every square with the same digit. Notes show in a 3x3 grid
  in 78% ink; the picked digit is bold blue inside notes.
- **Number pad 1-9** with remaining counts; a digit disappears once all nine are placed correctly.
- **Undo, Erase, Notes on/off, Hint.** Hint fills one square and explains the step the answer
  really depends on, naming where it happens, e.g. "This is the only place for 8 in the top-middle
  box." or "In the top-middle box, the 4 must go in column 5, so the rest of column 5 can't be 4. So
  this is the only place for 4 in row 5." The solver's chain is pruned to the steps the answer needs
  (each step records the candidates it relies on; a step is dropped if the answer still follows
  without it), and the hardest remaining step is explained. The answer square is gold, the step's
  pattern squares light gold, its area a pale wash. It prefers the square he has picked if that
  square has a direct answer, and fixes a wrong number first. The message text shrinks to fit its
  box, so the answer at the end is never cut off at large font sizes.
- **Mistakes:** with "Show mistakes" on, wrong numbers turn red and count. With the three-mistake
  limit on, the third one shows a gentle panel: "No harm done. Take it back" (free second chance)
  or start a new puzzle. "Take it back" returns the square to how it was before the mistakes (an
  earlier wrong number there is cleared too, notes come back). Correct numbers lock in place while
  mistakes are shown.
- **Timer** runs only while the board is on screen; Pause hides the board and stops the clock.
- **Home:** Continue (shows the game and time), New game (level chooser with best times),
  Daily challenge, Stats, Settings, How to play.
- **Daily challenge:** calendar by month (today's puzzle is made in the background at start-up and
  cached, so opening a day doesn't flash a wait panel); each day's puzzle is generated from the date, so it's the
  same on every phone. Mon/Tue Easy, Wed-Fri Medium, Sat/Sun Hard. Finished days get a blue circle;
  past days can be played or replayed (a replay doesn't count as another win).
- **Stats:** overall puzzles solved and dailies done; per level games, wins, win rate, best time,
  average time, current and best streak, perfect games (no mistakes, no hints), hints used; the
  last 25 games with mistakes and hints. Two-tap reset (a game under way stays counted as started).
- **Settings** (defaults in brackets): sound effects [off], show mistakes [on], three-mistake limit
  [on], highlight row and column [on], highlight same numbers [on], highlight duplicates [on],
  auto-remove notes [on], show timer [on].
- **Saves after every input** (board, notes, mistakes, hints, timer, picked square, notes mode,
  pause), and synchronously when the app goes to the background. Undo history is saved too. If the
  app is killed during a game it reopens straight into that game. The game clock uses the
  monotonic clock (`SystemClock.elapsedRealtime`).
- Sound: soft synthesized click / low tone / chime (no audio files), only when turned on.

## Rules details
- A game counts as played from his first input. Starting another game while one is under way ends
  it (streak broken, listed as "not finished"). The New game and Daily screens say so first.
- Mistakes are not undone by Undo (same as Sudoku.com). Hints never count as mistakes.
- With "Show mistakes" off, no mistakes are counted; if the board fills up with wrong numbers, the
  wrong ones are shown in red.

## How it was verified
- `./gradlew :sudoku:core:test`: 51 tests, all passing.
  - Solver: geometry, uniqueness and no-solution detection, random full grids valid and unbiased.
  - Logic soundness: on ~1,000 generated puzzles every step of every technique places the right
    digit and never eliminates the answer; every technique except naked quads is exercised.
  - Generator: every corpus puzzle has exactly one solution and grades to its level (Easy 30-34
    clues, singles only; Medium 1-3 Medium steps; Hard more, or triples/X-wing, and solvable without
    Expert techniques; Expert not solvable without them); clue counts and step counts rise by level
    (`DistributionReport`); generation time limits; daily puzzles pinned for three dates.
  - Hints: every step a hint explains is needed (removing any one breaks the deduction), the answer
    follows from them, the explained step is the hardest and its cells are the ones shaded; hints
    alone solve every corpus puzzle; longest possible hint 174 characters (limit 175).
  - Game rules: entry, locking, mistakes and limit, second chance (incl. earlier wrong number in the
    same square), auto-check off, notes and auto-removal, erase, undo of every input type,
    remaining counts, conflicts, JSON round trip.
  - Stats: wins, times, averages, streaks per level, daily marking, replays not counted, reset
    during a game, hints totalled, recent cap, time format.
  - Fuzzer: 3,000 random games with random settings and inputs (invariants: givens never change,
    no notes on filled squares, mistake limit respected, WON iff board equals solution, save
    round-trip) plus 500 runs checking undo is an exact inverse.
- Release build (R8 + shrinkResources) on the `dad-sudoku` emulator at 1080x2340 / 480 dpi
  (360 x 780 dp) with 3-button nav, at font scale 1.0 and 1.3. Screenshots in `shots/`: home, new
  game chooser, game start, mistake, notes, hints (single and with a reasoning step), pause,
  take-back after three mistakes (panel itself in fs13-mistakes), daily game, daily win, calendar with a finished day,
  stats, settings, how to play, launcher. `fs13-*.png` are the font-scale-1.3 checks (hint text,
  home, new game chooser scrolled, stats, settings, daily, mistakes panel, longest hint seen).
  At 1.3 the new-game chooser scrolls and the level tabs on Stats cap their font scale so all four
  fit. `anim-sheet.png` is a contact sheet of the highlight transitions.
- Kill test: backgrounded mid-game, `am force-stop`, relaunched: it opened straight into the game
  with board, picked square, notes mode and timer as they were.
