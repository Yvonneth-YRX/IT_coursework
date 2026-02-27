package structures;

import java.util.List;
import java.util.ArrayList;
import structures.basic.Card;
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

    private List<Card> player1Deck = new ArrayList<>();
    private List<Card> player2Deck = new ArrayList<>();

    private List<Card> player1Hand = new ArrayList<>();
    private List<Card> player2Hand = new ArrayList<>();

    public List<Card> getPlayer1Deck() {
        return player1Deck;
    }

    public List<Card> getPlayer2Deck() {
        return player2Deck;
    }

    public List<Card> getPlayer1Hand() {
        return player1Hand;
    }

    public List<Card> getPlayer2Hand() {
        return player2Hand;
    }
}
