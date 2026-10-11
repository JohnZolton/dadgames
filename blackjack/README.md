# Blackjack

An ad-free replacement for the Blackjack app Dad plays (com.blackjack.casino.card.solitaire, 71 h a year,
17 h in the last two weeks). Single player against the dealer, play money only, with his history carried over:
balance **559,864,578**, **Level 868**, bet **250K**, table **London**, and his settings (sound off, dealer stands
on soft 17, hint button on, auto hint off, insurance off, 8 decks).

## Features

- **Table like his app:** dealer cards at the top with the hole card face down. Player cards are large and
  fanned, with a total bubble (soft hands show both totals, e.g. "7 / 17"). The felt reads
  "LONDON / Dealer must stand on soft 17 / BLACKJACK PAYS 3 TO 2", and between hands it also lists the other rules.
- **Round action buttons with words:** Give up (surrender, only when it's switched on), Stand, Split, Double, Hit.
  Only legal moves are shown.
- **Hint bulb:** shows the basic-strategy move for the rules in force. **Auto hint** puts a pulsing gold ring
  around the right button. With split hands the bulb moves into the button row.
- **Shoe and discard tray:** the shoe gauge has a red cut-card mark at 75% penetration. The discard tray gets
  thicker as cards are used. At the cut card the tray slides back into the shoe with "Shuffling the shoe…" before
  the next deal. Changing the number of decks builds a new shoe.
- **Animation:** cards fly from the shoe one at a time and the hole card flips over. Totals change when a card
  lands, not when it leaves the shoe. The bet stack slides from the betting spot to beside the cards, and after a
  split each new hand's stack slides out from the first. At the end of a hand, winnings come out of the dealer's
  tray, lost chips go to the dealer, and everything left goes to the balance, which counts up or down. Then the
  table is swept into the discard tray. Tap the felt to skip the result pause.
- **Betting between hands:** chip rack with his five chips (500, 1K, 2.5K, 5K, 10K) plus 50K, 100K, 500K and 1M,
  showing only chips the table's maximum allows. Tap to add (chips fly to the bet), Clear, and Deal.
  **Hold Deal to auto-deal:** the same bet every hand until he taps the gold "Auto deal · Tap to stop" sign.
  The last bet is kept. A bet can't go over the balance or the table maximum (a short message explains which).
- **Split hands** side by side, each with its own bet. The active hand has a gold total and the others are dimmed.
- **Tables:** a carousel of 6 cities with the same rules, different felts and higher stakes:
  London 5–1M, Paris 1K–2M, Monte Carlo 10K–3M, Macau 25K–5M, Las Vegas 50K–7.5M, Dubai 100K–10M.
  (His 250K bet needs a 1M maximum, so London's maximum is 1M.)
- **No shop, no green "+" button.** If he can't cover the smallest chip, an "Out of chips" panel gives him 10M
  free chips.
- **Level badge:** a ring around the badge shows XP progress (see "Levels" below).
- **Settings** (defaults are his): sound effects, hint button, auto hint, deal speed (Relaxed / Normal / Quick).
  Table rules: presets (Your usual / Atlantic City / Downtown Vegas), decks 2/4/6/8, dealer hits soft 17,
  insurance, surrender, double on any two or 9–11 only, double after split, split up to 2/3/4 hands.
  Rule changes start with the next hand.
- **Stats:** win-rate ring, hands played, biggest win, current balance and level, a balance-over-time line chart
  (last 200 hands; drag or tap for a hand's value), won / lost / pushed, blackjacks, doubles won (of doubles),
  splits, biggest bet, best balance, current streak (wins or losses), best streak, and the 15 most recent hands.
  Reset needs two taps and keeps chips and level.
- **Saves after every action** and resumes exactly, mid-hand, even after Android kills the app. A relaunch goes
  back to the table if he was there.
- Synthesized sounds (card, chip, win, lose, shuffle); off by default. "How to play" screen.

## Rules and payouts

US rules, set up in `core/Rules.kt`:

- 2/4/6/8 decks (default 8). Fair Fisher–Yates shuffle (`kotlin.random`, seeded from `SecureRandom` per deal).
  Reshuffle once 75% of the shoe has been dealt.
- The dealer peeks for blackjack with an ace or ten up. If the dealer has blackjack, the hand ends before he
  decides anything (so doubles and splits can't be lost to it).
- Blackjack pays 3:2, rounded down to whole chips. Blackjack against dealer blackjack pushes.
- Dealer stands on soft 17 by default (H17 option).
- Double on any two cards (option: hard 9–11 only); exactly one card.
- Double after split allowed (option). Split any two ten-value cards. Split up to 3 times (4 hands; option 2 or 3).
  Split aces get one card each and can't be hit or resplit. 21 after a split pays 1:1.
- Insurance (off by default): half the bet, pays 2:1. Insurance with a blackjack works out to even money.
- Late surrender (off by default): first two cards, one hand only, after the dealer peeks; half the bet back.

### Basic strategy (hint)

`core/Strategy.kt` works out the expected value of every legal move for the rules in force. It recurses exactly
over the next cards, using odds from the shoe minus the dealer's up card and the hand's first two cards. Dealer
outcomes are conditioned on no dealer blackjack when an ace or ten shows. Splits are valued as two one-card hands,
with DAS and the split-aces rule taken into account. Insurance is never recommended.

`StrategyTest` checks the computed charts against the published 4–8-deck charts for S17/DAS, H17/DAS, no-DAS,
late surrender (S17 and H17) and double-9–11-only. All hard, soft and pair rows match. Two-deck charts match on
the key rows, and a few small-shoe borderline plays (e.g. 11 against an ace) differ.

### Levels

XP per hand: 10, plus 5 for a winning hand, plus 10 more for a blackjack.
XP needed to go from level L to L+1: **50 + L** (918 XP at his level 868, about 75 hands).
That is roughly one level per evening at his pace, so the badge keeps moving without inflating.

## Verification

- **Core tests: 54, all passing** (`./gradlew :blackjack:core:test`):
  - `GameTest` (42): payouts for every outcome (blackjack 3:2 and rounding, push, win, lose, bust, dealer bust,
    double win/lose, split with DAS, split aces, 21 after split, split hands settled separately, all split hands
    bust, insurance won/lost/even money/declined, surrender and when it's allowed); peek with ace or ten up; no
    peek with other up cards; dealer play (S17, H17, hard 17, soft-to-hard, visible steps); limits (no double or
    split without chips, max hands, no double after a hit, DAS off, 9–11 rule); shoe (reshuffle at the cut card,
    deck-count change, shuffle is a permutation, ace-of-spades position uniform over 52,000 shuffles,
    conservation); exact mid-hand save/resume through JSON.
  - `StrategyTest` (8): the computed charts against the published charts (above), plus three-card hands.
  - `Simulation` (4):
    - **Fuzzer:** 60,000 rounds across 40 random rule sets with random legal moves. Cards are conserved every
      step, chips are only moved (never created) mid-round, settlement matches the summary, payout bounds hold,
      and every round ends.
    - **House edge**, flat-bet basic strategy, 8 decks S17 DAS (published ≈0.46%):
      **0.536% over 1M hands** (test bound 0.15–0.75%) and **0.432% over 10M hands** (bound 0.35–0.60%).
    - **Same cards, different rules (5M hands):** S17 0.430%, H17 0.667%, no DAS 0.620%.
- **Release build** (R8 + shrinkResources) installed on the `dad-blackjack` emulator (1080x2340 @ 480 dpi =
  360 x 780 dp, 3-button nav). Every screen was screenshotted into `shots/` and checked for overlap, wrapping,
  clipping and off-centre glyphs: home, betting, dealing, your turn, hint, result, pair, three split hands with
  auto-highlighted hint, split result, insurance prompt, insured, surrender option, auto-deal, shuffling,
  out of chips, tables (London, Paris), settings, stats, help.
- **Animations** checked from `screenrecord` contact sheets: deal path from the shoe, flips, totals appearing
  when cards land, chips to the dealer and the balance, the sweep to the discard tray, and split stacks.
- **Kill and relaunch (release build):** force-stopped mid-hand and relaunched. The app came straight back to the
  table in the same state; a pixel diff against the screenshot before the kill differed only in the pulsing "Your turn".
- **System font scale 1.3:** home, stats, settings, tables, help and the table checked. Fixed the level badge (now
  sized in dp), the chart caption overlapping the chart, the stats tile label wrap, truncated recent-hand lines,
  and the tables card. The fixed-layout table limits font growth to 1.1x so text can't collide.

Debug builds accept `adb shell am start -n dadgames.blackjack/.MainActivity --es stack 8,6,8,10` (card values
dealt next) and `--el balance N`, for screenshots. Release builds ignore them.
