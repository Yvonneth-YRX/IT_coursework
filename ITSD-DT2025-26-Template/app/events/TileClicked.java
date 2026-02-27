package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;

import structures.board.BoardCell.Highlight;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case a tile.
 * The event returns the x (horizontal) and y (vertical) indices of the tile that was
 * clicked. Tile indices start at 1.
 * 
 * { 
 *   messageType = “tileClicked”
 *   tilex = <x index of the tile>
 *   tiley = <y index of the tile>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class TileClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		int x = message.get("tilex").asInt();
		int y = message.get("tiley").asInt();

		System.out.println("[EVENT] tileClicked x=" + x + " y=" + y);

		var cell = gameState.getCell(x, y);
		if (cell == null) return;

		gameState.setLastClickedCell(cell);

		// compute adjacent cells (including diagonal)
		var neighbors = gameState.getAdjacentCells(x, y, true);

		// IMPORTANT: use the new incremental highlight update (do NOT call clearAllHighlights here)
		gameState.updateHighlightsByCoord(out, x, y, neighbors);
	}
}
