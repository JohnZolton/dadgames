# Solitaire (com.dna.solitaireapp v28.0.4) — 79 h/year

Screens: `shots/s01`–`s09`.

## Gameplay
- Klondike. He uses **Draw 1** (Draw 3 available).
- Layout: 4 foundations (empty slots show a big "A") top-left; waste then **stock at top-right**; 7 tableau
  columns below. Cards are **very large**: rank+suit index takes most of the visible strip.
- **Tap a card to auto-move it** to its best legal destination (tested: tap 7♠ moved it onto 8♥).
  Drag also presumably supported.
- Header: **Time / Score / Moves**. Timer starts on first move.
- Bottom bar (icon + label): **Undo, Settings, Challenges, Hint, New Game**.
- Scoring matches Windows Klondike standard scoring with time bonus: +5 waste→tableau, +10 to foundation,
  +5 turn over tableau card, −15 foundation→tableau, time penalty −2 per 10 s, time bonus 700000/seconds
  on win (his ~2:00 wins score ~5200–5400). Verify and use that.

## Settings
Sounds (on), Remove Ads, Gameplay Animations (on), Win Animations (on), Draw Mode 1/3 (1),
Background (green felt), Card Front, Card Back (blue dots), Statistics, Share, Contact, Privacy, Terms.

## Statistics page (carry his numbers over)
Games won 1069 · Games played 3647 · Best score 5371 · Least moves 90 · Best time 1:28 ·
Total time 9 d 13:14:57. "Scoring" sub-page = **top-10 table: Score, Time, Date, Moves** (see README).
Reset.

## Challenges
Daily deal calendar, monthly trophies at 10/20/31 solved. He has 0/31 — secondary feature.

## Monetisation to strip
Banner ads on every screen including a sweepstakes casino ad; "Remove Ads" toggle.

## Exact scoring (decompiled from his APK v28.0.4; reproduces all 10 of his top scores)
Recovered with Il2CppDumper + capstone (ScoreManager.CountScore / CalculateGameCompleteScore / OnDeckPass).
- Waste → tableau **+45**; waste → foundation **+105**; tableau → foundation **+60**; foundation → tableau **−75**;
  tableau → tableau, drawing, foundation → foundation: 0.
- Card turned face up: **+25**.
- Undo: reverses the move's points exactly, then **−25** more. Undoing a deck pass refunds the pass penalty
  but still costs 25. (Undoing an automatic reveal probably takes back its +25 with no extra −25; medium confidence.)
- Deck pass (waste recycled to stock): Draw 1 **−200 every pass**; Draw 3 first 3 passes free, then −200 each.
- Score never goes below 0 (each deduction clamped). **No time penalty during play.**
- Win: `bonus = (20 * score) / max(floor(seconds), 1)` (integer division), final = score + bonus. No cap.
- Max base score = 24×105 + 28×60 + 21×25 = 4725. His usual 5195–5305 wins are 4725 − 200 (one recycle) plus bonus.
Check: draw + waste→tableau = 45 at 0:22 (matches s09).
