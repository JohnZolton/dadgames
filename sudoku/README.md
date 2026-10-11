# Sudoku

A calm, ad-free classic Sudoku for Dad, replacing Sudoku.com (which opens with full-screen ads, a
fake "Skip" that opens the Play Store, and a chain of event pop-ups). App id `dadgames.sudoku`,
palette `GamePalette.Paper` (dark ink on cream).

## Features
- **Four levels, graded by technique, not clue count.** A human-style solver (`core/Logic.kt`)
  solves each puzzle using the simplest technique that makes progress; the hardest one it needs
  sets the level:
  - Easy: hidden and naked singles only (36+ clues).
  - Medium: needs pointing, box/line, naked or hidden pairs.
  - Hard: needs naked/hidden triples, X-wing, XY-wing or XYZ-wing.
  - Expert: needs swordfish, naked quads, simple colouring or XY-chains.
- **Unique solutions, always.** Clues are removed in symmetric pairs and a removal is kept only if
  the logical solver can still finish (every step is a valid deduction, so finishing proves the
  answer is unique). Tests re-check uniqueness with a brute-force solver.
- **Fast on the phone.** JVM timings: Easy ~1 ms, Medium ~20 ms, Hard ~100 ms, Expert ~40 ms per
  puzzle. The app also keeps two ready-made puzzles per level in the background, so New game is
  instant.
- **Board** sized from the 411 dp screen width (~44 dp squares, digits two thirds of the square,
  sized in dp so font scale can't overflow them). Givens bold dark ink, his numbers blue, wrong
  numbers red. Picking a square shades its row, column and box and every square with the same digit.
  Notes show in a 3x3 grid; the picked digit is bold blue inside notes.
- **Number pad 1-9** with remaining counts; a digit disappears once all nine are placed correctly.
- **Undo, Erase, Notes on/off, Hint.** Hint fills one square and explains why in plain English,
  e.g. "This is the only place for 8 in this box." or "In the box, 8 has to go in one column, so the
  rest of that column can't be 8. After a few steps like that: only 7 fits here." It prefers the
  square he has picked if that square has a direct answer, and fixes a wrong number first. The
  square and the area the hint talks about are shaded gold.
- **Mistakes:** with "Show mistakes" on, wrong numbers turn red and count. With the three-mistake
  limit on, the third one shows a gentle panel: "No harm done. Take it back" (free second chance)
  or start a new puzzle. Correct numbers lock in place while mistakes are shown.
- **Timer** runs only while the board is on screen; Pause hides the board and stops the clock.
- **Home:** Continue (shows the game and time), New game (level chooser with best times),
  Daily challenge, Stats, Settings, How to play.
- **Daily challenge:** calendar by month; each day's puzzle is generated from the date, so it's the
  same on every phone. Mon/Tue Easy, Wed-Fri Medium, Sat/Sun Hard. Finished days get a blue circle;
  past days can be played or replayed.
- **Stats per level:** games, wins, win rate, best time, average time, current and best streak,
  perfect games (no mistakes, no hints), dailies done, and the last 25 games. Two-tap reset.
- **Settings** (defaults in brackets): sound effects [off], show mistakes [on], three-mistake limit
  [on], highlight row and column [on], highlight same numbers [on], highlight duplicates [on],
  auto-remove notes [on], show timer [on].
- **Saves after every input** (board, notes, mistakes, hints, timer, picked square, notes mode,
  pause), and synchronously when the app goes to the background. Undo history is saved too.
- Sound: soft synthesized click / low tone / chime (no audio files), only when turned on.

## Rules details
- A game counts as played from his first input. Starting another game while one is under way ends
  it (streak broken, listed as "not finished"). The New game and Daily screens say so first.
- Mistakes are not undone by Undo (same as Sudoku.com). Hints never count as mistakes.
- With "Show mistakes" off, no mistakes are counted; if the board fills up with wrong numbers, the
  wrong ones are shown in red.

## How it was verified
- `./gradlew :sudoku:core:test`: 44 tests, all passing.
  - Solver: geometry, uniqueness and no-solution detection, random full grids valid and unbiased.
  - Logic soundness: on a corpus of ~400 generated puzzles every step of every technique places the
    right digit and never eliminates the answer; every technique except naked quads is exercised.
  - Generator: every corpus puzzle has exactly one solution, its grade equals its level, it's
    solvable at its level and not one level lower; clue counts drop with level; symmetric givens;
    generation time limits; daily puzzles deterministic by date.
  - Game rules: entry, locking, mistakes and limit, second chance, auto-check off, notes and
    auto-removal, erase, undo of every input type, remaining counts, conflicts, hints (fill, fix
    wrong first, prefer picked square, hints alone solve every corpus puzzle), hint text length fits
    the message area, JSON round trip.
  - Stats: wins, times, averages, streaks per level, daily marking, recent cap, time format.
  - Fuzzer: 3,000 random games with random settings and inputs (invariants: givens never change,
    no notes on filled squares, mistake limit respected, WON iff board equals solution, save
    round-trip) plus 500 runs checking undo is an exact inverse.
- Release build (R8 + shrinkResources) installed on the `dad-sudoku` emulator at 1080x2340 / 480 dpi
  with 3-button nav. Screenshots in `shots/`: home, new game chooser, game start, mistake, notes,
  hint (single and technique), pause, three-mistakes panel, win, daily game, daily win, calendar with
  a finished day, stats, settings, how to play, continue button. `anim-sheet.png` is a contact sheet
  from a screen recording of the highlight transitions.
- Kill test: backgrounded mid-game (Expert, with hints placed and notes mode on), `am force-stop`,
  relaunched: board, picked square, notes mode and timer came back exactly. Also resumed exactly
  after reinstalling over a running game.
