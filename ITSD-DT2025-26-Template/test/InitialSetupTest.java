import static org.junit.Assert.*;

import org.junit.Test;

import commands.BasicCommands;
import commands.CheckMessageIsNotNullOnTell;
import structures.GameState;
import structures.basic.Unit;
import structures.board.BoardCell;

public class InitialSetupTest {

    @Test
    public void testPlaceInitialUnits() {
        CheckMessageIsNotNullOnTell altTell = new CheckMessageIsNotNullOnTell();
        BasicCommands.altTell = altTell;

        GameState gameState = new GameState();
        gameState.initBoard(null);
        gameState.placeInitialUnits(null);

        Unit p1Avatar = gameState.getPlayer1Avatar();
        Unit p2Avatar = gameState.getPlayer2Avatar();

        assertNotNull(p1Avatar);
        assertNotNull(p2Avatar);

        BoardCell p1Cell = gameState.getCellForUnit(p1Avatar);
        BoardCell p2Cell = gameState.getCellForUnit(p2Avatar);

        assertNotNull(p1Cell);
        assertNotNull(p2Cell);

        assertEquals(1, gameState.getUnitOwner(p1Avatar));
        assertEquals(2, gameState.getUnitOwner(p2Avatar));

        assertEquals(20, p1Avatar.getHealth());
        assertEquals(20, p2Avatar.getHealth());
        assertEquals(2, p1Avatar.getAttack());
        assertEquals(2, p2Avatar.getAttack());
    }

    @Test
    public void testAllocateUnitId() {
        GameState gameState = new GameState();

        int id1 = gameState.allocateUnitId();
        int id2 = gameState.allocateUnitId();

        assertTrue(id2 > id1);
    }
}