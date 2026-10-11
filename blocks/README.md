# Blocks

An ad-free replacement for Woodoku (Tripledot), Dad's most-played game (150 h a year). Classic mode plays like
the original, with its scoring and piece generator taken from his decompiled APK. The daily challenge is kept
simple. App id `dadgames.blocks`, palette `GamePalette.Wood`.

## Features

- **Classic**: a 9x9 board with the nine 3x3 boxes shaded alternately, as in Woodoku. You get a tray of 3 pieces,
  and a new tray only after all 3 are placed. Full rows, columns and 3x3 boxes clear, and clearing several at once
  counts as one move with several lines. The game ends when none of the tray pieces fits anywhere. Pieces that
  don't fit are faded.
- **Dragging** (BEST_PRACTICES 13d):
  - On touch-down the piece lifts out of the tray and grows to board-cell size. It floats centred above the
    finger, as high as the gap above the tray allows (about 60 dp on his screen).
  - A translucent shadow shows exactly where it will land. Every row, column or box that would clear glows gold,
    and the glow stays on through the drop and into the clear.
  - **While the finger is over the tray there is no landing spot**, so picking a piece up and letting go always
    sends it back. The space between board and tray is sized so the finger is still above the tray when placing
    on the bottom row.
  - The drop commits where the finger lifts: the up event's position is applied, so a quick flick lands where it
    was released. A cancelled gesture (Home, notification shade, a call) glides the piece back.
  - Grabbing the next piece while the last one is still dropping or gliding back finishes that animation at once
    and accepts the new grab.
  - Cleared blocks shrink and fade in a wave from the piece, and a "+points" floats up. The animation's start
    state is set in the same frame as the move (13f). New trays slide in from the right.
