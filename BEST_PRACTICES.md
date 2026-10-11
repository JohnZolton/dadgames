# Best practices for Dad's games

A running list. Every game in this repo follows it; add to it whenever something bites us.
Origin of each rule in brackets.

## Who it's for
1. **The player is John's dad** (Galaxy S21 FE, 360 x 780 dp, 480 dpi, 3-button nav bar). He plays for
   hours a day, keeps hints on, turns sound off, and likes **big cards and big numbers**. Design for that
   screen first; check nothing overlaps or wraps there. [research]
2. **No ads, no pop-ups, no currency shops, no events, no fake buttons, no "remove ads".** Play money is
   fine; if he goes broke, top him up for free. [research: every app he uses is full of this]
3. **Sound defaults off**, with an obvious toggle. [research: off in all his apps]
4. **Fair randomness.** Never rig deals or piece generation. Difficulty comes only from rules and AI
   skill. [Euchre, John's explicit ask]
5. **Bring his history over.** Seed stats/high scores from `research/README.md` so his records continue.

## Words and layout
6. **Plain words beat symbols.** In sentences, write "Ace of Spades", not "A♠" (suit glyphs in body
   text can render as dark emoji). Symbols are fine on the cards themselves, with `︎` appended.
   [Euchre]
7. **Buttons never wrap.** `maxLines = 1, softWrap = false`, modest content padding, and when several
   share a row give them `Modifier.weight()` roughly proportional to label length. [Euchre "Go alone"]
8. **Single glyphs in circles must be centred:** `PlatformTextStyle(includeFontPadding = false)` +
   `LineHeightStyle(Center, Trim.Both)` + `lineHeight = 1.em` (`ui`'s `centeredGlyph`). [Euchre dealer chip]
9. **Whose turn / what to do next must be unmissable**: solid gold glowing tag, gentle pulse, an
   arrow, and the prompt in larger gold text when it's his move. [Euchre]
10. **Size things from the real screen width** and leave room for neighbours; when a table element grows,
   re-check every other element on the 360 dp screen. [Euchre trick cards covered seat tags]
11. Edge-to-edge with `systemBarsPadding()`; keep tappable controls clear of the 3-button nav bar.
12. Prompts say exactly what to do ("Your turn. Tap or swipe up a card."). Hint button on every game.
12a. **Size digits and glyphs inside fixed cells in dp derived from the cell size, not sp**, so a large
    system font setting can't overflow the board. [Sudoku]
12b. Put extra detail on a **second line** of a big button (e.g. "Continue / Medium · 4:12") rather than
    appending it to the label. [Sudoku: single-line pill clipped]
12c. Generated help text (hints, explanations) gets a **test-enforced length limit** that fits its
    area on the 360 dp screen. [Sudoku]
12d. Decorative art must obey the game's own rules (no repeated digit in a Sudoku box, no impossible
    card layout); regular players notice instantly. [Sudoku]
12e. Launcher icon foreground stays inside the central 66 dp safe circle; check it in the app drawer
    with the round mask, not just the vector preview. [Sudoku]
12f. **Hint text must describe the step the answer actually depends on.** Drop reasoning steps that
    aren't needed, explain the hardest remaining one, and highlight the cells it talks about. [Sudoku review]
12g. Check every text area at **system font scale 1.3** (`adb shell settings put system font_scale 1.3`),
    not just at 1.0 or against a character limit. [Sudoku review]
12h. Daily/seeded content gets a test pinning a few dates to known output, so engine changes can't
    silently rewrite past dailies. [Sudoku review]
12i. Difficulty levels should match what players of the common apps expect from the same names;
    grade by how much hard reasoning a puzzle needs, not only the hardest single step. [Sudoku review]
12j. Kill-and-relaunch must restore the **screen** too (in a game → back in that game), not only the data.
    [Sudoku review]
12k. On screens, don't mirror small indices: a rotated 6 reads as 9 and 10 as "01". [Blackjack]
12l. Lay out fixed-position tables from the bottom up (controls, then the player's cards, then the
    opponent) so nothing collides, and cap font scaling (≈1.1x) on the play surface itself. [Blackjack]
12m. On resume, place everything where it belongs (cards, totals, chips); don't replay deal animations.
    A card's total updates when the card lands, not when it leaves the deck. [Blackjack]
12n. Count large numbers (balances) with Long/whole-number animations; a Float can't hold 559,864,578.
    [Blackjack]
12o. Debug-only intent hooks (stack the deck, set the balance, jump to a state) make screenshots of
    rare states quick; release builds must ignore them. [Blackjack]
12p. Show records being broken as **persistent** text for the rest of the game ("New best today!"), not
    only a timed banner that's easy to miss. [Blocks]
12q. Gesture callbacks must read the **latest** game state (keep them in a remembered holder updated each
    composition); a `pointerInput` keyed only on the piece kept validating against a stale board. [Blocks]
12r. Expose hint targets and key state to accessibility (content descriptions like "hint at row 3,
    column 5"); helps TalkBack and makes scripted end-to-end tests easy. [Blocks]
12s. Drags: set the offset from the absolute pointer position (`change.position - downPosition`) on every
    event **including the up event**; summing deltas loses the last movement on fast flicks. [Solitaire]
12t. Hit-test touches against the **latest game state**, not the last drawn frame; a tap right after a
    move must not hit the old layout. Keep per-item animation state above composables that get
    recreated, or redraws replay animations. [Solitaire]
12u. **Script a whole game end to end** on the emulator (from a solver/bot), checking a visible counter
    after each step. It finds bugs screenshots miss. [Solitaire, Blocks]
12v. Read the emulator's real density (`wm density`); never assume a px-per-dp ratio. [Solitaire]
12w. Floating controls (hint bulb, signs) need an explicit `zIndex` above table items; check them
    against the **longest realistic** hand/stack. [Blackjack review]
12x. Charts or canvases inside scrolling screens must not consume vertical drags. [Blackjack review]
12y. **Action rows keep fixed slots** (grey out unavailable actions) so repeated taps never land on a
    different button. [Blackjack review]
12z. Hold gestures need a hold well beyond the system long-press (≈800 ms) with visible progress, so slow
    deliberate taps don't trigger them. Pause game loops when the app is backgrounded. [Blackjack review]
13a. When hints can undo progress, test with a bot that **follows the hints** across thousands of games
    and fails on any repeated position; when no real progress exists, say so. [Solitaire review]
13b. Don't put a rule from research notes into the app or its help text ("same as your old app") until
    a screenshot or the original's code confirms it. [Solitaire review: scoring]
13c. **Any button that throws away a game in progress asks first** and says what it costs ("counts as
    a loss"). Detect "stuck" automatically after every move; don't wait for him to press Hint.
    [Solitaire review]

## Interaction and animation
13. **Support both tap and drag/swipe** for moving pieces/cards. [Euchre]
14. **Hand-offs are seamless:** when something moves from one container to another (hand → table, tray →
   board, column → foundation), start the destination animation at the exact on-screen position, size
   and rotation where the source left off. Never let it vanish and reappear elsewhere. [Euchre swipe]
15. **Drags follow the finger with plain state** (`mutableFloatStateOf`) updated synchronously in the
   gesture callback. Don't launch a coroutine per pointer event. Use `animate()` only for spring-back.
   [Euchre swipe]
16. Things that leave a row/stack make the rest slide to close the gap (`animateFloatAsState` on x).
17. **Ship release builds to phones**: R8 minify + shrinkResources, with kotlinx-serialization keep rules
   (`app/proguard-rules.pro` template). Debug builds stutter. After shrinking, verify that saving and
   restoring still work. [Euchre]
18. Keep the UI thread free: AI/search on `Dispatchers.Default` with a state snapshot copy; serialize
   saves on a single background dispatcher. [Euchre]

## State and code structure
19. **Pure-Kotlin `core` module** per game (rules, generation, AI, scoring) with no Android imports;
   `app` module is Compose UI only. [Euchre]
20. Game state is a `@Serializable` snapshot; **save after every action**, so the app resumes exactly
   where he left off. Previous screen state is never lost if Android kills the app.
20a. **A save that fails to load must never be silently replaced by fresh/seed data.** Keep balances
   and records under their own small keys, and use `Store.load` (it backs up unreadable saves to
   `<key>.unreadable`). Add new fields with defaults so old saves keep loading. [Blackjack review]
21. Stats screen: big headline numbers, streaks, best scores, recent games, two-tap reset. Show a
   one-line stat on the home screen and in the end-of-game panel. [Euchre]
22. Settings: grouped cards, every toggle has a one-line plain-English explanation; presets for
   rule sets; difficulty as simple named levels plus fine-tuning. [Euchre]

## Verification (every change)
23. **Core tests**: rules unit tests, a fuzzer that plays thousands of random games checking invariants
   (no cards/cells lost or duplicated, scores consistent, game always terminates), and, for AI, a
   tournament showing difficulty levels are ordered. [Euchre]
24. **Run it on an emulator set to his screen** (`wm size 1080x2340`, `wm density 480`) and look at
   screenshots of every screen and state: start, mid-game, prompts, end panel, settings, stats.
25. **Check animations with a screen recording**: `adb shell screenrecord`, then
   `ffmpeg -vf fps=20,...` and tile the frames into a contact sheet. Look for jumps, freezes, overlaps.
   [Euchre swipe]
26. Drive Compose UIs with `uiautomator dump` (text + bounds) rather than guessing coordinates.
27. **Use your own emulator**: every agent gets its own AVD and port. Never use another session's
   emulator, and never drive a real phone without checking it's free. [Euchre: shared emulator got hijacked]
28. All apps are signed with the same debug keystore, so new builds install over old ones and keep his saves.

## Releases (dad updates through Obtainium from GitHub releases)
29. Every release bumps the app's `versionCode` **and** `versionName`; Obtainium compares versions.
30. One repo, one release per app version: tag `<app>-v<version>` (e.g. `blocks-v1.0.3`), title
    `<App> <version>`, asset `<App>-<version>.apk` (release build). Obtainium filters by title/APK name.
31. Never change an app's `applicationId` or signing key after it's on his phone, or updates break.

## Process
32. Write it down: when a bug or a review finding teaches something general, add a rule here.
