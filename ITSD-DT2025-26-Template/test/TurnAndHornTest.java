import static org.junit.Assert.*;

import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;

public class TurnAndHornTest {

    @Test
    public void testSwitchPlayer() {
        GameState gameState = new GameState();

        assertEquals(1, gameState.getCurrentPlayer());

        gameState.switchPlayer();
        assertEquals(2, gameState.getCurrentPlayer());

        gameState.switchPlayer();
        assertEquals(1, gameState.getCurrentPlayer());
    }

    @Test
    public void testIncreaseTurn() {
        GameState gameState = new GameState();

        assertEquals(1, gameState.getTurnNumber());

        gameState.increaseTurn();
        assertEquals(2, gameState.getTurnNumber());

        gameState.increaseTurn();
        assertEquals(3, gameState.getTurnNumber());
    }

    @Test
    public void testHornEquipAndReduce() {
        CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell();
        BasicCommands.altTell = altTell;

        GameState gameState = new GameState();

        gameState.equipHorn(1);

        assertTrue(gameState.hasHorn(1));
        assertEquals(3, gameState.getHornDurability(1));

        gameState.reduceHornDurability(null, 1);
        assertEquals(2, gameState.getHornDurability(1));

        gameState.reduceHornDurability(null, 1);
        gameState.reduceHornDurability(null, 1);

        assertFalse(gameState.hasHorn(1));
        assertEquals(0, gameState.getHornDurability(1));
    }
}