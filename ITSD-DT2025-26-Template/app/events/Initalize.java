package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import demo.CommandDemo;
import demo.Loaders_2024_Check;
import structures.GameState;

/**
 * Indicates that both the core game loop in the browser is starting, meaning
 * that it is ready to recieve commands from the back-end.
 * 
 * { 
 *   messageType = “initalize”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class Initalize implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

		gameState.gameInitalised = true;

		// 1) Initialize + draw the board (9x5)
		gameState.initBoard(out);

		// 2) Stop running the demo once you start implementing the real game
		// CommandDemo.executeDemo(out); // <-- keep this commented out
		// Loaders_2024_Check.test(out);

		gameState.something = true;

		// User 1 makes a change
		CommandDemo.executeDemo(out); // this executes the command demo, comment out this when implementing your solution
		//Loaders_2024_Check.test(out);

		gameState.placeInitialUnits(out);

	}

}


