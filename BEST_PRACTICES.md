# Best practices for Dad's games

A running list. Every game in this repo follows it; add to it whenever something bites us.
Origin of each rule in brackets.

## Who it's for
1. **The player is John's dad** (Galaxy S21 FE, 411 x 891 dp, 480 dpi, 3-button nav bar). He plays for
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
   re-check every other element on the 411 dp screen. [Euchre trick cards covered seat tags]
11. Edge-to-edge with `systemBarsPadding()`; keep tappable controls clear of the 3-button nav bar.
12. Prompts say exactly what to do ("Your turn. Tap or swipe up a card."). Hint button on every game.

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

## Process
29. Write it down: when a bug or a review finding teaches something general, add a rule here.
