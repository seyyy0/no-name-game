import androidx.compose.runtime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

// Simple enum functionality for turn handling
//   @Serializable is used for persistence, using kotlin.serializable
@Serializable
enum class Player { ONE, TWO }
fun Player.displayName() = if (this == Player.ONE) "1" else "2"
fun Player.next() = if (this == Player.ONE) Player.TWO else Player.ONE
fun Player.toInt() = if (this == Player.ONE) 1 else 2

// Eases return handling from dropPiece()
data class MoveResult(val newBoard: List<List<Int>>, val landingRow: Int)

// At first, I had started by resetting everything manually,
//   which was very annoying, given it was too easy to forget something.
// GameState fixes that, by giving me a nice package of everything I need to worry about.
// Also, the board is represented by a 2D List of integers.
//   Inside the program, its obvious the board is either filled by 0s (default), 1s or 2s.
//   This data structuring allows for easy checking of win condition and reminds a lot of leetcode-y problems.
//   @Serializable is used for persistence, using kotlin.serializable
@Serializable
data class GameState(
    val board: List<List<Int>>,
    val currentPlayer: Player,
    val winner: Player?,
    val isDraw: Boolean,
    val winCondition: Int,
    val rows: Int,
    val cols: Int
) {
    val isOver get() = winner != null || isDraw

    companion object {
        fun initial(rows: Int = 6, cols: Int = 7, winCondition: Int = 4) = GameState(
            board = List(rows) { List(cols) { 0 } },
            currentPlayer = Player.ONE,
            winner = null,
            isDraw = false,
            winCondition = winCondition,
            rows = rows,
            cols = cols
        )
    }
}

// Simple functions for saving and loading game state from browsers localStorage, again, using kotlin.serializable
fun saveGame(game: GameState) {
    kotlinx.browser.window.localStorage.setItem(
        "connectFourGame",
        Json.encodeToString(game)
    )
}
fun loadGame(): GameState? = try {
    val raw = kotlinx.browser.window.localStorage.getItem("connectFourGame")
        ?: return null
    Json.decodeFromString<GameState>(raw)
} catch (e: Exception) {
    null  // If corrupted for some reason, just refresh
}

// Main gameplay logic function
// We work from the last row, looking for a 0 (i.e. empty) cell,
//   and insert into it.
// Return is, as mentioned, a data class which contains a new board and
//   targeted row.
fun dropPiece(board: List<List<Int>>, col: Int, player: Player): MoveResult? {
    val targetRow = (board.size - 1 downTo 0).firstOrNull { board[it][col] == 0 }
        ?: return null
    val newBoard = board.mapIndexed { r, row ->
        if (r == targetRow) row.toMutableList().also { it[col] = player.toInt() }
        else row
    }
    return MoveResult(newBoard, targetRow)
}

// Basic check if win condition is met.
//   The algorithm is fairly basic, and makes use of pre-baked axes'.
fun checkWin(board: List<List<Int>>, row: Int, col: Int, player: Player, winCondition: Int): Boolean {
    val axes = listOf(
        Pair(0, 1),
        Pair(1, 1),
        Pair(1, 0),
        Pair(1, -1)
    )
    return axes.any { (dr, dc) ->
        val count = 1 +
                countInDir(board, row, col, dr, dc, player) +
                countInDir(board, row, col, -dr, -dc, player)
        count >= winCondition
    }
}

// Basic check if last (i.e. first/i=0) row is filled
fun checkDraw(board: List<List<Int>>) = board[0].none { it == 0 }

// Helper function for checkWin()
private fun countInDir(board: List<List<Int>>, row: Int, col: Int, dr: Int, dc: Int, player: Player): Int {
    val p = player.toInt()
    var r = row + dr
    var c = col + dc
    var count = 0
    while (r in board.indices && c in board[0].indices && board[r][c] == p) {
        count++; r += dr; c += dc
    }
    return count
}

