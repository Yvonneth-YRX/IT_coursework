package structures;

import structures.basic.Tile;

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

	// 9*5 board
	public Tile[][] board = new Tile[9][5];

}
