# WordsWorth / ますます

An Android crossword-style word puzzle game built with Jetpack Compose. Players populate a crossword grid with intersecting horizontal (Across) and vertical (Down) words to maximize their score.

WordsWorth supports both **English** (**WordsWorth**) and **Japanese** (**ますます**) editions with instant switching, dynamic launcher icons and app titles, independent board state preservation, multiple grid sizes, custom board patterns, letter sprinkle bonuses, Time Attack challenges, and flexible scoring rules.

---

## Features & Highlights

### 🌐 Bilingual Support & Dynamic Launcher Branding
- **Top Bar Switcher**: Tap the top header badge (`En` for English, `日` for Japanese) to switch instantly between language editions.
- **Dynamic Launcher Icon & App Title**:
  - **English Edition**: Displays the **WORD** wooden tile icon and the title **WordsWorth**.
  - **Japanese Edition**: Displays the **ます** wooden tile icon and the title **ますます**.
  - *Development*: Seamless in-app icon and title toggling via Android `<activity-alias>` (`MainActivityAliasEn` and `MainActivityAliasJa`).
  - *Production*: Transitioning to system locale qualifiers (`res/mipmap-ja-...` and `res/values-ja/`) and localized Google Play Store listings for shipping.
- **Independent Session Preservation**: Switching languages preserves each language's active board, timer, cursor position, and word validation history without data loss.
- **English Mode**:
  - Full Latin alphabet (`A`–`Z`) with infinite tiles.
  - Standard Scrabble letter point distributions (1 to 10 points per letter).
  - 152,000+ English word dictionary (`words.txt`).
- **Japanese Mode (ますます)**:
  - Hiragana syllabary with Romaji-to-Hiragana input streaming.
  - 164,000+ Japanese word dictionary (`words_ja.txt`).
  - **Kana Point Values**: Point system tailored to Japanese frequency (from 0 to 10 points).
  - **Caboose Rule**: Terminal `-う` column vowels (う, く, す, つ, ぬ, ふ, む, ゆ, る) and terminal `て`/`で` score only **1 point** when appearing at the end of a word, but score their full point values when appearing elsewhere.
  - **Sokuon & Small Kana**: `ゃ`, `ゅ`, `ょ`, `っ`, and the prolonged sound mark `ー` score **0 points** (serving as structural bridge tiles).
  - **Dedicated `ー` Key**: Prolonged sound mark key on row 2, with automatic Romaji-to-Hiragana composition.

### 🌟 Letter Sprinkle Feature (Bonus Tiles)
- Configure pre-placed seed letters on new and cleared boards: **None**, **Some** (3–6 tiles), or **Lots** (8–14 tiles).
- **Double Points**: Words utilizing sprinkled letters award **2× double points** for each sprinkled tile used.
- **Visual Indicator**: Sprinkled tiles display a superscripted asterisk (e.g. **W\***, **ま\***) anchored to the upper-right shoulder while keeping the base letter perfectly centered.
- **Locked Tiles**: Sprinkled tiles cannot be cleared or overwritten mid-game.

### 📐 Multiple Board Sizes & Patterns
- **Grid Sizes**: Choose between **6×6**, **9×9**, and **12×12** boards.
- **Board Patterns**:
  - **All-blank**: Crossword grid where every square is available.
  - **Random pattern**: Generates a crossword board with blacked-out blocked squares (`#`).

### ⏱ Game Modes & Timers
- **Standard Mode**: Free play with an automatic elapsed timer counting up from `0:00`.
- **Time Attack**: Choose between **1 min**, **3 mins**, and **5 mins** timed challenges.
  - Real-time countdown timer turns warning red when $\le$ 10 seconds remain.
  - Board automatically locks when time expires, tallying final score.

### 🎯 Settings & Customization
Organized into dedicated settings categories:
- **Game & Rules**:
  - *Japanese Verb Rules*: Toggle between Standard and Caboose scoring modes.
  - *Time Attack Duration*: Quick adjustments for timed play.
- **Scoring**:
  - *Duplicate Word Points*: Toggle whether placing the same valid word more than once awards points each time or only once per board.
  - *Word Whitelist*: Long-press any unrecognized word in the validation list to add it to a persistent custom whitelist.
