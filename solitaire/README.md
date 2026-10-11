# Solitaire

Klondike for Dad, replacing com.dna.solitaireapp (79 h a year). No ads, no shop, no pop-ups.

- `core/`: pure Kotlin. `Klondike.kt` (immutable `@Serializable` game state, rules, scoring, undo, clock),
  `Hints.kt` (tap-to-move, hints, "no moves left"), `Solver.kt` (full-information solver), `Daily.kt`, `Stats.kt`.
- `app/`: Compose UI. `Board.kt` (layout, cards, gestures, animations), `GameScreen.kt`, `MenuScreens.kt`
  (home, settings, statistics, daily calendar), `GameViewModel.kt` (saves, clock, stats), `Look.kt`
  (big-index card face, card backs, table colours, icons), `Sounds.kt`.

## Features
- **Klondike, Draw 1 by default** (Draw 3 in Settings, applies from the next game). Stock can be turned over
  as often as you like in both modes, as in Windows. (Windows charges for it, see scoring.)
- Layout like his app: foundations top-left with a faint "A" in empty slots, waste then stock top-right (stock on
  the left, foundations on the right with **Left-handed**), 7 columns. Cards are sized from the screen width
  (7 across 360 dp, about 48 dp each) with an extra-large rank and suit index across the top. Face-down cards stack
  tightly; long columns squeeze face-down cards first, then face-up cards, to stay on screen.
- **Tap a card to move it**: foundation first for a single top card, then a column with cards, then an
  empty column (a king already at the bottom of a column doesn't hop to another empty one). Tapping a card in a
  run moves the whole run. If nothing fits, the cards give a small shake.
- **Drag** single cards and runs. The cards follow the finger and drop on the legal pile they overlap most. An
  invalid drop glides back from where it was let go. Moves animate from the exact spot the card left, and
  turned-over cards flip.
- Header: Time / Score / Moves. The clock starts on the first move and stops while the app is in the background
  or another screen is open.
- Bottom bar: Undo (unlimited, survives restarts), Settings, Daily, Hint, New game.
- **Hint** highlights the cards and where they go, with one plain sentence ("Move the Nine of Spades onto the Ten
  of Hearts."). Order: uncover a face-down card, then build foundations, then play from the waste, then
  useful shuffles, then "tap the stock" (only if drawing through will ever bring up a playable card), and last,
  bring a card back down from a foundation when that opens a move. Otherwise it says **No moves left** and offers
  Undo or New game.
- **Auto-complete** button once every card is face up and the stock and waste are empty. Cards fly up one at a
  time, then the win animation: classic bouncing cascade off the foundations (tap to skip, can be turned off).
- **Daily challenges**: calendar (Draw 1, one deal per date, past days playable), solved days in gold, monthly
  trophies at 10, 20 and every day. Each daily is a fair shuffle seeded from the date. The solver checks it, and if it
  can't prove the deal winnable it moves on to the next seed. So every daily can be won.
- **Statistics** seeded with his record: 1069 won / 3647 played, best score 5371, least moves 90, best time
  1:28, total time 9 d 13:14:57, and his top-10 table (score, time, date, moves). Also win rate, streaks, dailies solved
  and the last 20 games. Two-tap reset. Starting a new game after at least one move counts as a game played and
  lost (as in his app); its time goes into total time.
- **Settings** (defaults are his): Sounds off (synthesized place/flip/win sounds when on), Gameplay
  animations on, Win animation on, Draw 1/3, six table colours (green felt default), five card backs (blue dots
  default), Left-handed. The scoring rules are listed at the bottom.
- Saves after every move (game, undo history, clock), plus every 5 s of play and when the app goes to the background.
  After Android kills the app it reopens on the same screen with the same board, score and time.

## Scoring (Windows standard)
| | |
|---|---|
| Waste → column | +5 |
| Card to a foundation | +10 |
| Turning over a column card | +5 |
| Foundation → column | −15 |
| Draw 1: each turn of the stock back over | −100 |
| Draw 3: each turn-over after the third | −20 |
| Every 10 seconds of play | −2 |
| Score never goes below 0 | |
| Win bonus | 700000 / seconds, for wins over 30 s |

**One deviation, to match his numbers:** the win bonus is capped at the 2:30 value (700000 / 150 = 4666). His
top-10 wins took 1:44 to 2:29 and all scored 5195 to 5371. Uncapped, his 1:56 win would get a 6034 bonus by itself, and his
fastest win (1:28, a 7954 bonus uncapped) isn't in his top 10 at all. With the cap, his wins work out to
530 to 700 points before the bonus, which is what a clean game earns. Our solver-played wins (about 380 points before the
bonus, 4 stock turn-overs each) come to 4844 to 5186. A human who turns the stock over less lands at his 5200 to 5400.
Wins slower than 2:30 get the normal 700000 / seconds. The live check is `ScoringCheck`.

Undo puts the cards, score and move count back exactly, but not the clock or any time penalty already charged.

## How it was verified
- `./gradlew :solitaire:core:test`: 26 tests (plus one emulator-driver helper, skipped by default), all pass:
  - rules unit tests: deal, Draw 1/Draw 3, unlimited recycles, column and foundation rules, runs, turn-over,
    every scoring line, time penalty, undo (including time), win and bonus, tap-to-move choices, hints (including
    foundation-down), no moves left, JSON round trip, stats and top-10 insertion, clock formats.
  - **fuzzer**: 3000 random games (Draw 1 and 3), 1.6 M actions mixing moves, undos and clock ticks. Checks 52 unique
    cards, foundation order, face-up runs, score ≥ 0 and moves = history after every step, and that every undo
    restores the exact previous state. 1075 games were won along the way.
  - undo all the way back always restores the original deal (200 games).
  - solver: 150 of 200 random Draw 1 deals proven winnable (every solution replayed through the engine to a
    win), 44 of 60 Draw 3. All 365 dailies of 2026 are winnable (worst case needed 5 shuffles, about 100 ms each).
  - three daily dates pinned to their exact deal, and the longest possible hint text is checked to fit (72 chars).
- Release build (R8 + shrink) installed on the `dad-solitaire` emulator (1080x2340 @ 480 dpi = 360 x 780 dp,
  3-button nav).
  - A full game, today's daily (147 moves), was played entirely through the UI with adb drags and taps generated
    from the solver, checking the move counter after each one. Then auto-complete, the cascade and the win panel.
    The bonus shown matches 700000 / 476 s.
  - Kill and relaunch mid-game: same screen, board, moves and clock (penalty kept ticking correctly).
  - Animations checked with screen recordings tiled into contact sheets (`shots/anim-*.png`): tap to foundation
    plus flip, auto-complete plus cascade, drag release.
  - Every screen checked at font scale 1.0 and 1.3 (`shots/`): home, game start, mid-game, hint, foundation-down
    hint, no moves left, after kill/relaunch, auto-complete, win animation, win panel, statistics, top scores,
    settings (3 pages), daily calendar, Draw 3 + left-handed + blue table, launcher icon in the app drawer.
- Bugs found on the emulator and fixed: the last bit of finger movement in a fast drag was lost (the drop landed
  short); touches right after a move could hit the old layout; a hint redraw re-dealt the board; stuck positions
  where only a foundation-down move helps said "No moves left"; the shared stats headline wrapped at 360 dp.

`tools/make_sounds.py` regenerates the three sound effects.