- **Tap to place** (BEST_PRACTICES #13): tap a tray piece (it glows), tap the board to show its shadow, then tap
  the shadow to place it. A spot where it can't fit shakes the piece and says "It doesn't fit there".
- **Hint**: the best spot for one tray piece pulses on the board and the piece glows. Tapping the glowing shadow
  places it. The board's accessibility description says where (slot, size, top-left cell).
- **Top bar**: back, current score (counts up), "TODAY" best with a crown, settings. Once today's best is beaten
  (a non-zero best, so the first game of a day doesn't claim a record), the Today box turns gold and the crown
  turns dark so it stays visible. "New best today!" or "New best ever!" stays in the status line for the rest of
  the game.
- **My High Scores**: Today, This week (Monday to Sunday), This month, This year and Best ever, seeded with his
  Woodoku records as of 2026-10-11 (750 / 1380 / 1380 / 2158 / 2447). Each period's best is stored with its
  period key, so a new day, week, month or year starts fresh. The home screen includes the game in progress.
- **Daily challenge**: one seeded puzzle per date. Some wooden blocks start on the board and 6 to 10 of them hold
  gems. Clear the rows, columns or boxes holding every gem to win.
  - A month calendar marks solved days and highlights today. Past days are playable back to October 2026, the
    app's first month; the back arrow stops there.
  - Monthly trophies at 1, 3, 10 and 24 wins. **Each date counts once**, however often it's replayed (13g).
  - Starting another day while a daily is in progress asks first and says how many gems would be lost. Restart
    takes two taps. A classic game in progress is kept separately.
- **Asking before losing anything** (13c): "New game" over an unfinished game asks first. The unfinished game's
  score still counts for high scores but not for the average.
- **Stats**: games played, average score (finished games only), best score per period, blocks cleared, lines and
  boxes cleared, most lines at once, longest streak, daily wins, and the last 20 games (with record and
  "Unfinished" badges). The home screen shows a one-line summary.
- **Settings**: sound (off by default; synthesized wooden tock and clear chime), vibration (a long-press-strength
  buzz on drop), "How to play", and two-tap "Reset stats" and "Reset high scores".
- **Navigation** is a back stack (13e). Back always returns to where you came from, for example
  Game → Settings → How to play → Back → Back lands on the game. The stack is saved, so relaunching after Android
  kills the app lands on the same screen (12j).
- **Saving**: the game is saved after every move, with classic and daily kept separately.

## Rules and scoring (`core/`)

Exact rules come from his decompiled APK v2.11.02 (`research/woodoku.md`, `research/woodoku-pieces.txt`).

- **Score per move** (`Scoring` in `Game.kt`; matches Woodoku exactly): +1 per block placed. A move that clears
  `C` regions (rows, columns and boxes count the same) adds `18·C + 10·(C−1) + 10·S`, where `S` is the number of
  consecutive clearing moves before this one. The code writes it as `28·C + 10·s − 20` with `s = S + 1`.
  - One clear on a fresh streak: +18. Two at once: +46. Three: +74.
  - Each further move in a row that clears adds 10.
  - Woodoku resets the streak when the app restarts; Blocks saves it.
- **Piece generator** (`PieceGenerator.kt`), as in Woodoku:
  - 17 base shapes A–Q, with weights from a 12-stage table. The stage is picked by trays dealt so far this game
    (stages start at trays 0, 1, 2, 3, 6, 7, 9, 10, 12, 15, 19, 24). Early trays are small, friendly shapes;
    diagonals and the plus come in later.
  - Each draw: weighted pick, then a 50% horizontal mirror, then a rotation of 0, 90 or 180 degrees
    counter-clockwise (never 270). So only 6 of the 8 orientations occur, and some shapes are lopsided; for
    example the L tetromino never appears as `#./#./##`.
  - A tray of 3 is filled one piece at a time. A piece whose base shape is already in the tray is redrawn once,
    and the redraw is kept.
  - **one_valid_move_rule**: if none of a freshly dealt tray fits anywhere, one random piece becomes a single
    block. This is the only time the board is looked at, so a fresh tray never ends the game; game over is only
    checked as the tray is played down.
  - The trays-dealt counter is saved with the game. The random generator (SplitMix64) is saved too, so a resumed
    game deals exactly the pieces it would have.
- **Game over**: when none of the remaining tray pieces fits anywhere.
- **Daily**: the board, gems and piece stream are seeded from the date and use the same piece table. A layout is
  only accepted if the hint bot solves it, so no day is impossible, and retrying a day deals the same pieces.
  Woodoku's own daily (10-gem goal, 6 symmetric gem blocks, gems carried on new pieces) isn't copied yet.

## How it was verified

- `./gradlew :blocks:core:test`: **27 tests, all passing** (about 1 minute).
  - `GeneratorTest` (8):
    - the stage table equals the one parsed from `research/woodoku-pieces.txt`;
    - the base cell counts are right;
    - the orientation odds of every shape match the file exactly;
    - over 200,000 draws, base frequencies are within 0.6% of the weights and lines are horizontal 4/6 of the
      time;
    - duplicate redraws keep repeated shapes rare;
    - the single-block swap happens exactly when nothing fits, changes exactly one piece, and otherwise leaves
      the tray identical;
    - the board is otherwise ignored;
    - the trays-dealt counter is saved, and diagonals never appear in a first tray.
  - `EngineTest` (15): the 49 orientations, bitboard ops, the 27 regions, bounds and overlap, +1 per block,
    row/column/box clears, 2- and 3-region clears (+46 / +74), streaks, refill only when the tray is empty, game
    over, preview matching placement, save/restore including future pieces, gem collection and daily win, and the
    bot taking an obvious clear.
  - `FuzzTest` (3):
    - 3000 random classic games, checking every invariant after every move (score, cell counts, no full region
      left, gems on blocks, streak rules, refills, game over, JSON round-trip, termination);
    - the bot against random play (about 2000 vs 70 in 300 moves);
    - 90 daily puzzles: all well formed and all solvable by the bot.
  - `DailyPinTest`: three dates are pinned to their exact puzzle (12h). They were re-pinned after the generator
    change; the boards and gems are the same, only the trays changed.
- Release build (R8) on the `dad-blocks` emulator (1080x2340 @ 480 dpi = 360 x 780 dp, 3-button nav).
- **Real-drag checks**, with screen recordings cut into contact sheets (`shots/contact-*`):
  - touch inside the tray, wiggle 30 px, lift: no shadow, the piece goes back, the score is unchanged;
  - a flick (down in the tray, up over the board with no move events) lands where the finger lifted;
  - grabbing the next piece during the previous piece's glide-back is accepted and placed;
  - a system CANCEL mid-drag over a valid spot, and pressing Home mid-drag, both glide the piece back with the
    score unchanged;
  - a real drag onto a clearing spot keeps the gold glow through the drop, and the clear fades without a blank
    frame (frame-by-frame, at the emulator's native frame rate).
- **Navigation**: Home→Settings→Help→Back→Back, Game→Settings→Help→Back→Back, and the same routes after a
  relaunch from Help, all land where expected. The daily end panel's "Calendar" returns to the calendar.
- **Dailies and records**:
  - replaying and re-solving a solved day kept "1 win in October" and stats daily wins at 1;
  - the calendar's back arrow is disabled in October 2026;
  - both confirmation dialogs appear with the right text;
  - with the clock moved to Monday 2026-10-12, Today and This week rolled over (showing the live game) while
    month and year kept their bests, and the first game of the day showed no record banner.
- **Font scale 1.3** (`shots/fontscale-1.3-*`): game, home ("Solved!", two-line Continue), the confirmation
  dialog, calendar, settings, stats and the end panel. Nothing clips.
- **Kill test**: force-stop mid-game, then relaunch, gives a pixel-identical screen.
- Screenshots of every screen are in `shots/`.