// Helper function, to ease and prettify `onClick` handling.
//   Before, there were a bunch of scattered if branches and random booleans.
//   Now, everything is packaged nicely inside GameState so nothing gets forgotten
// Does basic checks of game state and behaves accordingly
//   We check if game is over, or play a move.
fun onColumnClick(game: GameState, col: Int): GameState {
    if (game.isOver) return game
    val result = dropPiece(game.board, col, game.currentPlayer) ?: return game
    val won = checkWin(result.newBoard, result.landingRow, col, game.currentPlayer, game.winCondition)
    return when {
        // .copy() is basically the equivalent of `...props` in JS, and creates
        //   a new GameState, where only specified fields are edited
        won -> game.copy(board = result.newBoard, winner = game.currentPlayer)
        checkDraw(result.newBoard) -> game.copy(board = result.newBoard, isDraw = true)
        else -> game.copy(
            board = result.newBoard,
            currentPlayer = game.currentPlayer.next()
        )
    }
}

// My own styling for the UI
val COLOR_GRANITE = Color("#3C493F")
val COLOR_GREY_OLIVE = Color("#7E8D85")
val COLOR_ASH_GREY = Color("#B3BFB8")
val COLOR_MINT_CREAM = Color("#F0F7F4")
val COLOR_CELADON = Color("#A2E3C4")

fun StyleScope.playerColors(player: Player) {
    if (player == Player.ONE) {
        backgroundColor(COLOR_CELADON); color(COLOR_GRANITE)
    } else {
        backgroundColor(COLOR_GREY_OLIVE); color(COLOR_MINT_CREAM)
    }
}

fun main() {
    renderComposable(rootElementId = "root") { Body() }
}


@Composable
fun Body() {
    // game is either in initial state, or loaded from localStorage
    var game by remember { mutableStateOf(loadGame() ?: GameState.initial()) }
    var inputRows by remember { mutableStateOf("") }
    var inputCols by remember { mutableStateOf("") }
    var inputWinCondition by remember { mutableStateOf("") }
    var configError by remember { mutableStateOf<String?>(null) }

    Div(attrs = {
        style {
            backgroundColor(COLOR_MINT_CREAM)
            width(100.percent)
            minHeight(100.vh)
            // Helvetica is a no-brainer ;)
            property("font-family", "Helvetica, Arial, sans-serif")
            fontSize(20.px)
            paddingTop(20.px)
        }
    }) {
        ConfigPanel(
            inputRows = inputRows,
            inputCols = inputCols,
            inputWinCondition = inputWinCondition,
            configError = configError,
            onRowsChange = { inputRows = it },
            onColsChange = { inputCols = it },
            onWinConditionChange = { inputWinCondition = it },
            onUpdate = {
                val r = inputRows.toIntOrNull()
                val c = inputCols.toIntOrNull()
                val w = inputWinCondition.toIntOrNull() ?: 4
                when {
                    r == null || c == null || r < 2 || c < 2 ->
                        configError = "Rows and cols must be numbers >= 2"
                    w < 2 || w > 10 ->
                        configError = "Win condition must be between 2 and 10"
                    w > r && w > c ->
                        configError = "Win condition is larger than both dimensions"
                    else -> {
                        configError = null

                        game = GameState.initial(r, c, w)
                        saveGame(game)
                    }
                }
            }
        )

        StatusBanner(game)

        DropButtons(game) {
            col -> game = onColumnClick(game, col)
            saveGame(game)
        }

        GameBoard(game)
    }
}

