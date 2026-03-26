package structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.board.BoardCell;
import structures.board.BoardCell.Highlight;
import utils.BasicObjectBuilders;

/**
 * Extracted from the original GameState.java board-management code so board
 * rendering and traversal logic is no longer embedded directly in the main
 * state object.
 */
public final class BoardService {

    private BoardService() {
    }

    public static void initBoard(GameState gameState, ActorRef out) {
        if (gameState.getBoardCells() != null) {
            return;
        }

        BoardCell[][] cells = new BoardCell[GameState.BOARD_WIDTH][GameState.BOARD_HEIGHT];
        List<BoardCell> allCells = new ArrayList<>(GameState.BOARD_WIDTH * GameState.BOARD_HEIGHT);
        List<Tile> allTiles = new ArrayList<>(GameState.BOARD_WIDTH * GameState.BOARD_HEIGHT);
        Highlight[][] lastHighlight = gameState.getLastHighlightGrid();

        for (int y = 0; y < GameState.BOARD_HEIGHT; y++) {
            for (int x = 0; x < GameState.BOARD_WIDTH; x++) {
                Tile tile = BasicObjectBuilders.loadTile(x, y);
                BoardCell cell = new BoardCell(x, y, tile);

                cells[x][y] = cell;
                allCells.add(cell);
                allTiles.add(tile);

                lastHighlight[x][y] = Highlight.NONE;
                BasicCommands.drawTile(out, tile, 0);
            }
        }

        gameState.setBoardCells(cells);
        gameState.setAllCells(allCells);
        gameState.setAllTiles(allTiles);
    }

    public static boolean isOnBoard(int x, int y) {
        return x >= 0 && x < GameState.BOARD_WIDTH && y >= 0 && y < GameState.BOARD_HEIGHT;
    }

    public static BoardCell getCell(GameState gameState, int x, int y) {
        BoardCell[][] cells = gameState.getBoardCells();
        if (cells == null || !isOnBoard(x, y)) {
            return null;
        }
        return cells[x][y];
    }

    public static Tile getTile(GameState gameState, int x, int y) {
        BoardCell cell = getCell(gameState, x, y);
        return cell == null ? null : cell.getTile();
    }

    public static List<Tile> getAllTiles(GameState gameState) {
        return Collections.unmodifiableList(gameState.getMutableAllTiles());
    }

    public static List<BoardCell> getAllCells(GameState gameState) {
        return Collections.unmodifiableList(gameState.getMutableAllCells());
    }

    public static void clearAllHighlights(GameState gameState, ActorRef out) {
        BoardCell[][] cells = gameState.getBoardCells();
        if (cells == null) {
            return;
        }

        Highlight[][] lastHighlight = gameState.getLastHighlightGrid();
        for (int y = 0; y < GameState.BOARD_HEIGHT; y++) {
            for (int x = 0; x < GameState.BOARD_WIDTH; x++) {
                if (lastHighlight[x][y] != Highlight.NONE) {
                    BoardCell cell = cells[x][y];
                    cell.setHighlight(Highlight.NONE);
                    cell.render(out);
                    lastHighlight[x][y] = Highlight.NONE;
                }
            }
        }
    }

    public static void redrawBoardAsNormal(GameState gameState, ActorRef out) {
        BoardCell[][] cells = gameState.getBoardCells();
        if (cells == null) {
            return;
        }

        Highlight[][] lastHighlight = gameState.getLastHighlightGrid();
        for (BoardCell cell : gameState.getMutableAllCells()) {
            cell.setHighlight(Highlight.NONE);
            cell.render(out);
            lastHighlight[cell.getX()][cell.getY()] = Highlight.NONE;
        }
    }

    public static BoardCell getCellForUnit(GameState gameState, Unit unit) {
        if (unit == null) {
            return null;
        }

        for (BoardCell cell : gameState.getMutableAllCells()) {
            Unit occupant = cell.getOccupant();
            if (occupant != null && occupant.getId() == unit.getId()) {
                return cell;
            }
        }
        return null;
    }

    public static List<BoardCell> getAdjacentCells(GameState gameState, int x, int y, boolean includeDiagonal) {
        List<BoardCell> result = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                if (!includeDiagonal && Math.abs(dx) + Math.abs(dy) != 1) {
                    continue;
                }

                BoardCell cell = getCell(gameState, x + dx, y + dy);
                if (cell != null) {
                    result.add(cell);
                }
            }
        }
        return result;
    }

    public static boolean areAdjacent(BoardCell a, BoardCell b, boolean includeDiagonal) {
        if (a == null || b == null) {
            return false;
        }

        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());

        if (includeDiagonal) {
            return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
        }
        return dx + dy == 1;
    }
}
