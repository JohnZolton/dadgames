# Euchre

Android euchre (Kotlin + Jetpack Compose). Built for a relaxed, big-type game against computer players.

- `core/`: pure-Kotlin rules engine (`Game.kt`), card logic (`Cards.kt`), rule options (`Rules.kt`) and bots (`Ai.kt`).
- `app/`: Compose UI. Game state and settings persist in SharedPreferences, so a game resumes after the app is closed.

## Rules options
Players (4 partners / 3 cutthroat), play to 5/7/10/11/15, deck 24/28/32, joker (Benny), stick the dealer,
going under (farmer's hand), no ace no face redeal, call the turned-down suit, no trump, going alone,
defend alone, Canadian loner, partner's best card, who leads against a loner, all point values, super euchre.
Presets: Standard, Stick the dealer, Canadian, British (Benny), Bar rules, Cutthroat.

## Computer players
Deals are always a fair shuffle; difficulty only changes how the bots think. Partner and opponents each have:
- **Skill (IQ 0-100)**: <35 blunders, <45 forgets played cards, <25 ignores partner, 65+ simulates the
  rest of the hand from visible information (75+ tracks voids, 85+ also simulates bids).
- **Boldness (0-100)**: how light it calls trump, goes alone and defends alone.

Tournament vs an IQ-50 team (400 games each): IQ 0 18%, 20 28%, 40 47%, 60 51%, 70 58%, 85 65%, 100 64%.
Re-run: `./gradlew :core:test --tests '*Tournament*' -Ptournament -Pgames=400`.

## Build
`./gradlew :core:test :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (R8-optimized, signed with the debug key so it installs over debug builds). Use the release build on phones: debug builds stutter in animations.
