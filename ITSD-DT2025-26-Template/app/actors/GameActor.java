package actors;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
import structures.GameSessionService;
import utils.ImageListForPreLoad;

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

	private final ObjectMapper mapper = new ObjectMapper(); // Jackson Java Object Serializer, is used to turn java objects to Strings
	private final ActorRef out; // The ActorRef can be used to send messages to the front-end UI
	private final Map<String,EventProcessor> eventProcessors; // Classes used to process each type of event
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
		eventProcessors = createEventProcessors();
		
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

	private Map<String, EventProcessor> createEventProcessors() {
		Map<String, EventProcessor> processors = new HashMap<String, EventProcessor>();
		processors.put("initalize", new Initalize());
		processors.put("heartbeat", new Heartbeat());
		processors.put("unitMoving", new UnitMoving());
		processors.put("unitstopped", new UnitStopped());
		processors.put("tileclicked", new TileClicked());
		processors.put("cardclicked", new CardClicked());
		processors.put("endturnclicked", new EndTurnClicked());
		processors.put("otherclicked", new OtherClicked());
		return processors;
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
			GameSessionService.initializeSession(out, gameState);
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

    /**
     * Legacy compatibility bridge kept in the original actor file so the
     * coursework template history stays visible. The live implementation now
     * lives in {@link GameSessionService}.
     */
    @Deprecated
    public static void initializeDecks(GameState gameState) {
        GameSessionService.initializeDecks(gameState);
    }

    /**
     * Legacy compatibility bridge. Card-flow orchestration is now centralized
     * in {@link GameSessionService} to avoid duplicating startup logic.
     */
    @Deprecated
    public static void drawCard(ActorRef out, GameState gameState, int player) {
        GameSessionService.drawCard(out, gameState, player);
    }

    /**
     * Legacy compatibility bridge. Startup hand generation now lives in
     * {@link GameSessionService}.
     */
    @Deprecated
    public static void drawStartingHand(ActorRef out, GameState gameState) {
        GameSessionService.drawStartingHand(out, gameState);
    }
}
