package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import structures.GameState;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case
 * somewhere that is not on a card tile or the end-turn button.
 *
 * {
 *   messageType = “otherClicked”
 * }
 *
 * @author Dr. Richard McCreadie
 *
 */
public class OtherClicked implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		// Intentionally do nothing.
		// The frontend may fire "otherClicked" immediately after "cardClicked",
		// which causes the selected card to flash once and disappear.
		// Card / unit deselection is already handled by:
		// 1) clicking the same card again
		// 2) successful card resolution
		// 3) successful movement / attack resolution
		System.out.println("[EVENT] otherClicked ignored");
	}
}