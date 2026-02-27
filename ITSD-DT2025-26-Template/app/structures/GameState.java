package structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Tile;
import structures.board.BoardCell;
import structures.board.BoardCell.Highlight;
import utils.BasicObjectBuilders;



/**
 * Holds the ongoing game state (server-side).
 * Created in GameActor and shared across event processors.
 *
 * Board rules: 9 columns x 5 rows (0-based indices: x=0..8, y=0..4).
 */
public class GameState {

	public boolean gameInitalised = false;

	// Some template event files may reference this placeholder; keep it to avoid compile errors.
	public boolean something = false;

	// ---- Board constants ----
	public static final int BOARD_WIDTH = 9;
	public static final int BOARD_HEIGHT = 5;

	// New: logical board cells (each cell wraps a Tile + gameplay state)
	private BoardCell[][] cells;

	// Compatibility: keep a Tile list for old code paths
	private List<Tile> allTiles = new ArrayList<>();

	// New: list of all cells
	private List<BoardCell> allCells = new ArrayList<>();

	// New: last clicked cell (interaction landing point)
	private BoardCell lastClickedCell;

	// Track last highlight state by coordinates (avoids Set/List identity issues)
	private Highlight[][] lastHighlight = new Highlight[BOARD_WIDTH][BOARD_HEIGHT];


	/**
	 * Build the board and draw to the front-end.
	 * Safe to call multiple times (will only init once).
	 */
	public void initBoard(ActorRef out) {
		if (cells != null) return;

		cells = new BoardCell[BOARD_WIDTH][BOARD_HEIGHT];
		allCells = new ArrayList<>(BOARD_WIDTH * BOARD_HEIGHT);
		allTiles = new ArrayList<>(BOARD_WIDTH * BOARD_HEIGHT);

		for (int y = 0; y < BOARD_HEIGHT; y++) {
			for (int x = 0; x < BOARD_WIDTH; x++) {

				Tile tile = BasicObjectBuilders.loadTile(x, y);
				BoardCell cell = new BoardCell(x, y, tile);

				cells[x][y] = cell;
				allCells.add(cell);
				allTiles.add(tile);

				// init highlight tracking
				lastHighlight[x][y] = Highlight.NONE;

				// Draw normal tile (mode 0)
				// Either draw via cell.render(out) or directly:
				BasicCommands.drawTile(out, tile, 0);
			}
		}
	}

	/** Returns true if (x,y) is inside the board bounds. */
	public boolean isOnBoard(int x, int y) {
		return x >= 0 && x < BOARD_WIDTH && y >= 0 && y < BOARD_HEIGHT;
	}

	// ---------------------------------------------------------------------
	// Backward-compatible APIs (so existing code using Tile won't break)
	// ---------------------------------------------------------------------

	/** Old API: Get a tile by 0-based grid coordinates. */
	public Tile getTile(int x, int y) {
		if (cells == null) return null;
		if (!isOnBoard(x, y)) return null;
		return cells[x][y].getTile();
	}

	/** Old API: Read-only list of all tiles. */
	public List<Tile> getAllTiles() {
		return Collections.unmodifiableList(allTiles);
	}

	// ---------------------------------------------------------------------
	// New APIs (BoardCell-based gameplay functionality)
	// ---------------------------------------------------------------------

	/** New API: Get a BoardCell by 0-based coordinates. */
	public BoardCell getCell(int x, int y) {
		if (cells == null) return null;
		if (!isOnBoard(x, y)) return null;
		return cells[x][y];
	}

	/** New API: Read-only list of all cells. */
	public List<BoardCell> getAllCells() {
		return Collections.unmodifiableList(allCells);
	}

	/** Click landing point */
	public void setLastClickedCell(BoardCell cell) {
		this.lastClickedCell = cell;
	}

	public BoardCell getLastClickedCell() {
		return lastClickedCell;
	}

	/** Clear all highlights on the board (re-draw tiles). */



	/**
	 * Clear only the previously highlighted cells to reduce WebSocket traffic.
	 * This prevents missing drawTile messages and highlight desync.
	 */


	/**
	 * Update highlights safely:
	 * - clear previous highlighted cells
	 * - highlight selected cell as VALID
	 * - highlight neighbor cells as ATTACK
	 * - remember current highlighted cells for next click
	 */




	/** Force redraw the whole board as normal (mode 0). Useful to avoid stale highlights. */
	public void redrawBoardAsNormal(ActorRef out) {
		if (cells == null) return;
		for (BoardCell cell : allCells) {
			cell.setHighlight(Highlight.NONE);
			cell.render(out); // draws tile with mode 0
		}
	}





	/** Adjacent cells; includeDiagonal=true means 8-neighborhood. */
	public List<BoardCell> getAdjacentCells(int x, int y, boolean includeDiagonal) {
		List<BoardCell> result = new ArrayList<>();
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dy == 0) continue;
				if (!includeDiagonal && Math.abs(dx) + Math.abs(dy) != 1) continue;

				int nx = x + dx;
				int ny = y + dy;
				BoardCell n = getCell(nx, ny);
				if (n != null) result.add(n);
			}
		}
		return result;
	}

	public void updateHighlightsByCoord(
			ActorRef out,
			int selX,
			int selY,
			List<BoardCell> neighbors
	) {
		if (cells == null) return;

		// Desired highlight state
		Highlight[][] desired = new Highlight[BOARD_WIDTH][BOARD_HEIGHT];

		for (int y = 0; y < BOARD_HEIGHT; y++) {
			for (int x = 0; x < BOARD_WIDTH; x++) {
				desired[x][y] = Highlight.NONE;
			}
		}

		// Selected cell
		if (isOnBoard(selX, selY)) {
			desired[selX][selY] = Highlight.VALID;
		}

		// Adjacent cells
		if (neighbors != null) {
			for (BoardCell n : neighbors) {
				int nx = n.getX();
				int ny = n.getY();
				if (isOnBoard(nx, ny) && !(nx == selX && ny == selY)) {
					desired[nx][ny] = Highlight.ATTACK;
				}
			}
		}

		// Diff render
		for (int y = 0; y < BOARD_HEIGHT; y++) {
			for (int x = 0; x < BOARD_WIDTH; x++) {
				if (desired[x][y] != lastHighlight[x][y]) {
					BoardCell c = cells[x][y];
					c.setHighlight(desired[x][y]);
					c.render(out);
					lastHighlight[x][y] = desired[x][y];
				}
			}
		}
	}


}