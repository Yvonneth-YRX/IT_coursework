package actors;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import events.CardClicked;
import events.EndTurnClicked;
import events.EventProcessor;
import events.Heartbeat;
import events.Initalize;
import events.OtherClicked;
import events.TileClicked;
import events.UnitMoving;
import events.UnitStopped;
import play.libs.Json;
import structures.GameState;
import utils.ImageListForPreLoad;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;
import commands.BasicCommands;

import structures.basic.Card;

/**
 * The game actor is an Akka Actor that receives events from the user front-end UI (e.g. when 
 * the user clicks on the board) via a websocket connection. When an event arrives, the 
 * processMessage() method is called, which can be used to react to the event. The Game actor 
 * also includes an ActorRef object which can be used to issue commands to the UI to change 
 * what the user sees. The GameActor is created when the user browser creates a websocket
 * connection to back-end services (on load of the game web page).
 * @author Dr. Richard McCreadie
 *
 */
public class GameActor extends AbstractActor {

	private ObjectMapper mapper = new ObjectMapper(); // Jackson Java Object Serializer, is used to turn java objects to Strings
	private ActorRef out; // The ActorRef can be used to send messages to the front-end UI
	private Map<String,EventProcessor> eventProcessors; // Classes used to process each type of event
	private GameState gameState; // A class that can be used to hold game state information

	/**
	 * Constructor for the GameActor. This is called by the GameController when the websocket
	 * connection to the front-end is established.
	 * @param out
	 */
	@SuppressWarnings("deprecation")
	public GameActor(ActorRef out) {

		this.out = out; // save this, so we can send commands to the front-end later

		// create class instances to respond to the various events that we might recieve
		eventProcessors = new HashMap<String,EventProcessor>();
		eventProcessors.put("initalize", new Initalize());
		eventProcessors.put("heartbeat", new Heartbeat());
		eventProcessors.put("unitMoving", new UnitMoving());
		eventProcessors.put("unitstopped", new UnitStopped());
		eventProcessors.put("tileclicked", new TileClicked());
		eventProcessors.put("cardclicked", new CardClicked());
		eventProcessors.put("endturnclicked", new EndTurnClicked());
		eventProcessors.put("otherclicked", new OtherClicked());
		
		// Initalize a new game state object
		gameState = new GameState();
		
		// Get the list of image files to pre-load the UI with
		Set<String> images = ImageListForPreLoad.getImageListForPreLoad();
		
		try {
			ObjectNode readyMessage = Json.newObject();
			readyMessage.put("messagetype", "actorReady");
			readyMessage.put("preloadImages", mapper.readTree(mapper.writeValueAsString(images)));
			out.tell(readyMessage, out);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method simply farms out the processing of the json messages from the front-end to the
	 * processMessage method
	 * @return
	 */
	public Receive createReceive() {
		return receiveBuilder()
				.match(JsonNode.class, message -> {
					String messageType = message.get("messagetype").asText();
					if (!"heartbeat".equals(messageType)) {
						System.out.println(message);
					}
					processMessage(messageType, message);
				}).build();
	}

	/**
	 * This looks up an event processor for the specified message type.
	 * Note that this processing is asynchronous.
	 * @param messageType
	 * @param message
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({"deprecation"})
	public void processMessage(String messageType, JsonNode message) throws Exception{
		if ("restartgame".equals(messageType)) {
			gameState = new GameState();
			new Initalize().processEvent(out, gameState, message);
			return;
		}

		EventProcessor processor = eventProcessors.get(messageType);
		if (processor==null) {
			// Unknown event type received
			System.err.println("GameActor: Recieved unknown event type "+messageType);
		} else {
			processor.processEvent(out, gameState, message); // process the event
		}
	}
	
	
	public void reportError(String errorText) {
		ObjectNode returnMessage = Json.newObject();
		returnMessage.put("messagetype", "ERR");
		returnMessage.put("error", errorText);
		out.tell(returnMessage, out);
	}

    // Initialize the card piles of both sides111.
    public static void initializeDecks(GameState gameState) {
        gameState.getPlayer1Deck().clear();
        gameState.getPlayer2Deck().clear();

        // ===== Human player =====
        for (String cardPath : StaticConfFiles.humanCards) {

            for (int i = 0; i < 2; i++) {   // two counts of each kind

                Card card = BasicObjectBuilders.loadCard(
                        cardPath,
                        1,
                        Card.class
                );

                gameState.getPlayer1Deck().add(card);
            }
        }

        // ===== AI =====
        for (String cardPath : StaticConfFiles.aiCards) {

            for (int i = 0; i < 2; i++) {

                Card card = BasicObjectBuilders.loadCard(
                        cardPath,
                        2,
                        Card.class
                );

                gameState.getPlayer2Deck().add(card);
            }
        }

        // random
        Collections.shuffle(gameState.getPlayer1Deck());
        Collections.shuffle(gameState.getPlayer2Deck());
    }

	private static void drawCard(ActorRef out, GameState gameState, int player, boolean renderToClient) {
		List<Card> deck;
		List<Card> hand;

		if (player == 1) {
			deck = gameState.getPlayer1Deck();
			hand = gameState.getPlayer1Hand();
		} else {
			deck = gameState.getPlayer2Deck();
			hand = gameState.getPlayer2Hand();
		}

		if (deck.isEmpty()) return;

		Card drawnCard = deck.remove(0);

		if (hand.size() >= 6) {
			String owner = player == 1 ? "You" : "AI";
			String cardName = drawnCard == null ? "a card" : drawnCard.getCardname();
			BasicCommands.addPlayer1Notification(out, owner + " overdrew and burned " + cardName, 2);
			return;
		}

		hand.add(drawnCard);
		int handPosition = hand.size(); // 1-based for UI

		if (renderToClient) {
			BasicCommands.drawCard(out, drawnCard, handPosition, player - 1);
		}
	}

	// General card-drawing method
	public static void drawCard(ActorRef out, GameState gameState, int player) {
		drawCard(out, gameState, player, player == 1);
	}

    public static void drawStartingHand(ActorRef out, GameState gameState) {
        // Human plyer
        for (int i = 0; i < 3; i++) {
            drawCard(out, gameState, 1, true);
        }

        // AI
        for (int i = 0; i < 3; i++) {
            drawCard(out, gameState, 2, false);
        }
    }

}
