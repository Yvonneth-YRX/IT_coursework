import static org.junit.Assert.*;

import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;
import structures.board.BoardCell;

public class GameStateBoardTest {

    @Test
    public void testBoardInitialization() {
        CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell();
        BasicCommands.altTell = altTell;

        GameState gameState = new GameState();
        gameState.initBoard(null);

        assertNotNull(gameState.getCell(0, 0));
        assertNotNull(gameState.getCell(8, 4));
        assertNull(gameState.getCell(-1, 0));
        assertNull(gameState.getCell(9, 0));
        assertEquals(45, gameState.getAllCells().size());
    }

    @Test
    public void testIsOnBoard() {
        GameState gameState = new GameState();

        assertTrue(gameState.isOnBoard(0, 0));
        assertTrue(gameState.isOnBoard(8, 4));
        assertFalse(gameState.isOnBoard(-1, 0));
        assertFalse(gameState.isOnBoard(9, 0));
        assertFalse(gameState.isOnBoard(0, 5));
    }

    @Test
    public void testAdjacentCells() {
        CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell();
        BasicCommands.altTell = altTell;

        GameState gameState = new GameState();
        gameState.initBoard(null);

        BoardCell center = gameState.getCell(4, 2);

        assertEquals(8, gameState.getAdjacentCells(center.getX(), center.getY(), true).size());
        assertEquals(4, gameState.getAdjacentCells(center.getX(), center.getY(), false).size());
    }
}