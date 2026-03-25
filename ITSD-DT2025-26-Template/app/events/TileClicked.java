package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Unit;
import structures.board.BoardCell;

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
public class TileClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		if (gameState.isGameOver()) return;

		int x = message.get("tilex").asInt();
		int y = message.get("tiley").asInt();

		System.out.println("[EVENT] tileClicked x=" + x + " y=" + y);

		BoardCell clickedCell = gameState.getCell(x, y);
		if (clickedCell == null) return;

		gameState.setLastClickedCell(clickedCell);

		// only human player is controllable for now
		if (gameState.getCurrentPlayer() != 1) {
			BasicCommands.addPlayer1Notification(out, "Wait for your turn", 2);
			return;
		}

		if (gameState.isInputLocked()) {
			BasicCommands.addPlayer1Notification(out, "Wait for the current action to finish", 2);
			return;
		}

		// -----------------------------------------------------------------
		// 1) If a card is selected, resolve summon / spell first
		// -----------------------------------------------------------------
		if (gameState.getSelectedCard() != null) {
			if (gameState.tryResolveCardActionAt(out, clickedCell)) {
				return;
			} else {
				// clicked somewhere invalid while card selected -> keep the current card selected
				BasicCommands.addPlayer1Notification(out, "Invalid target", 2);
				return;
			}
		}

		// -----------------------------------------------------------------
		// 2) Otherwise use existing unit logic
		// -----------------------------------------------------------------
		Unit selectedUnit = gameState.getSelectedUnit();

		if (selectedUnit != null) {
			if (clickedCell.isOccupied()) {
				Unit clickedUnit = clickedCell.getOccupant();
				if (clickedUnit != null && selectedUnit.getId() == clickedUnit.getId()) {
					gameState.clearSelection(out);
					return;
				}
			}

			if (clickedCell.isEmpty() && gameState.isProvoked(selectedUnit)) {
				BasicCommands.addPlayer1Notification(out, "This unit is provoked!", 2);
				return;
			}

			if (clickedCell.isEmpty()) {
				if (gameState.moveSelectedUnitTo(out, clickedCell)) {
					return;
				}
			} else {

				if (gameState.isProvoked(selectedUnit)) {

					Unit target = clickedCell.getOccupant();

					if (target == null || !target.isProvoke()) {
						BasicCommands.addPlayer1Notification(out, "Must attack Provoke unit!", 2);
						return;
					}
				}

				if (gameState.attackSelectedTarget(out, clickedCell)) {
					return;
				}
			}
		}

		if (clickedCell.isOccupied()) {
			Unit clickedUnit = clickedCell.getOccupant();

			if (gameState.isOwnedByCurrentPlayer(clickedUnit)) {
				if (gameState.hasMovedThisTurn(clickedUnit) && gameState.hasAttackedThisTurn(clickedUnit)) {
					BasicCommands.addPlayer1Notification(out, "This unit has finished its action", 2);
					return;
				}

				if (gameState.isUnitStunned(clickedUnit)) {
					BasicCommands.addPlayer1Notification(out, "This unit is stunned", 2);
					return;
				}

				gameState.selectUnit(out, clickedUnit);
				return;
			}
		}

		gameState.clearSelection(out);
	}
}
