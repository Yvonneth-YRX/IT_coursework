package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
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

        player.setMana(mana);

        // reset all units of current player
        gameState.resetCurrentPlayerUnitsForNewTurn();

        if (gameState.getCurrentPlayer() == 1) {
            BasicCommands.setPlayer1Mana(out, player);
            BasicCommands.addPlayer1Notification(out, "Your Turn", 2);
        } else {
            BasicCommands.setPlayer2Mana(out, player);
            BasicCommands.addPlayer1Notification(out, "AI Turn", 2);

            // let AI act automatically
            try {
                Thread.sleep(300);
            } catch (Exception e) {
                e.printStackTrace();
            }

            AIController.takeTurn(out, gameState);

            // after AI finishes, automatically end AI turn
            if (!gameState.isGameOver() && gameState.getCurrentPlayer() == 2) {
                if (gameState.isInputLocked() || gameState.hasMovingUnits()) {
                    gameState.queueEndTurn();
                } else {
                    endTurn(out, gameState);
                }
            }
        }

        System.out.println("Start Turn: Player " + gameState.getCurrentPlayer());
    }

    // End round
    public static void endTurn(ActorRef out, GameState gameState) {

        if (gameState.isGameOver()) return;
        if (gameState.isInputLocked() || gameState.hasMovingUnits()) {
            gameState.queueEndTurn();
            return;
        }

        System.out.println("[TURN] endTurn start, currentPlayer = " + gameState.getCurrentPlayer());

        Player player = gameState.getCurrentPlayerObject();

        // current player's mana -> 0
        player.setMana(0);

        if (gameState.getCurrentPlayer() == 1) {
            BasicCommands.setPlayer1Mana(out, player);
        } else {
            BasicCommands.setPlayer2Mana(out, player);
        }

        // clear current selection/highlights before switching
        gameState.clearSelection(out);

        // switch player
        gameState.switchPlayer();

        // increase turn number
        gameState.increaseTurn();

        System.out.println("[TURN] switched to player = " + gameState.getCurrentPlayer());

        // draw 1 card for new current player
        GameSessionService.drawCard(out, gameState, gameState.getCurrentPlayer());

        // start new round
        startTurn(out, gameState);
    }
}
