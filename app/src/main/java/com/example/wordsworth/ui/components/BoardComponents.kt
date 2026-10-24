package com.example.wordsworth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordsworth.CellValidation
import com.example.wordsworth.KeyboardLayout
import com.example.wordsworth.TallyResult
import com.example.wordsworth.WordDirection
import com.example.wordsworth.WordsWorthLogic
import com.example.wordsworth.ui.theme.WordsWorthColors
import kotlin.math.roundToInt

/**
 * Interactive NxN crossword board grid with zero-dead-zone touch handling.
 *
 * Touch coordinates are mapped directly to `(row, col)` cells across the entire container so taps
 * near cell borders never miss. Tapping a cell focuses it (or toggles Across/Down if already focused),
 * and pressing-and-holding an occupied cell clears its letter.
 */
@Composable
fun CrosswordBoardGrid(
    board: Array<Array<Char?>>,
    boardSize: Int,
    focusedCell: Pair<Int, Int>?,
    focusDirection: WordDirection,
    hoveredCell: Pair<Int, Int>?,
    tallyResult: TallyResult?,
    boardPadding: Dp,
    cellSpacing: Dp,
    onGridBoundsChanged: (Rect) -> Unit,
    onBoardCellTap: (Int, Int) -> Unit,
    onClearCell: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    pendingRomaji: String = ""
) {
    val currentBoard by rememberUpdatedState(board)
    val currentBoardSize by rememberUpdatedState(boardSize)
    val currentOnBoardCellTap by rememberUpdatedState(onBoardCellTap)
    val currentOnClearCell by rememberUpdatedState(onClearCell)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(if (isCompact) 4.dp else 6.dp, RoundedCornerShape(if (isCompact) 10.dp else 14.dp))
            .clip(RoundedCornerShape(if (isCompact) 10.dp else 14.dp))
            .background(WordsWorthColors.BoardFrameBackground)
            .padding(boardPadding)
            .onGloballyPositioned { coords ->
                onGridBoundsChanged(coords.boundsInRoot())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val size = currentBoardSize
                    val w = this.size.width.coerceAtLeast(1)
                    val h = this.size.height.coerceAtLeast(1)
                    val col = ((down.position.x / w) * size).toInt().coerceIn(0, size - 1)
                    val row = ((down.position.y / h) * size).toInt().coerceIn(0, size - 1)

                    val boardSnap = currentBoard
                    val cellChar = boardSnap.getOrNull(row)?.getOrNull(col)
                    if (!WordsWorthLogic.isBlockedCell(cellChar)) {
                        val hadLetterInitially = WordsWorthLogic.isLetterCell(cellChar)
                        // Immediately focus the tapped square (or toggle Across/Down if already focused)
                        currentOnBoardCellTap(row, col)

                        // Support press-and-hold on an existing letter square to clear it
                        var upOrCancel: PointerInputChange? = null
                        try {
                            upOrCancel = withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation()
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            if (hadLetterInitially) {
                                currentOnClearCell(row, col)
                            }
                        }

                        if (upOrCancel == null) {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        } else {
                            upOrCancel.consume()
                        }
                    } else {
                        // Consume pointer events on blacked-out squares without moving the cursor
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(cellSpacing)
        ) {
            for (r in 0 until boardSize) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                ) {
                    for (c in 0 until boardSize) {
                        val cellChar = board.getOrNull(r)?.getOrNull(c)
                        val isHovered = hoveredCell == (r to c)
                        val isFocused = focusedCell == (r to c)
                        val validation = tallyResult?.cellValidations?.get(r to c)
                            ?: CellValidation.UNCHECKED
                        val displayValue = WordsWorthLogic.cellDisplayValue(board, r, c)

                        BoardCell(
                            letter = cellChar,
                            boardSize = boardSize,
                            isFocused = isFocused,
                            focusDirection = focusDirection,
                            isHovered = isHovered,
                            validation = validation,
                            isCompact = isCompact,
                            pendingRomaji = if (isFocused) pendingRomaji else "",
                            displayValue = displayValue,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders a single square within the crossword grid.
 *
 * - Empty focused squares display a centered direction arrow (`→` for Across, `↓` for Down)
 *   scaled to match the board's letter font size.
 * - Occupied squares display the uppercase letter centered and its point value in the bottom-right corner.
 * - Validated squares are tinted green ([CellValidation.VALID]) or red ([CellValidation.INVALID]).
 */
@Composable
fun BoardCell(
    letter: Char?,
    boardSize: Int,
    isFocused: Boolean,
    focusDirection: WordDirection,
    isHovered: Boolean,
    validation: CellValidation,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    pendingRomaji: String = "",
    displayValue: Int? = null
) {
    val isBlocked = WordsWorthLogic.isBlockedCell(letter)
    val isLetter = WordsWorthLogic.isLetterCell(letter)

    val bgColor = when {
        isBlocked -> WordsWorthColors.BlockedCellBackground
        isHovered -> WordsWorthColors.HoveredCellBackground
        isFocused && letter == null -> WordsWorthColors.FocusedEmptyCellBackground
        letter == null -> WordsWorthColors.EmptyCellBackground
        validation == CellValidation.VALID -> WordsWorthColors.ValidCellBackground
        validation == CellValidation.INVALID -> WordsWorthColors.InvalidCellBackground
        else -> WordsWorthColors.UncheckedLetterCellBackground
    }

    val borderColor = when {
        isBlocked -> WordsWorthColors.BlockedCellBorder
        isHovered -> WordsWorthColors.HoveredCellBorder
        isFocused -> WordsWorthColors.FocusedCursorBlue
        validation == CellValidation.VALID -> WordsWorthColors.ValidCellBorder
        validation == CellValidation.INVALID -> WordsWorthColors.InvalidCellBorder
        isLetter -> WordsWorthColors.UncheckedLetterCellBorder
        else -> WordsWorthColors.EmptyCellBorder
    }

    val borderWidth = when {
        !isBlocked && isFocused -> when {
            isCompact && boardSize >= 12 -> 1.8.dp
            isCompact -> 2.2.dp
            boardSize >= 12 -> 2.3.dp
            else -> 3.dp
        }
        !isBlocked && (isHovered || validation != CellValidation.UNCHECKED) -> if (isCompact) 1.4.dp else 1.8.dp
        else -> 1.dp
    }

    val cornerRadius = when {
        boardSize >= 12 -> if (isCompact) 3.dp else 4.dp
        boardSize >= 9 -> if (isCompact) 4.dp else 5.dp
        else -> if (isCompact) 5.dp else 7.dp
    }

    val letterFontSize = if (isCompact) {
        when {
            boardSize >= 12 -> 10.sp
            boardSize >= 9 -> 14.sp
            else -> 19.sp
        }
    } else {
        when {
            boardSize >= 12 -> 13.sp
            boardSize >= 9 -> 17.sp
            else -> 24.sp
        }
    }

    val valueFontSize = if (isCompact) {
        when {
            boardSize >= 12 -> 5.5.sp
            boardSize >= 9 -> 6.5.sp
            else -> 8.sp
        }
    } else {
        when {
            boardSize >= 12 -> 7.sp
            boardSize >= 9 -> 8.sp
            else -> 10.sp
        }
    }

    val innerPadding = when {
        isCompact && boardSize >= 9 -> 0.5.dp
        boardSize >= 12 -> 1.dp
        else -> 2.dp
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .padding(innerPadding)
    ) {
        // Show pending Romaji (if typing) or centered direction arrow inside an empty focused square
        if (isFocused && pendingRomaji.isNotEmpty()) {
            Text(
                text = pendingRomaji.uppercase(),
                fontSize = (letterFontSize.value * 0.85f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = WordsWorthColors.FocusedCursorBlue,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (isFocused && !isBlocked && !isLetter) {
            Text(
                text = if (focusDirection == WordDirection.HORIZONTAL) "→" else "↓",
                fontSize = letterFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = WordsWorthColors.FocusedCursorBlue,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (isLetter && letter != null && !(isFocused && pendingRomaji.isNotEmpty())) {
            val textColor = when (validation) {
                CellValidation.VALID -> WordsWorthColors.ValidWordText
                CellValidation.INVALID -> WordsWorthColors.InvalidWordText
                CellValidation.UNCHECKED -> WordsWorthColors.TilePrimaryText
            }
            Text(
                text = letter.toString(),
                fontSize = letterFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
            val pointVal = displayValue ?: WordsWorthLogic.letterValue(letter)
            Text(
                text = pointVal.toString(),
                fontSize = valueFontSize,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isCompact) 0.5.dp else 1.dp)
            )
        }
    }
}

/**
 * On-screen keyboard of letter tiles (`A`–`Z`) and the `CLR` eraser key.
 *
 * Supports both instant tap-to-place (populating the focused cell on touch-down and advancing
 * cursor focus) and drag-and-drop onto any square on the board.
 */
@Composable
fun TileKeyboardRack(
    keyboardLayout: KeyboardLayout,
    selectedLetter: Char?,
    isBoardLocked: Boolean,
    dragThresholdPx: Float,
    onKeyboardOriginChanged: (Offset) -> Unit,
    onKeyboardInput: (Char?) -> Pair<Pair<Int, Int>, Char?>?,
    onDragStart: (Char, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: (Char, Offset, Pair<Pair<Int, Int>, Char?>?) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onKeyAction: ((String) -> Unit)? = null
) {
    val keyboardRowsList = WordsWorthLogic.keyboardRows(keyboardLayout)
    val currentKeyboardRows by rememberUpdatedState(keyboardRowsList)
    val currentIsBoardLocked by rememberUpdatedState(isBoardLocked)
    val currentOnKeyboardInput by rememberUpdatedState(onKeyboardInput)
    val currentOnKeyAction by rememberUpdatedState(onKeyAction)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val outerPadding = if (isCompact) 4.dp else 6.dp
    val keySpacing = if (isCompact) 3.dp else 4.dp

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isCompact) 10.dp else 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(outerPadding)
                .onGloballyPositioned { coords ->
                    onKeyboardOriginChanged(coords.boundsInRoot().topLeft)
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        val rows = currentKeyboardRows
                        val w = this.size.width.coerceAtLeast(1)
                        val h = this.size.height.coerceAtLeast(1)
                        val row = ((down.position.y / h) * rows.size).toInt()
                            .coerceIn(0, rows.size - 1)
                        val rowKeys = rows[row]
                        val totalWeight = rowKeys.sumOf {
                            if (it == WordsWorthLogic.KEY_CLR || it == WordsWorthLogic.KEY_MODIFIER) 1.25 else 1.0
                        }
                        val targetWeight = (down.position.x / w).coerceIn(0f, 1f) * totalWeight
                        var accumulated = 0.0
                        var touchedKey: String = rowKeys.last()
                        for (k in rowKeys) {
                            val keyWeight = if (k == WordsWorthLogic.KEY_CLR || k == WordsWorthLogic.KEY_MODIFIER) 1.25 else 1.0
                            accumulated += keyWeight
                            if (targetWeight <= accumulated) {
                                touchedKey = k
                                break
                            }
                        }

                        if (currentOnKeyAction != null) {
                            currentOnKeyAction?.invoke(touchedKey)
                        } else {
                            if (touchedKey == WordsWorthLogic.KEY_CLR) {
                                currentOnKeyboardInput(null)
                            } else if (touchedKey.length == 1) {
                                currentOnKeyboardInput(touchedKey[0])
                            }
                        }

                        val touchedChar: Char? = if (touchedKey.length == 1) touchedKey[0] else null

                        var isDragging = false
                        var lastPos = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (change != null) {
                                change.consume()
                                lastPos = change.position
                                if (!currentIsBoardLocked &&
                                    touchedChar != null &&
                                    !isDragging &&
                                    (lastPos - down.position).getDistance() > dragThresholdPx
                                ) {
                                    isDragging = true
                                    currentOnDragStart(touchedChar, lastPos)
                                } else if (isDragging) {
                                    currentOnDragMove(lastPos)
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDragging && touchedChar != null) {
                            currentOnDragEnd(touchedChar, lastPos, null)
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(keySpacing)
        ) {
            for (rowKeys in keyboardRowsList) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keySpacing)
                ) {
                    for (key in rowKeys) {
                        when (key) {
                            WordsWorthLogic.KEY_CLR -> {
                                EraserRackTile(
                                    isSelected = selectedLetter == null,
                                    isCompact = isCompact,
                                    modifier = Modifier.weight(1.25f)
                                )
                            }
                            WordsWorthLogic.KEY_MODIFIER, "小", "゛゜" -> {
                                ModifierRackTile(
                                    isCompact = isCompact,
                                    modifier = Modifier.weight(1.25f)
                                )
                            }
                            else -> {
                                val isSelected = selectedLetter?.toString().equals(key, ignoreCase = true)
                                KeyRackTile(
                                    key = key,
                                    isSelected = isSelected,
                                    isCompact = isCompact,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Unified Japanese modifier tile cycling between small (小), voiced (゛), and semi-voiced (゜) variants.
 */
@Composable
fun ModifierRackTile(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val tileHeight = if (isCompact) 38.dp else 46.dp
    val labelFontSize = if (isCompact) 11.sp else 13.sp

    Box(
        modifier = modifier
            .height(tileHeight)
            .shadow(1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp))
            .padding(horizontal = 1.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "小゛゜",
            fontSize = labelFontSize,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Japanese modifier tile backward compatibility wrapper.
 */
@Composable
fun SpecialModifierRackTile(
    label: String,
    subtext: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    ModifierRackTile(
        modifier = modifier,
        isCompact = isCompact
    )
}

/**
 * Individual keyboard key tile displaying its label.
 */
@Composable
fun KeyRackTile(
    key: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val bgColor = if (isSelected) WordsWorthColors.TileSelectedBackground else WordsWorthColors.TileDefaultBackground
    val borderColor = if (isSelected) WordsWorthColors.TileSelectedBorder else WordsWorthColors.TileDefaultBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val tileHeight = if (isCompact) 38.dp else 46.dp
    val letterFontSize = if (isCompact) 15.sp else 17.sp

    Box(
        modifier = modifier
            .height(tileHeight)
            .shadow(if (isSelected) 3.dp else 1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key,
            fontSize = letterFontSize,
            fontWeight = FontWeight.ExtraBold,
            color = WordsWorthColors.TilePrimaryText,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Individual letter tile in the on-screen keyboard displaying the uppercase letter
 * and its point value in the bottom-right corner.
 */
@Composable
fun LetterRackTile(
    letter: Char,
    value: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val bgColor = if (isSelected) WordsWorthColors.TileSelectedBackground else WordsWorthColors.TileDefaultBackground
    val borderColor = if (isSelected) WordsWorthColors.TileSelectedBorder else WordsWorthColors.TileDefaultBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val tileHeight = if (isCompact) 38.dp else 46.dp
    val letterFontSize = if (isCompact) 16.sp else 18.sp
    val valueFontSize = if (isCompact) 8.sp else 9.sp

    Box(
        modifier = modifier
            .height(tileHeight)
            .shadow(if (isSelected) 3.dp else 1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = if (isCompact) 2.dp else 4.dp, vertical = if (isCompact) 1.dp else 2.dp)
    ) {
        Text(
            text = letter.toString(),
            fontSize = letterFontSize,
            fontWeight = FontWeight.ExtraBold,
            color = WordsWorthColors.TilePrimaryText,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-2).dp)
        )
        Text(
            text = value.toString(),
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = WordsWorthColors.TileSubscriptText,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

/**
 * Backspace / Clear (`⌫ CLR`) tile located in the bottom-right corner of the on-screen keyboard.
 */
@Composable
fun EraserRackTile(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val bgColor = if (isSelected) WordsWorthColors.EraserSelectedBackground else WordsWorthColors.EraserDefaultBackground
    val borderColor = if (isSelected) WordsWorthColors.InvalidCellBorder else WordsWorthColors.TileDefaultBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val tileHeight = if (isCompact) 38.dp else 46.dp

    Box(
        modifier = modifier
            .height(tileHeight)
            .shadow(if (isSelected) 3.dp else 1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⌫",
                fontSize = if (isCompact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = WordsWorthColors.InvalidWordText
            )
            Text(
                text = "CLR",
                fontSize = if (isCompact) 7.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                color = WordsWorthColors.InvalidWordText
            )
        }
    }
}

/**
 * Floating tile preview rendered slightly above the user's finger while dragging a letter
 * from the keyboard onto the crossword board.
 */
@Composable
fun FloatingDraggedTile(
    letter: Char,
    dragPositionInRoot: Offset,
    tileSizePx: Float
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (dragPositionInRoot.x - tileSizePx / 2f).roundToInt(),
                    y = (dragPositionInRoot.y - tileSizePx * 1.2f).roundToInt()
                )
            }
            .size(50.dp)
            .shadow(10.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(WordsWorthColors.FloatingTileBackground)
            .border(2.dp, WordsWorthColors.TileSelectedBorder, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Text(
            text = letter.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WordsWorthColors.TilePrimaryText,
            modifier = Modifier.align(Alignment.Center)
        )
        Text(
            text = WordsWorthLogic.letterValue(letter).toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WordsWorthColors.TileSubscriptText,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