// Since the game's config panel could end up handling a lot of state, coming from a React background,
//   I tried to lift up as much of it as possible into props.
// It's also the component which handles invalid inputs and renders error messages.
@Composable
fun ConfigPanel(
    inputRows: String,
    inputCols: String,
    inputWinCondition: String,
    configError: String?,
    onRowsChange: (String) -> Unit,
    onColsChange: (String) -> Unit,
    onWinConditionChange: (String) -> Unit,
    onUpdate: () -> Unit
) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            marginBottom(10.px)
        }
    }) {
        Text("Rows:")
        TextInput(value = inputRows, attrs = {
            style {
                marginLeft(10.px)
                marginRight(20.px)
                borderRadius(5.px)
            }
            onInput { onRowsChange(it.value) }
        })
        Text("Cols:")
        TextInput(value = inputCols, attrs = {
            style {
                marginLeft(10.px)
                marginRight(20.px)
                borderRadius(5.px)
            }
            onInput { onColsChange(it.value) }
        })
        Text("Win condition:")
        TextInput(value = inputWinCondition, attrs = {
            style {
                marginLeft(10.px)
                borderRadius(5.px)
            }
            onInput { onWinConditionChange(it.value) }
        })
    }

    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            marginBottom(10.px)
        }
    }) {
        Button(attrs = {
            style {
                backgroundColor(COLOR_GRANITE)
                color(COLOR_MINT_CREAM)
                padding(10.px)
                fontSize(20.px)
                borderRadius(10.px)
            }
            onClick { onUpdate() }
        }) {
            Text("UPDATE (RESTART GAME)")
        }
    }

    if (configError != null) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                marginBottom(10.px)
            }
        }) {
            Span(attrs = {
                style {
                    backgroundColor(Color.palevioletred)
                    color(Color.darkred)
                    padding(10.px)
                    borderRadius(5.px)
                    fontWeight(700)
                }
            }) {
                Text(configError)
            }
        }
    }
}

// UI responsible for announcing winners, draws or which players turn it is.
@Composable
fun StatusBanner(game: GameState) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            marginBottom(20.px)
        }
    }) {
        when {
            game.winner != null -> {
                Span(attrs = {
                    style {
                        playerColors(game.winner)
                        padding(15.px)
                        borderRadius(10.px)
                        fontWeight(700)
                    }
                }) {
                    Text("Player ${game.winner.displayName()} wins!")
                }
            }
            game.isDraw -> {
                Span(attrs = {
                    style {
                        backgroundColor(Color.lightyellow)
                        color(Color.indianred)
                        padding(15.px)
                        borderRadius(10.px)
                        fontWeight(700)
                    }
                }) {
                    Text("It's a draw!")
                }
            }
            else -> {
                Span(attrs = {
                    style {
                        playerColors(game.currentPlayer)
                        padding(15.px)
                        borderRadius(10.px)
                        fontWeight(700)
                    }
                }) {
                    Text("Player ${game.currentPlayer.displayName()}'s turn")
                }
            }
        }
    }
}

// An additional "row" on top of the table, made up of buttons
@Composable
fun DropButtons(game: GameState, onColumnClick: (Int) -> Unit) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            width(100.percent)
            marginBottom(10.px)
        }
    }) {
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                width(80.percent)
            }
        }) {
            repeat(game.cols) { col ->
                val colFull = game.board[0][col] != 0
                val disabled = game.isOver || colFull
                Button(attrs = {
                    style {
                        color(COLOR_ASH_GREY)
                        backgroundColor(COLOR_GRANITE)
                        borderRadius(5.px)
                        property("border-color", "#7E8D85")
                        fontSize(20.px)
                        width(90.percent)
                        if (disabled) opacity(0.4)
                    }
                    if (!disabled) onClick { onColumnClick(col) }
                }) {
                    Text("↓")
                }
            }
        }
    }
}

// UI representing the actual board game.
@Composable
fun GameBoard(game: GameState) {
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            width(100.percent)
            height(60.vh)
        }
    }) {
        Table(attrs = {
            style {
                width(80.percent)
                property("border-collapse", "collapse")
                overflow("hidden")
            }
        }) {
            repeat(game.rows) { row ->
                Tr {
                    repeat(game.cols) { col ->
                        val cell = game.board[row][col]
                        Td(attrs = {
                            style {
                                property("border", "1px solid black")
                                width((100.0 / game.cols).percent)
                                height((100.0 / game.rows).percent)
                                textAlign("center")
                                backgroundColor(when (cell) {
                                    1 -> COLOR_CELADON
                                    2 -> COLOR_GREY_OLIVE
                                    else -> Color("#FFFFFF")
                                })
                            }
                        }) {
                            if (cell != 0) {
                                Span(attrs = {
                                    classes("piece")  // triggers the animation
                                    style {
                                        fontWeight(700)
                                        fontSize(30.px)
                                    }
                                }) {
                                    Text(cell.toString())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
