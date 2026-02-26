package structures;

import com.fasterxml.jackson.databind.ObjectMapper;
import structures.basic.Unit;
import structures.basic.Tile;
import akka.actor.ActorRef;
import commands.BasicCommands;

/**
 * This class can be used to hold information about the on-going game.
 * Its created with the GameActor.
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class GameState {

	
	public boolean gameInitalised = false;
	
	public boolean something = false;

	/**
	 * Place player characters and AI characters
	 */
	private void placeInitialUnits(ActorRef out) {

		try {

			ObjectMapper mapper = new ObjectMapper();

			Tile playerTile = board[1][2];
			Tile aiTile = board[7][2];

			Unit playerUnit = mapper.readValue(
					new File("conf/gameconfs/avatars/avatar1.json"),
					Unit.class
			);

			Unit aiUnit = mapper.readValue(
					new File("conf/gameconfs/avatars/avatar2.json"),
					Unit.class
			);

			playerUnit.setId(1);
			aiUnit.setId(2);

			playerUnit.setPositionByTile(playerTile);
			aiUnit.setPositionByTile(aiTile);

			BasicCommands.drawUnit(out, playerUnit, playerTile);
			BasicCommands.drawUnit(out, aiUnit, aiTile);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
