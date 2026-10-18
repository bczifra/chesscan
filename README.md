# ChessScan

Photo of a chess scoresheet -> ML Kit OCR -> auto-correct to legal moves -> manual review -> PNG (board + move list) via the share sheet.

## Build
1. Open this folder in Android Studio (Koala or newer). Let it sync and generate the Gradle wrapper.
2. Run on a device or emulator (minSdk 24).

## Notes
- OCR: ML Kit on-device (Latin). It is built for printed text, so handwriting will need the review step.
- Auto-correct: each OCR token is snapped to the closest *legal* move in the current position
  (OCR-confusable characters like 1/l, 5/S, 0/O cost less). Ambiguous or far-off tokens stop the
  process, and the rest is left raw for you to fix.
- Two-column scoresheets (moves 1-30 beside 31-60) can come out interleaved; reorder in the editor.
- If chess glyphs render badly, bundle a chess font in res/font and set `BoardRenderer.pieceTypeface`.
