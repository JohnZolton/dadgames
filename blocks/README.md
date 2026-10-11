# Blocks

An ad-free replacement for Woodoku (Tripledot), Dad's most-played game (150 h a year). Classic mode plays like
the original. The daily challenge is kept simple. App id `dadgames.blocks`, palette `GamePalette.Wood`.

## Features

- **Classic**: a 9x9 board with the nine 3x3 boxes shaded alternately, as in Woodoku. You get a tray of 3 pieces,
  and a new tray only after all 3 are placed. Full rows, columns and 3x3 boxes clear. Clearing several at once is a
  combo. The game ends when none of the tray pieces fits anywhere. Tray pieces that don't fit are faded.
- **Dragging**: on touch-down the piece lifts out of the tray and grows to board-cell size. It floats centred above
  the finger, with its bottom edge about 60 dp (1.6 cells) above the touch point, so the finger never covers it. A
  translucent shadow shows exactly where it will land, and the blocks of every row, column or box that would clear
  glow gold. On a valid drop the piece snaps into its cells in 90 ms. Cleared blocks shrink and fade in a wave
  spreading out from the piece, and a "+points" floats up. An invalid drop glides back to the tray slot from wherever
  it was released (position and size are interpolated, so it never jumps). New trays slide in from the right.
- **Tap to place** (BEST_PRACTICES #13): tap a tray piece (it glows), tap the board to show its shadow centred on that
  cell, then tap the shadow to place it.
- **Hint**: the best spot for one tray piece pulses on the board and its tray piece glows. Drag it there, or tap the
  glowing shadow.
- **Top bar**: back, current score (counts up), "TODAY" best with a crown, and settings. The Today box turns gold
  and tracks your score once you beat today's best. "New best today!" or "New best ever!" shows in the status line
  the moment you pass the record, and again as the title of the end panel.
- **My High Scores**: Today, This week (Monday to Sunday, like Woodoku's calendar), This month, This year and Best
  ever. These are seeded with his Woodoku records as of 2026-10-11: today 750, week 1380, month 1380, year 2158,
  best ever 2447. Each period's best is stored with its period key, so a new day, week, month or year starts at 0.
- **Daily challenge**: one seeded puzzle per date. Some wooden blocks start on the board and 6 to 10 of them hold
  gems. Clear the rows, columns or boxes holding every gem to win. A month calendar marks solved days and highlights
  today. You can play any past day, and Restart takes two taps. Monthly trophies come at 1, 3, 10 and 24 wins. A
  classic game in progress is kept separately and isn't touched.
- **Stats**: games played, average score, best score per period, blocks cleared, rows/columns/boxes cleared,
  biggest combo, longest streak, daily wins, and the last 20 games with record badges. The home screen shows a
  one-line summary.
- **Settings**: sound (off by default; synthesized wooden tock and clear chime, no assets), vibration on drop,
  "How to play" (rules, scoring, daily, hint), and two-tap "Reset stats" and "Reset high scores".
- **Saving**: the game state is saved after every move (on a background writer), and so are classic and daily
  games and which screen you were on. If Android kills the app, relaunching lands on the same board with the same
  upcoming pieces.

## Rules and scoring (as implemented, `core/.../Game.kt`)

- **Pieces**: the 49 shapes in Woodoku's tray, from the WoodokuAI project, which read them off the real app
  (github.com/CosmicSubspace/WoodokuAI, `piecedefs.txt`). They are: single; lines of 2, 3, 4 and 5 in both
  orientations; diagonals of 2, 3 and 4 in both directions; the 3-block corner (4 turns); the 2x2 square; L
  tetromino (8); T tetromino (4); S/Z (4); the big 3x3 corner (4); the big T (4); the plus; and U (4). There is no
  3x3 square.
- **Generation**: each tray slot is drawn uniformly from all 49 shapes using a SplitMix64 generator whose state is
  saved. It never looks at the board (fair random, no rigging), and a test checks this.
- **Score per move**: +1 for each block placed. If the move clears `c` regions (rows + columns + boxes), add
  `28*c + 10*s - 20`, where `s` is the streak: the number of consecutive moves, this one included, that cleared
  something. A move without a clear resets the streak to 0.
  - On a fresh streak, that is +18 for one clear, +46 for a double (combo) and +74 for a triple.
  - Each further move in a row that clears adds another 10.
  - Source: the "woodoku" score mode of gym-woodoku (github.com/helpingstar/gym-woodoku). I couldn't find an
    official Tripledot formula. Screenshot w08 confirms +1 per block (a 2x2 gave 4).
- **Game over**: when no remaining tray piece fits anywhere. This is checked after every move and after each refill.
- **Daily**: the board, gems and piece stream are seeded from the date. A layout is only accepted if the hint bot
  solves it. Otherwise the next variant is tried, so no day is impossible. Retrying a day deals the same pieces.

## How it was verified

- `./gradlew :blocks:core:test`: **20 tests, all passing** (about 2.5 minutes).
  - `EngineTest` (16): the piece set (49 unique shapes, family counts, no 3x3); bitboard ops; the 27 regions; bounds
    and overlap; +1 per block; row, column and box clears; 2- and 3-region combos (+46 / +74); streak growth and
    reset; refill only when the tray is empty; game over; preview matching placement; save/restore giving the
    identical state and identical future pieces; a chi-square check that deals are uniform and ignore the board;
    gem collection and daily win; the bot taking an obvious clear.
  - `FuzzTest`: 3000 random classic games. After every move it checks that the score adds up, placed cells were
    empty, `blocks on board = start + placed - cleared`, no full region is left, gems sit only on blocks, streak
    rules hold, the tray refills correctly, game over is correct, the game always terminates, and a JSON round-trip
    is exact.
  - Also in `FuzzTest`: 20 bot games against random play (bot about 3800 per 300 moves vs random about 100), and 90
    daily puzzles (2026-09-01 onward). The dailies are deterministic and well formed, all 90 are solvable by the bot,
    and random play on them keeps every invariant.
  - `DailyPinTest`: three dates (2026-10-11, 2026-12-25, 2027-02-28) are pinned to their exact board, gems and
    first tray, so engine changes can't rewrite past dailies (BEST_PRACTICES 12h).
- Release build (R8 minify and shrinkResources) installed on the `dad-blocks` emulator (1080x2340 @ 480 dpi = 360 x 780 dp,
  3-button nav). Screens checked in `shots/`: home, game start, drag in progress (lifted piece + shadow), mid-game, clear
  animation frames, tap-to-place, hint, end panel, daily puzzle, daily solved panel, calendar with a solved day,
  stats, settings, how to play, launcher icon.
- Screen recordings were cut into contact sheets to check that the invalid-drop glide-back starts at the release point
  and ends in the tray slot, and that the clear wave and floater play out.
- Font scale 1.3 (`shots/fontscale-1.3-*`): home, game, calendar, settings, stats and the end panel. Nothing clips.
  Text inside fixed boxes (score, Today, gems, day cells, milestones, high-score columns, status line) is sized in
  dp so it can't overflow. Stats tile labels wrap to two lines.
- Kill test: `am force-stop` mid-game, then relaunch. The screen was pixel-identical to the one before the kill: same screen, board, tray and score (BEST_PRACTICES 12j).
