# Woodoku (com.tripledot.woodoku v2.11.02) — 150 h/year, his #1 game

Screens: `shots/w01`–`w08`.

## Gameplay (Classic, what he plays)
- 9x9 board, the nine 3x3 boxes shaded in alternating dark/light wood (checkerboard of boxes).
- Tray of **3 pieces** under the board; drag a piece onto the board. New set of 3 only after all 3 are placed.
- Clear any full **row, column, or 3x3 box**; cleared cells empty. Multiple simultaneous clears = combo.
- Game over when none of the remaining tray pieces fits anywhere.
- Scoring seen: +1 per block placed (2x2 square gave 4). Clears give bonus points with combos and
  consecutive-clear streaks (look up exact published Woodoku scoring; match it closely).
- Drag feel: piece scales up to board-cell size and floats **above the finger** (so the finger doesn't
  hide it); a translucent **shadow preview** shows the landing cells; drop snaps; invalid drop returns to tray.
- Top bar: back arrow, current score, "TODAY <best>" with a crown, settings gear.

## Meta
- Home: Daily Challenge card with milestones (1 / 3 / 10 / 24 wins per month) + PLAY; NEW GAME.
- Bottom tabs: Home, Classic, Daily, Journey.
- Classic tab: **My High Scores — Today / This week / This month / This year, Best Ever** (with crown).
- Daily tab: month calendar, today highlighted, play past days, monthly milestone trophies.
  (He has done 0 dailies this month — keep it but secondary.)
- Journey: map of levels (locked behind update on his phone). Not needed.
- Settings: Sounds (he has OFF), Language, Tutorials (VIEW), Help center.

## Monetisation to strip
- Banner ad across the top during play. Gems/diamond currency.

## His numbers
Best ever 2447; this year 2158; month 1380; week 1380; today 750.

## Exact rules (decompiled from his APK v2.11.02; bundled config defaults, remote config may override)
- **Scoring (matches ours):** per move `cells + 18·C + 10·max(0, C−1) + 10·S`, C = regions cleared by the move
  (rows, columns, boxes all equal), S = consecutive clearing moves *before* this one (a clearing move adds 1
  afterwards; a non-clearing move resets to 0). Multiplier 1.0 in Classic. Streak isn't saved (resets on resume).
- **Pieces:** 17 base shapes A–Q with weights per stage; stage chosen by trays dealt this game
  (stage starts at trays 0,1,2,3,6,7,9,10,12,15,19,24). Each draw: weighted pick, 50% mirror, then rotate
  0/90/180° CCW (never 270). Tray of 3; a duplicate shape is redrawn once. New tray only when all 3 placed.
  **one_valid_move_rule:** after dealing, if none of the 3 fits, one random piece becomes a single block.
  No other board awareness. Full table: `woodoku-pieces.txt`; config: `woodoku-config-defaults.json`.
- **Daily:** id (year−2000)·10000 + month·100 + day seeds the day; gem type = month; goal 10 gems;
  6 symmetric pre-placed gem blocks; same piece table. (Inferred: 87.5% of new pieces carry a gem.)
