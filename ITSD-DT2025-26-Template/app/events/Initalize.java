package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import demo.CommandDemo;
import demo.Loaders_2024_Check;
import structures.GameState;
import commands.BasicCommands;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

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

		// 标记游戏已经初始化
		gameState.gameInitalised = true;

		// Draw 9x5 board
		for (int x = 0; x < 9; x++) {
			for (int y = 0; y < 5; y++) {

				Tile tile = BasicObjectBuilders.loadTile(x, y);
				gameState.board[x][y] = tile;

				BasicCommands.drawTile(out, tile, 0);

				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		BasicCommands.addPlayer1Notification(out, "Board initialised", 2);
	}

}


