package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Player;

public class TurnSystem {

    // Start round
    public static void startTurn(ActorRef out, GameState gameState) {

        Player player = gameState.getCurrentPlayerObject();

        int turn = gameState.getTurnNumber();
        int mana = turn + 1;

        if (mana > 9) {
            mana = 9;
        }

        // Set the player's mana
        player.setMana(mana);

        // Update UI
        if (gameState.getCurrentPlayer() == 1) {
            BasicCommands.setPlayer1Mana(out, player);
            BasicCommands.addPlayer1Notification(out,"Your Turn",2);
        } else {
            BasicCommands.setPlayer2Mana(out, player);
        }

        System.out.println("Start Turn: Player " + gameState.getCurrentPlayer());
    }


    // End round
    public static void endTurn(ActorRef out, GameState gameState) {

        Player player = gameState.getCurrentPlayerObject();

        // Set The current player's mana to zero.
        player.setMana(0);

        if (gameState.getCurrentPlayer() == 1) {
            BasicCommands.setPlayer1Mana(out, player);
        } else {
            BasicCommands.setPlayer2Mana(out, player);
        }

        // Switch player
        gameState.switchPlayer();

        // Increase number of rounds
        gameState.increaseTurn();

        // Start new round
        startTurn(out, gameState);
    }
}