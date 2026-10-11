# Solitaire

Klondike for Dad, replacing com.dna.solitaireapp (79 h a year). No ads, no shop, no pop-ups.

- `core/`: pure Kotlin. `Klondike.kt` (immutable `@Serializable` game state, rules, scoring, undo, clock),
  `Hints.kt` (tap-to-move, hints, "no moves left"), `Solver.kt` (full-information solver), `Daily.kt`, `Stats.kt`.
- `app/`: Compose UI. `Board.kt` (layout, cards, gestures, animations), `GameScreen.kt`, `MenuScreens.kt`
  (home, settings, statistics, daily calendar), `GameViewModel.kt` (saves, clock, stats), `Look.kt`
  (big-index card face, card backs, table colours, icons), `Sounds.kt`.

## Features
- **Klondike, Draw 1 by default** (Draw 3 in Settings, applies from the next game). Stock can be turned over
  as often as you like in both modes (each pass costs points, see scoring).
- Layout like his app: foundations top-left with a faint "A" in empty slots, waste then stock top-right (stock on
  the left, foundations on the right with **Left-handed**), 7 columns. Cards are sized from the screen width
  (7 across 360 dp, about 48 dp each) with an extra-large rank and suit index across the top. Face-down cards stack
  tightly; long columns squeeze face-down cards first, then face-up cards, to stay on screen.
- **Tap a card to move it** (foundation cards only come down by dragging, never by a stray tap): foundation first for a single top card, then a column with cards, then an
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
  bring a card back down from a foundation, only when that leads to real progress (a face-down card turned over
  or a card out of the stock) within two moves. Hints never undo the move just made, so following them can't go
  round in circles. When nothing helps, **No moves left** appears by itself after the move (no need to press
  Hint), offering Undo or New game. Banners take space from the board, so columns squeeze up above them.
- **New game** (and playing a different daily) asks first when a game is under way: "Start a new game? This
  one will count as a loss."
- **Auto-complete** button as soon as every column card is face up, even with cards left in the stock or
  waste (it sends those straight up too). The clock stops the moment it is offered. Cards fly up one at a
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

## Scoring (exactly like his old app, by default)
Decompiled from his app's APK (com.dna.solitaireapp 28.0.4, `research/solitaire.md`); it reproduces all 10 of
his top scores. All scoring is in `core/.../Scoring.kt` behind one `ScoreRules` interface.

| | |
|---|---|
| Waste → column | +45 |
| Waste → foundation | +105 |
| Column → foundation | +60 |
| Foundation → column | −75 |
| Column → column, drawing | 0 |
| Card turned face up | +25 |
| Undo | the move's points come off exactly, then −25 (a turned-up card's +25 comes off with no extra charge) |
| Deck pass (stock turned over) | Draw 1: −200 every pass. Draw 3: first three free, then −200 each |
| Time | no penalty during play |
| Win bonus | (20 × score) / whole seconds, integer division, no cap |

Score never goes below 0 (each deduction is clamped). Auto-complete cards from the stock score like a waste →
foundation play (+105), as if drawn and played. Checks: drawing and then playing waste → column gives 45 (his
s08 → s09 screenshots); each of his top-10 entries solves to a whole-5 base (5305 at 116 s = 4525 + 780;
5371 at 140 s = 4700 + 671).

**Windows scoring** stays available in Settings (+5 / +10 / +5 / −15, −100 per Draw 1 pass, Draw 3 −20 after the
third pass, −2 per 10 s, 700000 / seconds bonus for wins over 30 s, no undo charge). Windows-scored games count
as wins but stay out of the best score and top 10, which use his old app's scale.

## Stats
Draw 1 statistics carry his imported history; Draw 3 games are tracked separately (chips at the top of the
Statistics screen; Settings' draw mode picks which one opens). Replays of a daily he has already solved don't
count at all. If saved stats ever fail to load, `Store` keeps the raw text in `<key>.unreadable` and the app
tries a lenient read of it before falling back, so his records aren't silently replaced (BEST_PRACTICES 20a).

## How it was verified
- `./gradlew :solitaire:core:test`: 37 tests (plus one emulator-driver helper, skipped by default), all pass:
  - rules unit tests: deal, Draw 1/Draw 3, unlimited deck passes, column and foundation rules, runs, turn-over,
    undo, win, tap-to-move (foundation cards don't move on tap), hints (including foundation-down), no moves
    left, JSON round trip, stats and top-10 insertion, clock formats.
  - **old app scoring** (`OldAppScoringTest`): every rule, undo costs, deck passes in both modes with clamping,
    no time penalty, the win bonus, draw then waste → column = 45, and all 10 of his top scores solve to a
    whole-5 base (5305 → 4525, 5371 → 4700). Windows scoring keeps its own tests.
  - **hint bot** (`HintBotTest`): follows only the hints across 5000 deals (2500 Draw 1, 2500 Draw 3, including
    the Sept 8 daily and seeds 5 and 24 from the review) and fails on any repeated position. None repeat. Draw 1:
    1249 reach auto-complete, 1251 end on "No moves left". Draw 3: 442 and 2058.
  - auto-complete with cards left in the stock or waste always finishes (493 games), scores like normal plays,
    and undoes exactly.
  - **fuzzer**: 3000 random games, 1.5 M actions mixing moves, undos and clock ticks. Checks 52 unique cards,
    foundation order, face-up runs, score ≥ 0 and moves = history after every step, and that every undo restores
    the exact position with the score less the undo cost.
  - solver: about 75% of random Draw 1 deals proven winnable, every solution replayed to a win. All 365 dailies of
    2026 are winnable. Three daily dates are pinned to their exact deal, and the longest hint text fits.
- Release build (R8 + shrink) on the `dad-solitaire` emulator (360 x 780 dp, 3-button nav), font scale 1.0 and 1.3:
  - Today's daily played entirely through the UI with solver-generated drags and taps (110 moves, the move counter
    checked after each). Auto-complete was offered with cards still in the stock, the clock stayed at 5:13 while
    the button waited, then the cascade and the win panel: 4125 + 20 × 4125 / 313 = 4388.
  - Oct 4 daily followed by hints until stuck: "No moves left" appeared by itself after the last move, with the
    columns squeezed above the banner. Undo then took 105 + 25 off (1120 → 990).
  - The new-game confirmation (from the bar and from the calendar), the scoring settings, Draw 1 / Draw 3 stats.
  - Earlier: kill and relaunch restores screen, board and clock; animation contact sheets in `shots/anim-*.png`.
- Bugs found on the emulator and fixed: lost drag motion on fast flicks; touches hitting the old layout; a hint
  redraw re-dealing the board; card accessibility labels placed before the card offset; "New game" clipped in
  the confirmation.

`tools/make_sounds.py` regenerates the three sound effects.
