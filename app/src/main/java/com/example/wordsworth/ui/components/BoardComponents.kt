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
    modifier: Modifier = Modifier
) {
    val currentBoard by rememberUpdatedState(board)
    val currentBoardSize by rememberUpdatedState(boardSize)
    val currentOnBoardCellTap by rememberUpdatedState(onBoardCellTap)
    val currentOnClearCell by rememberUpdatedState(onClearCell)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
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

                        BoardCell(
                            letter = cellChar,
                            boardSize = boardSize,
                            isFocused = isFocused,
                            focusDirection = focusDirection,
                            isHovered = isHovered,
                            validation = validation,
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
    modifier: Modifier = Modifier
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
        !isBlocked && isFocused -> if (boardSize >= 12) 2.3.dp else 3.dp
        !isBlocked && (isHovered || validation != CellValidation.UNCHECKED) -> 1.8.dp
        else -> 1.dp
    }

    val cornerRadius = when {
        boardSize >= 12 -> 4.dp
        boardSize >= 9 -> 5.dp
        else -> 7.dp
    }

    val letterFontSize = when {
        boardSize >= 12 -> 13.sp
        boardSize >= 9 -> 17.sp
        else -> 24.sp
    }

    val valueFontSize = when {
        boardSize >= 12 -> 7.sp
        boardSize >= 9 -> 8.sp
        else -> 10.sp
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .padding(if (boardSize >= 12) 1.dp else 2.dp)
    ) {
        // Show centered direction arrow inside an empty focused square
        if (isFocused && !isBlocked && !isLetter) {
            Text(
                text = if (focusDirection == WordDirection.HORIZONTAL) "→" else "↓",
                fontSize = letterFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = WordsWorthColors.FocusedCursorBlue,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (isLetter && letter != null) {
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
            Text(
                text = WordsWorthLogic.letterValue(letter).toString(),
                fontSize = valueFontSize,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp)
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
    modifier: Modifier = Modifier
) {
    val keyboardRowsList = WordsWorthLogic.keyboardRows(keyboardLayout)
    val currentKeyboardRows by rememberUpdatedState(keyboardRowsList)
    val currentIsBoardLocked by rememberUpdatedState(isBoardLocked)
    val currentOnKeyboardInput by rememberUpdatedState(onKeyboardInput)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
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
                        val col = ((down.position.x / w) * rowKeys.size).toInt()
                            .coerceIn(0, rowKeys.size - 1)
                        val touchedLetter: Char? = rowKeys[col]

                        // Immediately populate the focused square and advance focus
                        val undoPlacement = currentOnKeyboardInput(touchedLetter)

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
                                    touchedLetter != null &&
                                    !isDragging &&
                                    (lastPos - down.position).getDistance() > dragThresholdPx
                                ) {
                                    isDragging = true
                                    currentOnDragStart(touchedLetter, lastPos)
                                } else if (isDragging) {
                                    currentOnDragMove(lastPos)
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDragging && touchedLetter != null) {
                            currentOnDragEnd(touchedLetter, lastPos, undoPlacement)
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (rowKeys in keyboardRowsList) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (letter in rowKeys) {
                        if (letter != null) {
                            val value = WordsWorthLogic.letterValue(letter)
                            val isSelected = selectedLetter == letter
                            LetterRackTile(
                                letter = letter,
                                value = value,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            EraserRackTile(
                                isSelected = selectedLetter == null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) WordsWorthColors.TileSelectedBackground else WordsWorthColors.TileDefaultBackground
    val borderColor = if (isSelected) WordsWorthColors.TileSelectedBorder else WordsWorthColors.TileDefaultBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .height(46.dp)
            .shadow(if (isSelected) 3.dp else 1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = letter.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WordsWorthColors.TilePrimaryText,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-2).dp)
        )
        Text(
            text = value.toString(),
            fontSize = 9.sp,
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
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) WordsWorthColors.EraserSelectedBackground else WordsWorthColors.EraserDefaultBackground
    val borderColor = if (isSelected) WordsWorthColors.InvalidCellBorder else WordsWorthColors.TileDefaultBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .height(46.dp)
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WordsWorthColors.InvalidWordText
            )
            Text(
                text = "CLR",
                fontSize = 8.sp,
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
