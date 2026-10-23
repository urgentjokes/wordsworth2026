# WordsWorth

An Android crossword-style word puzzle game played on a 6×6 matrix of squares.

## Gameplay & Features
- **6×6 Board**: Populate the board with intersecting horizontal (Across) and vertical (Down) words of 2 to 6 letters.
- **Infinite Alphabet Tiles**: All 26 letters (`A`–`Z`) are available in infinite instances with the following point values:
  - `1 pt`: A, E, I, N, O, R, S, T, Y
  - `2 pts`: D, G, L, U
  - `3 pts`: B, C, M, P
  - `4 pts`: F, H, W
  - `5 pts`: K, V
  - `8 pts`: J, X
  - `10 pts`: Q, Z
- **Timer (`0:00`)**: Starts counting up automatically from `0:00` as soon as the first letter is placed on the board.
- **Left-Hand Saved Games Column**: Displays a running list of mini screenshots of saved 6×6 game boards with the final score shown right next to each thumbnail (persisted across app restarts). Tap any thumbnail to inspect a larger preview.
- **Quit Options**:
  - **Quit without saving**: Resets the active board, score, and timer (`0:00`).
  - **Save final board and score**: Captures a mini screenshot of the final board and its tallied score, adds it to the left-hand column, and resets the board and timer (`0:00`) for a new round.
- **Settings Screen**: Accessible via the gear icon in the top-right corner.

## Build & Deploy
```bash
deploy-app WordsWorth
```
Or build directly with Gradle:
```bash
cd /root/projects/WordsWorth
./gradlew assembleDebug
```
# wordsworth2026
