package structures.board;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Tile;
import structures.basic.Unit;

/**
 * A single logical cell on the board.
 * Wraps a renderable Tile and adds gameplay state:
 * - occupant (unit on this cell)
 * - highlight state
 */
public class BoardCell {

    public enum Highlight {
        NONE(0),        // normal tile texture
        VALID(1),       // green-ish highlight (tile_grid.png)
        ATTACK(2);      // red highlight (tile_grid_red.png)

        private final int mode;
        Highlight(int mode) { this.mode = mode; }
        public int mode() { return mode; }
    }

    private final int x;        // 0..8
    private final int y;        // 0..4
    private final Tile tile;

    private Unit occupant;      // null = empty
    private Highlight highlight = Highlight.NONE;

    public BoardCell(int x, int y, Tile tile) {
        this.x = x;
        this.y = y;
        this.tile = tile;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Tile getTile() { return tile; }

    // ----- Occupancy -----
    public boolean isEmpty() { return occupant == null; }
    public boolean isOccupied() { return occupant != null; }
    public Unit getOccupant() { return occupant; }

    /**
     * Put a unit on this cell. Returns false if already occupied.
     * (Later you can decide whether to allow replacement.)
     */
    public boolean trySetOccupant(Unit unit) {
        if (unit == null) throw new IllegalArgumentException("unit cannot be null");
        if (occupant != null) return false;
        occupant = unit;
        return true;
    }

    public void clearOccupant() {
        occupant = null;
    }

    // ----- Highlight -----
    public Highlight getHighlight() { return highlight; }

    public void setHighlight(Highlight highlight) {
        if (highlight == null) highlight = Highlight.NONE;
        this.highlight = highlight;
    }

    /** Re-draw this cell’s tile based on current highlight mode. */
    public void render(ActorRef out) {
        BasicCommands.drawTile(out, tile, highlight.mode());
    }
}