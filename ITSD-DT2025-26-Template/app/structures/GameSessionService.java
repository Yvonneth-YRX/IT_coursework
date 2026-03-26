package structures;

import java.util.Collections;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

/**
 * Centralizes game session setup and card-flow orchestration so actor/event
 * classes do not need to coordinate low-level startup steps.
 */
public final class GameSessionService {

    private GameSessionService() {
    }

    public static void initializeSession(ActorRef out, GameState gameState) {
        gameState.markInitialized();

        initializeDecks(gameState);
        drawStartingHand(out, gameState);

        gameState.initBoard(out);
        gameState.placeInitialUnits(out);

        BasicCommands.setPlayer1Health(out, gameState.getPlayer1());
        BasicCommands.setPlayer2Health(out, gameState.getPlayer2());
        BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
        BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());

        TurnSystem.startTurn(out, gameState);
    }

    public static void initializeDecks(GameState gameState) {
        gameState.getPlayer1Deck().clear();
        gameState.getPlayer2Deck().clear();
        int nextCardId = 1;

        for (String cardPath : StaticConfFiles.humanCards) {
            for (int i = 0; i < 2; i++) {
                Card card = BasicObjectBuilders.loadCard(cardPath, nextCardId++, Card.class);
                gameState.getPlayer1Deck().add(card);
            }
        }

        for (String cardPath : StaticConfFiles.aiCards) {
            for (int i = 0; i < 2; i++) {
                Card card = BasicObjectBuilders.loadCard(cardPath, nextCardId++, Card.class);
                gameState.getPlayer2Deck().add(card);
            }
        }

        Collections.shuffle(gameState.getPlayer1Deck());
        Collections.shuffle(gameState.getPlayer2Deck());
    }

    public static void drawStartingHand(ActorRef out, GameState gameState) {
        for (int i = 0; i < 3; i++) {
            drawCard(out, gameState, 1, true);
        }

        for (int i = 0; i < 3; i++) {
            drawCard(out, gameState, 2, false);
        }
    }

    public static void drawCard(ActorRef out, GameState gameState, int player) {
        drawCard(out, gameState, player, player == 1);
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

        if (deck.isEmpty() || hand.size() >= 6) {
            return;
        }

        Card drawnCard = deck.remove(0);
        hand.add(drawnCard);

        if (renderToClient) {
            int handPosition = hand.size();
            BasicCommands.drawCard(out, drawnCard, handPosition, player - 1);
        }
    }
}