- **Board & Tiles**:
  - *Keyboard Layout*: Switch between **Alphabetical** and **QWERTY** on-screen keyboard racks.
  - *Letter Sprinkle Mode*: Set starting seed tile density (**None**, **Some**, **Lots**).
  - *Haptic Feedback*: Toggle subtle tactile vibration on tile placement, deletion, and validation.
- **Data & History**:
  - Clear board, load example puzzle boards, and clear saved game history.

### 🎮 Responsive Touch & Keyboard Controls
- **Tap to Focus / Toggle Direction**: Tap an empty square to focus; tap the focused square to toggle typing direction (Across $\leftrightarrow$ Down).
- **Long-Press to Clear**: Long-press any placed tile on the board to clear it.
- **Drag-and-Drop Tile Placement**: Drag a letter tile from the keyboard rack directly onto any board cell with a floating preview tile.
- **Hardware & Bluetooth Keyboard**: Full physical keyboard support (letters, arrow keys, Backspace, Space to toggle direction, Enter to validate).

### 💾 Saved Games & History
- Mini screenshots of completed boards are captured and saved with final scores and elapsed times.
- Tap any saved game thumbnail to view an enlarged preview dialog.
- Long-press any saved game entry to delete it with confirmation.

---

## Point Systems

### English Letter Values
| Points | Letters |
| :--- | :--- |
| **1 pt** | A, E, I, N, O, R, S, T, Y |
| **2 pts** | D, G, L, U |
| **3 pts** | B, C, M, P |
| **4 pts** | F, H, W |
| **5 pts** | K, V |
| **8 pts** | J, X |
| **10 pts** | Q, Z |

### Japanese Kana Values (ますます)
| Points | Kana Characters |
| :--- | :--- |
| **0 pts** | ゃ, ゅ, ょ, っ, ー *(Structural Bridge Tiles)* |
| **1 pt** | の, い, た, に, て, と, し, は, な, を, か *(and Caboose endings)* |
| **2 pts** | ら, れ, ま, そ, り, こ, ん, あ |
| **3 pts** | す, お, き, け, つ, ち |
| **4 pts** | ほ, わ, よ, め, み, う |
| **5 pts** | や, さ, ひ, せ, る, く |
| **6 pts** | ゆ, も |
| **7 pts** | ね, む |
| **8 pts** | ろ, ふ |
| **9 pts** | へ, え |
| **10 pts** | ぬ |

*Note: Dakuten and handakuten kana inherit base kana scoring (e.g., が scores the same base points as か, ぱ as は), with Caboose rules applying to terminal voiced endings.*

---

## Architecture & Code Structure

```
app/src/main/java/com/example/wordsworth/
├── MainActivity.kt               # Main entry point, hardware keyboard dispatch & alias targets
├── WordsWorthGameState.kt        # ViewModel managing dual-language board, timers, sprinkles & settings
├── WordsWorthLogic.kt            # Core engine: scoring, dictionary lookup, Caboose rule & launcher icon updates
├── KanaConverter.kt              # Romaji-to-Hiragana converter & kana scoring tables
├── SavedGamesManager.kt          # Disk persistence & screenshot generator for saved games
└── ui/
    ├── screens/
    │   ├── WordsWorthGameScreen.kt   # Adaptive layouts (Phone/Tablet, Portrait/Landscape)
    │   └── SettingsScreen.kt          # Categorized settings: Game, Scoring, Board & Tiles, Data
    ├── components/
    │   ├── BoardComponents.kt        # CrosswordBoardGrid, BoardCell, sprinkled tiles, TileKeyboardRack
    │   ├── SidebarComponents.kt      # Score cards, timers, word lists, saved game thumbnails
    │   └── GameDialogs.kt            # Time Attack, whitelist, quit, preview, and delete dialogs
    └── theme/
        └── Theme.kt                  # Material3 theme setup, color tokens & typography
```

---

## Building & Testing

### Prerequisites
- Android Studio Ladybug / Meerkat or Android SDK (API 34+)
- JDK 17+

### Run Unit Tests
```bash
./gradlew test
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Install on Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
