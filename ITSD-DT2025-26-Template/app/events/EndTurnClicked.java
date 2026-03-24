package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.TurnSystem;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case
 * the end-turn button.
 *
 * {
 *   messageType = “endTurnClicked”
 * }
 *
 * @author Dr. Richard McCreadie
 *
 */
public class EndTurnClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		if (gameState.isGameOver()) return;

		if (gameState.getCurrentPlayer() != 1) {
			BasicCommands.addPlayer1Notification(out, "Wait for your turn", 2);
			return;
		}

		if (gameState.isInputLocked()) {
			gameState.queueEndTurn();
			BasicCommands.addPlayer1Notification(out, "End turn queued", 2);
			return;
		}

		TurnSystem.endTurn(out, gameState);
	}
}
