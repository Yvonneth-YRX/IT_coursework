package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case a card.
 * The event returns the position in the player's hand the card resides within.
 *
 * {
 *   messageType = “cardClicked”
 *   position = <hand index position [1-6]>
 * }
 *
 * @author Dr. Richard McCreadie
 *
 */
public class CardClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		if (gameState.isGameOver()) return;

		// for now only allow human player to play cards
		if (gameState.getCurrentPlayer() != 1) {
			BasicCommands.addPlayer1Notification(out, "Wait for your turn", 2);
			return;
		}

		int handPosition = message.get("position").asInt(); // 1..6
		int index = handPosition - 1;

		if (index < 0 || index >= gameState.getPlayer1Hand().size()) {
			return;
		}

		Card clickedCard = gameState.getPlayer1Hand().get(index);

		if (clickedCard == null) return;

		// click same hand slot again -> unselect
		if (gameState.getSelectedCard() != null &&
				gameState.getSelectedHandPosition() == handPosition) {
			gameState.clearSelection(out);
			return;
		}

		gameState.selectCard(out, clickedCard, handPosition);
		System.out.println("[EVENT] cardClicked position=" + handPosition + " card=" + clickedCard.getCardname());
	}
}
