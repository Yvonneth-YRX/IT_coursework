import static org.junit.Assert.*;

import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

public class UnitStateTest {

    @Test
    public void testRegisterUnitOwner() throws Exception {
        CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell();
        BasicCommands.altTell = altTell;

        GameState gameState = new GameState();
        gameState.initBoard(null);

        Unit unit = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 999, Unit.class);
        gameState.registerUnit(unit, 1);

        assertEquals(1, gameState.getUnitOwner(unit));
        assertFalse(gameState.hasMovedThisTurn(unit));
        assertFalse(gameState.hasAttackedThisTurn(unit));
    }

    @Test
    public void testMoveAndAttackFlags() throws Exception {
        GameState gameState = new GameState();
        gameState.initBoard(null);

        Unit unit = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 998, Unit.class);
        gameState.registerUnit(unit, 1);

        gameState.setMovedThisTurn(unit, true);
        gameState.setAttackedThisTurn(unit, true);

        assertTrue(gameState.hasMovedThisTurn(unit));
        assertTrue(gameState.hasAttackedThisTurn(unit));
    }

    @Test
    public void testStunUnit() throws Exception {
        GameState gameState = new GameState();
        gameState.initBoard(null);

        Unit unit = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 997, Unit.class);
        gameState.registerUnit(unit, 1);

        gameState.stunUnitUntilNextTurn(unit);

        assertTrue(gameState.isUnitStunned(unit));
        assertTrue(gameState.hasMovedThisTurn(unit));
        assertTrue(gameState.hasAttackedThisTurn(unit));
    }
}