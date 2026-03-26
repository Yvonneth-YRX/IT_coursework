package structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.board.BoardCell;
import structures.board.BoardCell.Highlight;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;



/**
 * Holds the ongoing game state (server-side).
 * Board rules: 9 columns x 5 rows (0-based indices: x=0..8, y=0..4).
 */
public class GameState {

	@Deprecated
	public boolean gameInitalised = false;

	private boolean initialized = false;

	// ---- Board constants ----
	public static final int BOARD_WIDTH = 9;
	public static final int BOARD_HEIGHT = 5;

	// ---- Board ----
	private BoardCell[][] cells;
	private List<Tile> allTiles = new ArrayList<>();
	private List<BoardCell> allCells = new ArrayList<>();
	private BoardCell lastClickedCell;
	private Highlight[][] lastHighlight = new Highlight[BOARD_WIDTH][BOARD_HEIGHT];

	// ---- Players ----
	private Player player1 = new Player();
	private Player player2 = new Player();

	private Unit player1Avatar;
	private Unit player2Avatar;

	// ---- Turn system ----
	private int currentPlayer = 1;
	private int turnNumber = 1;
	private boolean gameOver = false;

	// ---- Input / animation lock ----
	private boolean inputLocked = false;
	private boolean pendingEndTurn = false;
	private long lastActionAtMs = 0L;
	private static final long INPUT_LOCK_TIMEOUT_MS = 2200L;
	private final Set<Integer> movingUnits = new LinkedHashSet<>();

	// ---- Artifacts ----
	private boolean player1HornActive = false;
	private boolean player2HornActive = false;

	private int player1HornDurability = 0;
	private int player2HornDurability = 0;

	// ---- Deck / Hand ----
	private List<Card> player1Deck = new ArrayList<>();
	private List<Card> player2Deck = new ArrayList<>();
	private List<Card> player1Hand = new ArrayList<>();
	private List<Card> player2Hand = new ArrayList<>();

	// ---- Unit ownership + turn flags ----
	private final Map<Integer, Integer> unitOwners = new HashMap<>();
	private final Map<Integer, String> unitCardNames = new HashMap<>();
	private final Map<Integer, Boolean> movedThisTurn = new HashMap<>();
	private final Map<Integer, Boolean> attackedThisTurn = new HashMap<>();
	private final Map<Integer, Integer> stunnedUntilTurn = new HashMap<>();

	// ---- Selection ----
	private Unit selectedUnit;
	private Card selectedCard;
	private int selectedHandPosition = -1; // UI position: 1..6

	private final List<BoardCell> selectedMoveCells = new ArrayList<>();
	private final List<BoardCell> selectedAttackCells = new ArrayList<>();
	private final List<BoardCell> selectedCardTargetCells = new ArrayList<>();

	// ---- Card click debounce ----
	private long lastCardSelectionAtMs = 0L;

	private final Set<Integer> deathResolvedThisStep = new HashSet<>();

	private int nextUnitId = 100;

	// ---- queued move-then-attack ----
	private Integer pendingFollowUpAttackUnitId = null;
	private Integer pendingFollowUpAttackTargetX = null;
	private Integer pendingFollowUpAttackTargetY = null;

	private enum SelectionMode {
		NONE,
		UNIT,
		CARD_SUMMON,
		CARD_SPELL
	}

	private SelectionMode selectionMode = SelectionMode.NONE;

	// ---------------------------------------------------------------------
	// Basic getters
	// ---------------------------------------------------------------------

	public Player getPlayer1() { return player1; }
	public Player getPlayer2() { return player2; }
	public boolean isInitialized() { return initialized; }

	public void markInitialized() {
		this.initialized = true;
		this.gameInitalised = true;
	}

	public Unit getPlayer1Avatar() { return player1Avatar; }
	public Unit getPlayer2Avatar() { return player2Avatar; }

	public int getCurrentPlayer() { return currentPlayer; }
	public int getTurnNumber() { return turnNumber; }
	public boolean isGameOver() { return gameOver; }
	public boolean isInputLocked() { return inputLocked; }
	public boolean hasPendingEndTurn() { return pendingEndTurn; }
	public boolean hasMovingUnits() { return !movingUnits.isEmpty(); }

	public List<Card> getPlayer1Deck() { return player1Deck; }
	public List<Card> getPlayer2Deck() { return player2Deck; }
	public List<Card> getPlayer1Hand() { return player1Hand; }
	public List<Card> getPlayer2Hand() { return player2Hand; }

	public Unit getSelectedUnit() { return selectedUnit; }
	public Card getSelectedCard() { return selectedCard; }
	public int getSelectedHandPosition() { return selectedHandPosition; }
	public boolean hasSelectedCard() { return selectedCard != null; }
	public long getLastCardSelectionAtMs() { return lastCardSelectionAtMs; }

	public Player getCurrentPlayerObject() {
		return currentPlayer == 1 ? player1 : player2;
	}

	public Player getOpponentPlayerObject() {
		return currentPlayer == 1 ? player2 : player1;
	}

	public List<Card> getCurrentPlayerHand() {
		return currentPlayer == 1 ? player1Hand : player2Hand;
	}

	public List<Card> getCurrentPlayerDeck() {
		return currentPlayer == 1 ? player1Deck : player2Deck;
	}

	public List<Unit> getUnits() {
		List<Unit> units = new ArrayList<>();
		for (BoardCell cell : allCells) {
			if (cell.isOccupied()) {
				units.add(cell.getOccupant());
			}
		}
		return units;
	}

	public void equipHorn(int player) {
		if (player == 1) {
			player1HornActive = true;
			player1HornDurability = 3;
		} else {
			player2HornActive = true;
			player2HornDurability = 3;
		}
	}

	public boolean hasHorn(int player) {
		if (player == 1) {
			return player1HornActive;
		} else {
			return player2HornActive;
		}
	}

	public int getHornDurability(int player) {
		if (player == 1) {
			return player1HornDurability;
		} else {
			return player2HornDurability;
		}
	}

	public void reduceHornDurability(ActorRef out, int player) {
		if (player == 1) {
			if (!player1HornActive) return;

			player1HornDurability = player1HornDurability - 1;

			if (player1HornDurability <= 0) {
				player1HornActive = false;
				player1HornDurability = 0;
				BasicCommands.addPlayer1Notification(out, "Player 1 Horn destroyed", 2);
			}
		} else {
			if (!player2HornActive) return;

			player2HornDurability = player2HornDurability - 1;

			if (player2HornDurability <= 0) {
				player2HornActive = false;
				player2HornDurability = 0;
				BasicCommands.addPlayer1Notification(out, "Player 2 Horn destroyed", 2);
			}
		}
	}

	public void switchPlayer() {
		currentPlayer = (currentPlayer == 1) ? 2 : 1;
	}

	public void increaseTurn() {
		turnNumber++;
	}

	public void setGameOver(boolean gameOver) {
		this.gameOver = gameOver;
	}

	public void queueEndTurn() {
		pendingEndTurn = true;
	}

	public void clearQueuedEndTurn() {
		pendingEndTurn = false;
	}

	public void noteActionIssued() {
		lastActionAtMs = System.currentTimeMillis();
	}

	public void lockInputForUnit(int unitId) {
		inputLocked = true;
		lastActionAtMs = System.currentTimeMillis();
		if (unitId > 0) {
			movingUnits.add(unitId);
		}
	}

	public void onUnitMoving(int unitId) {
		if (unitId <= 0) return;
		inputLocked = true;
		lastActionAtMs = System.currentTimeMillis();
		movingUnits.add(unitId);
	}

	public void onUnitStopped(ActorRef out, int unitId) {
		if (unitId > 0) {
			movingUnits.remove(unitId);
		}
		lastActionAtMs = System.currentTimeMillis();

		if (movingUnits.isEmpty()) {
			inputLocked = false;

			if (!gameOver && pendingFollowUpAttackUnitId != null && pendingFollowUpAttackTargetX != null && pendingFollowUpAttackTargetY != null) {
				if (unitId == pendingFollowUpAttackUnitId.intValue()) {
					BoardCell queuedTarget = getCell(pendingFollowUpAttackTargetX, pendingFollowUpAttackTargetY);
					clearPendingFollowUpAttack();
					if (queuedTarget != null) {
						attackSelectedTarget(out, queuedTarget);
					}
				}
			}
			
			if (pendingEndTurn && !gameOver) {
				pendingEndTurn = false;
				TurnSystem.endTurn(out, this);
			}
		}
	}

	public void onHeartbeat(ActorRef out) {
		if (inputLocked) {
			long now = System.currentTimeMillis();
			if (now - lastActionAtMs > INPUT_LOCK_TIMEOUT_MS) {
				movingUnits.clear();
				inputLocked = false;
			}
		}

		if (!inputLocked && pendingEndTurn && !gameOver) {
			pendingEndTurn = false;
			TurnSystem.endTurn(out, this);
		}
	}

	public int allocateUnitId() {
		return nextUnitId++;
	}

	// ---------------------------------------------------------------------
	// Board helpers extracted from the original GameState.java into
	// BoardService. These wrappers remain here intentionally because
	// GameState.java is coursework-provided source that should stay visible.
	// ---------------------------------------------------------------------

	public void initBoard(ActorRef out) {
		BoardService.initBoard(this, out);
	}

	public boolean isOnBoard(int x, int y) {
		return BoardService.isOnBoard(x, y);
	}

	public BoardCell getCell(int x, int y) {
		return BoardService.getCell(this, x, y);
	}

	public Tile getTile(int x, int y) {
		return BoardService.getTile(this, x, y);
	}

	public List<Tile> getAllTiles() {
		return BoardService.getAllTiles(this);
	}

	public List<BoardCell> getAllCells() {
		return BoardService.getAllCells(this);
	}

	public void setLastClickedCell(BoardCell cell) {
		this.lastClickedCell = cell;
	}

	public BoardCell getLastClickedCell() {
		return lastClickedCell;
	}

	public void clearAllHighlights(ActorRef out) {
		BoardService.clearAllHighlights(this, out);
	}

	public void redrawBoardAsNormal(ActorRef out) {
		BoardService.redrawBoardAsNormal(this, out);
	}

	// ---------------------------------------------------------------------
	// Added: initial units + ownership / turn-state helpers
	// ---------------------------------------------------------------------

	public void placeInitialUnits(ActorRef out) {
		try {
			int playerX = 1, playerY = 2;
			int aiX = BOARD_WIDTH - 1 - playerX, aiY = playerY;

			BoardCell playerCell = getCell(playerX, playerY);
			BoardCell aiCell = getCell(aiX, aiY);

			player1Avatar = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 1, Unit.class);
			player2Avatar = BasicObjectBuilders.loadUnit(StaticConfFiles.aiAvatar, 2, Unit.class);

			// explicitly set avatar stats
			player1Avatar.setAttack(2);
			player1Avatar.setHealth(20);
			player1Avatar.setMaxHealth(20);

			player2Avatar.setAttack(2);
			player2Avatar.setHealth(20);
			player2Avatar.setMaxHealth(20);

			playerCell.trySetOccupant(player1Avatar);
			aiCell.trySetOccupant(player2Avatar);

			player1Avatar.setPositionByTile(playerCell.getTile());
			player2Avatar.setPositionByTile(aiCell.getTile());

			registerUnit(player1Avatar, 1);
			registerUnit(player2Avatar, 2);

			BasicCommands.drawUnit(out, player1Avatar, playerCell.getTile());
			Thread.sleep(50);
			BasicCommands.setUnitHealth(out, player1Avatar, player1Avatar.getHealth());
			BasicCommands.setUnitAttack(out, player1Avatar, player1Avatar.getAttack());
			Thread.sleep(50);

			BasicCommands.drawUnit(out, player2Avatar, aiCell.getTile());
			Thread.sleep(50);
			BasicCommands.setUnitHealth(out, player2Avatar, player2Avatar.getHealth());
			BasicCommands.setUnitAttack(out, player2Avatar, player2Avatar.getAttack());
			Thread.sleep(50);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void registerUnit(Unit unit, int owner) {
		registerUnit(unit, owner, null);
	}

	public void registerUnit(Unit unit, int owner, String cardName) {
		if (unit == null) return;
		unitOwners.put(unit.getId(), owner);
		unitCardNames.put(unit.getId(), normalizeName(cardName));
		movedThisTurn.put(unit.getId(), false);
		attackedThisTurn.put(unit.getId(), false);
	}

	public int getUnitOwner(Unit unit) {
		if (unit == null) return 0;
		return unitOwners.getOrDefault(unit.getId(), 0);
	}

	public String getUnitCardName(Unit unit) {
		if (unit == null) return "";
		return unitCardNames.getOrDefault(unit.getId(), "");
	}

	public boolean isOwnedByCurrentPlayer(Unit unit) {
		return getUnitOwner(unit) == currentPlayer;
	}

	public boolean hasMovedThisTurn(Unit unit) {
		if (unit == null) return false;
		return movedThisTurn.getOrDefault(unit.getId(), false);
	}

	public boolean hasAttackedThisTurn(Unit unit) {
		if (unit == null) return false;
		return attackedThisTurn.getOrDefault(unit.getId(), false);
	}

	public void setMovedThisTurn(Unit unit, boolean value) {
		if (unit == null) return;
		movedThisTurn.put(unit.getId(), value);
	}

	public void setAttackedThisTurn(Unit unit, boolean value) {
		if (unit == null) return;
		attackedThisTurn.put(unit.getId(), value);
	}

	public boolean isUnitStunned(Unit unit) {
		if (unit == null) return false;
		Integer stunTurn = stunnedUntilTurn.get(unit.getId());
		return stunTurn != null && turnNumber <= stunTurn;
	}

	void clearDeathResolutionCache() {
		deathResolvedThisStep.clear();
	}

	public void stunUnitUntilNextTurn(Unit unit) {
		if (unit == null) return;
		stunnedUntilTurn.put(unit.getId(), turnNumber + 1);
		setMovedThisTurn(unit, true);
		setAttackedThisTurn(unit, true);
	}

	public void resetCurrentPlayerUnitsForNewTurn() {
		for (BoardCell cell : allCells) {
			Unit unit = cell.getOccupant();
			if (unit == null) continue;
			if (getUnitOwner(unit) != currentPlayer) continue;

			Integer stunTurn = stunnedUntilTurn.get(unit.getId());
			if (stunTurn != null && turnNumber <= stunTurn) {
				setMovedThisTurn(unit, true);
				setAttackedThisTurn(unit, true);
				if (turnNumber == stunTurn) {
					stunnedUntilTurn.remove(unit.getId());
				}
			} else {
				setMovedThisTurn(unit, false);
				setAttackedThisTurn(unit, false);
			}
		}
	}

	// ---------------------------------------------------------------------
	// Stat helpers
	// ---------------------------------------------------------------------

	void applyCardStatsToUnit(Card card, Unit unit) {
		if (card == null || unit == null || card.getBigCard() == null) return;

		unit.setAttack(card.getBigCard().getAttack());
		unit.setHealth(card.getBigCard().getHealth());
		unit.setMaxHealth(card.getBigCard().getHealth());
	}

	void applyTokenStats(Unit unit, int attack, int health) {
		if (unit == null) return;

		unit.setAttack(attack);
		unit.setHealth(health);
		unit.setMaxHealth(health);
	}

	// ---------------------------------------------------------------------
	// Board helpers
	// ---------------------------------------------------------------------

	public BoardCell getCellForUnit(Unit unit) {
		return BoardService.getCellForUnit(this, unit);
	}

	public List<BoardCell> getAdjacentCells(int x, int y, boolean includeDiagonal) {
		return BoardService.getAdjacentCells(this, x, y, includeDiagonal);
	}

	public boolean areAdjacent(BoardCell a, BoardCell b, boolean includeDiagonal) {
		return BoardService.areAdjacent(a, b, includeDiagonal);
	}

	BoardCell[][] getBoardCells() {
		return cells;
	}

	void setBoardCells(BoardCell[][] cells) {
		this.cells = cells;
	}

	List<Tile> getMutableAllTiles() {
		return allTiles;
	}

	void setAllTiles(List<Tile> allTiles) {
		this.allTiles = allTiles;
	}

	List<BoardCell> getMutableAllCells() {
		return allCells;
	}

	void setAllCells(List<BoardCell> allCells) {
		this.allCells = allCells;
	}

	Highlight[][] getLastHighlightGrid() {
		return lastHighlight;
	}

	List<BoardCell> getSelectedMoveCellsMutable() {
		return selectedMoveCells;
	}

	List<BoardCell> getSelectedAttackCellsMutable() {
		return selectedAttackCells;
	}

	List<BoardCell> getSelectedCardTargetCellsMutable() {
		return selectedCardTargetCells;
	}

	void setSelectedUnitInternal(Unit unit) {
		this.selectedUnit = unit;
	}

	void setSelectedCardInternal(Card card) {
		this.selectedCard = card;
	}

	void setSelectedHandPositionInternal(int handPosition) {
		this.selectedHandPosition = handPosition;
	}

	void markCardSelectionNow() {
		this.lastCardSelectionAtMs = System.currentTimeMillis();
	}

	void setSelectionModeCardSummon() {
		this.selectionMode = SelectionMode.CARD_SUMMON;
	}

	void setSelectionModeCardSpell() {
		this.selectionMode = SelectionMode.CARD_SPELL;
	}

	boolean isSelectionModeCardSummon() {
		return selectionMode == SelectionMode.CARD_SUMMON;
	}

	boolean isSelectionModeCardSpell() {
		return selectionMode == SelectionMode.CARD_SPELL;
	}

	// ---------------------------------------------------------------------
	// Selection clearing / hand redraw
	// ---------------------------------------------------------------------

	public void redrawPlayerHand(ActorRef out, int player) {
		if (player != 1) {
			return;
		}

		redrawHumanHand(out);
	}

	private void redrawHumanHand(ActorRef out) {
		for (int pos = 1; pos <= 6; pos++) {
			BasicCommands.deleteCard(out, pos);
		}

		List<Card> hand = player1Hand;

		for (int i = 0; i < hand.size() && i < 6; i++) {
			int handPosition = i + 1;
			int mode = (selectedCard != null && selectedHandPosition == handPosition) ? 1 : 0;
			BasicCommands.drawCard(out, hand.get(i), handPosition, mode);
		}
	}

	public void highlightHandCard(ActorRef out, int player, int handPosition) {
		if (player != 1) {
			return;
		}

		redrawHumanHand(out);
	}

	public void drawHandCardNormal(ActorRef out, int player, int handPosition) {
		if (player != 1) {
			return;
		}

		redrawHumanHand(out);
	}

	public void clearSelection(ActorRef out) {
		int oldHandPosition = selectedHandPosition;

		selectedUnit = null;
		selectedCard = null;
		selectedHandPosition = -1;
		selectionMode = SelectionMode.NONE;

		selectedMoveCells.clear();
		selectedAttackCells.clear();
		selectedCardTargetCells.clear();
		clearPendingFollowUpAttack();

		clearAllHighlights(out);

		if (currentPlayer == 1 && oldHandPosition >= 1) {
			drawHandCardNormal(out, 1, oldHandPosition);
		}
	}

	// ---------------------------------------------------------------------
	// Unit select / move / attack
	// ---------------------------------------------------------------------

	public void selectUnit(ActorRef out, Unit unit) {
		selectedCard = null;
		selectedHandPosition = -1;
		selectedCardTargetCells.clear();
		selectionMode = SelectionMode.UNIT;

		selectedUnit = unit;
		selectedMoveCells.clear();
		selectedAttackCells.clear();

		if (unit == null) {
			clearSelection(out);
			return;
		}

		BoardCell unitCell = getCellForUnit(unit);
		if (unitCell == null) {
			clearSelection(out);
			return;
		}

		if (!hasMovedThisTurn(unit) && !isUnitStunned(unit)) {
			selectedMoveCells.addAll(getValidMoveCells(unit));
		}

		if (!hasAttackedThisTurn(unit) && !isUnitStunned(unit)) {
			selectedAttackCells.addAll(getValidAttackCells(unit));
		}

		applyUnitSelectionHighlights(out, unitCell);
	}

	private void applyUnitSelectionHighlights(ActorRef out, BoardCell selectedCell) {
		clearAllHighlights(out);

		if (selectedCell != null) {
			selectedCell.setHighlight(Highlight.VALID);
			selectedCell.render(out);
			lastHighlight[selectedCell.getX()][selectedCell.getY()] = Highlight.VALID;
		}

		for (BoardCell cell : selectedMoveCells) {
			cell.setHighlight(Highlight.VALID);
			cell.render(out);
			lastHighlight[cell.getX()][cell.getY()] = Highlight.VALID;
		}

		for (BoardCell cell : selectedAttackCells) {
			cell.setHighlight(Highlight.ATTACK);
			cell.render(out);
			lastHighlight[cell.getX()][cell.getY()] = Highlight.ATTACK;
		}
	}

	public boolean isSelectedMoveCell(BoardCell cell) {
		return selectedMoveCells.contains(cell);
	}

	public boolean isSelectedAttackCell(BoardCell cell) {
		return selectedAttackCells.contains(cell);
	}

	public List<BoardCell> getValidMoveCells(Unit unit) {
		if (unit == null || isProvoked(unit)) {
			return new ArrayList<>();
		}

		if (hasFlying(unit)) {
			List<BoardCell> result = new ArrayList<>();
			BoardCell origin = getCellForUnit(unit);
			if (origin == null) return result;

			int x = origin.getX();
			int y = origin.getY();

			// Allow movement within a 2-grid range (unobstructed)
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -2; dy <= 2; dy++) {
					if (dx == 0 && dy == 0) continue;

					int nx = x + dx;
					int ny = y + dy;

					BoardCell cell = getCell(nx, ny);
					if (cell != null && cell.isEmpty()) {
						result.add(cell);
					}
				}
			}

			return result;
		}

		List<BoardCell> result = new ArrayList<>();
		BoardCell origin = getCellForUnit(unit);
		if (origin == null) return result;

		Set<BoardCell> unique = new LinkedHashSet<>();

		int x = origin.getX();
		int y = origin.getY();

		addIfEmpty(unique, x + 1, y);
		addIfEmpty(unique, x - 1, y);
		addIfEmpty(unique, x, y + 1);
		addIfEmpty(unique, x, y - 1);

		addIfTwoStepClear(unique, x, y, 1, 0);
		addIfTwoStepClear(unique, x, y, -1, 0);
		addIfTwoStepClear(unique, x, y, 0, 1);
		addIfTwoStepClear(unique, x, y, 0, -1);

		addIfEmpty(unique, x + 1, y + 1);
		addIfEmpty(unique, x + 1, y - 1);
		addIfEmpty(unique, x - 1, y + 1);
		addIfEmpty(unique, x - 1, y - 1);

		result.addAll(unique);
		return result;
	}

	private void addIfEmpty(Set<BoardCell> result, int x, int y) {
		BoardCell c = getCell(x, y);
		if (c != null && c.isEmpty()) result.add(c);
	}

	private void addIfTwoStepClear(Set<BoardCell> result, int x, int y, int dx, int dy) {
		BoardCell first = getCell(x + dx, y + dy);
		BoardCell second = getCell(x + 2 * dx, y + 2 * dy);
		if (first != null && second != null && first.isEmpty() && second.isEmpty()) {
			result.add(second);
		}
	}

	public List<BoardCell> getValidAttackCells(Unit unit) {
		List<BoardCell> result = new ArrayList<>();
		BoardCell origin = getCellForUnit(unit);
		if (origin == null) return result;

		List<BoardCell> provokes = new ArrayList<>();
		for (BoardCell cell : getAdjacentCells(origin.getX(), origin.getY(), true)) {
			if (cell.isOccupied() && getUnitOwner(cell.getOccupant()) != getUnitOwner(unit)) {
				if (hasProvoke(cell.getOccupant())) {
					provokes.add(cell);
				} else {
					result.add(cell);
				}
			}
		}
		return provokes.isEmpty() ? result : provokes;
	}

	public boolean moveSelectedUnitTo(ActorRef out, BoardCell destination) {
		if (selectedUnit == null || destination == null) return false;
		if (!isSelectedMoveCell(destination)) return false;

		BoardCell origin = getCellForUnit(selectedUnit);
		if (origin == null || !destination.isEmpty()) return false;

		origin.clearOccupant();
		destination.trySetOccupant(selectedUnit);
		selectedUnit.setPositionByTile(destination.getTile());

		lockInputForUnit(selectedUnit.getId());
		BasicCommands.moveUnitToTile(out, selectedUnit, destination.getTile());
		setMovedThisTurn(selectedUnit, true);

		selectedMoveCells.clear();
		selectedAttackCells.clear();

		if (!hasAttackedThisTurn(selectedUnit)) {
			selectedAttackCells.addAll(getValidAttackCells(selectedUnit));
		}

		if (selectedAttackCells.isEmpty()) {
			clearSelection(out);
		} else {
			BoardCell newCell = getCellForUnit(selectedUnit);
			applyUnitSelectionHighlights(out, newCell);
		}

		return true;
	}

	public boolean attackSelectedTarget(ActorRef out, BoardCell targetCell) {
		return CombatResolutionService.attackSelectedTarget(this, out, targetCell);
	}

	public boolean tryMoveThenAttackSelectedTarget(ActorRef out, BoardCell targetCell) {
		if (selectedUnit == null || targetCell == null || !targetCell.isOccupied()) return false;
		if (hasMovedThisTurn(selectedUnit) || hasAttackedThisTurn(selectedUnit)) return false;

		BoardCell destination = chooseFollowUpAttackCell(targetCell);
		if (destination == null) return false;

		pendingFollowUpAttackUnitId = selectedUnit.getId();
		pendingFollowUpAttackTargetX = targetCell.getX();
		pendingFollowUpAttackTargetY = targetCell.getY();

		return moveSelectedUnitTo(out, destination);
	}

	private BoardCell chooseFollowUpAttackCell(BoardCell targetCell) {
		BoardCell origin = getCellForUnit(selectedUnit);
		if (origin == null) return null;

		BoardCell bestCell = null;
		int bestDistance = Integer.MAX_VALUE;

		for (BoardCell candidate : selectedMoveCells) {
			if (!areAdjacent(candidate, targetCell, true)) continue;

			int distanceFromOrigin = Math.abs(candidate.getX() - origin.getX()) + Math.abs(candidate.getY() - origin.getY());
			if (bestCell == null
					|| distanceFromOrigin < bestDistance
					|| (distanceFromOrigin == bestDistance && candidate.getY() < bestCell.getY())
					|| (distanceFromOrigin == bestDistance && candidate.getY() == bestCell.getY() && candidate.getX() < bestCell.getX())) {
				bestCell = candidate;
				bestDistance = distanceFromOrigin;
			}
		}

		return bestCell;
	}

	private void clearPendingFollowUpAttack() {
		pendingFollowUpAttackUnitId = null;
		pendingFollowUpAttackTargetX = null;
		pendingFollowUpAttackTargetY = null;
	}

	// ---------------------------------------------------------------------
	// Card-resolution helpers extracted from the original GameState.java into
	// CardResolutionService. These wrappers stay here intentionally so the
	// coursework-provided GameState.java still shows the refactoring path.
	// ---------------------------------------------------------------------

	public void selectCard(ActorRef out, Card card, int handPosition) {
		CardResolutionService.selectCard(this, out, card, handPosition);
	}

	private boolean isHelpfulSpell(Card card) {
		String name = normalizeCardName(card);
		return name.equals("sundrop elixir") || name.equals("wraithling swarm") || name.equals("horn of the forsaken");
	}

	private void applyCardTargetHighlights(ActorRef out, Highlight highlightMode) {
		clearAllHighlights(out);

		for (BoardCell cell : selectedCardTargetCells) {
			cell.setHighlight(highlightMode);
			cell.render(out);
			lastHighlight[cell.getX()][cell.getY()] = highlightMode;
		}
	}

	public boolean tryResolveCardActionAt(ActorRef out, BoardCell cell) {
		return CardResolutionService.tryResolveCardActionAt(this, out, cell);
	}

	public List<BoardCell> getValidSummonCellsForCurrentPlayer() {
		return CardResolutionService.getValidSummonCellsForCurrentPlayer(this);
	}

	public List<BoardCell> getValidSummonCellsForCurrentPlayer(Card card) {
		return CardResolutionService.getValidSummonCellsForCurrentPlayer(this, card);
	}

	private List<BoardCell> getSpellTargetCells(Card card) {
		return CardResolutionService.getSpellTargetCellsForAI(this, card);
	}

	private boolean summonCreatureCard(ActorRef out, Card card, BoardCell targetCell) {
		return CardResolutionService.summonCreatureCard(this, out, card, targetCell);
	}

	void playSummonAnimation(ActorRef out, BoardCell targetCell) {
		if (out == null || targetCell == null) {
			return;
		}

		try {
			EffectAnimation summonEffect = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon);
			if (summonEffect == null) {
				return;
			}

			int waitMs = BasicCommands.playEffectAnimation(out, summonEffect, targetCell.getTile());
			Thread.sleep(Math.max(waitMs, 150));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean castSpellCard(ActorRef out, Card card, BoardCell targetCell) {
		return CardResolutionService.castSpellCard(this, out, card, targetCell);
	}

	String normalizeCardName(Card card) {
		return normalizeName(card == null ? null : card.getCardname());
	}

	private List<BoardCell> getSummonCellsNearTarget(BoardCell targetCell, int maxCount) {
		List<BoardCell> result = new ArrayList<>();
		if (targetCell == null || maxCount <= 0) {
			return result;
		}

		List<BoardCell> validCells = getValidSummonCellsForCurrentPlayer();
		if (!validCells.contains(targetCell)) {
			return result;
		}

		result.add(targetCell);

		for (BoardCell cell : getAdjacentCells(targetCell.getX(), targetCell.getY(), true)) {
			if (result.size() >= maxCount) {
				break;
			}
			if (validCells.contains(cell) && !result.contains(cell)) {
				result.add(cell);
			}
		}

		if (result.size() < maxCount) {
			for (BoardCell cell : validCells) {
				if (result.size() >= maxCount) {
					break;
				}
				if (!result.contains(cell)) {
					result.add(cell);
				}
			}
		}

		return result;
	}

	void playSpellEffect(ActorRef out, BoardCell targetCell, String impactEffectConf) {
		if (targetCell == null || impactEffectConf == null || impactEffectConf.isEmpty()) {
			return;
		}

		Unit casterAvatar = (currentPlayer == 1) ? player1Avatar : player2Avatar;
		BoardCell casterCell = getCellForUnit(casterAvatar);

		try {
			if (casterCell != null) {
				EffectAnimation projectile = BasicObjectBuilders.loadEffect(StaticConfFiles.f1_projectiles);
				if (projectile != null) {
					BasicCommands.playProjectileAnimation(out, projectile, 0, casterCell.getTile(), targetCell.getTile());
					Thread.sleep(180);
				}
			}

			EffectAnimation impact = BasicObjectBuilders.loadEffect(impactEffectConf);
			if (impact != null) {
				int waitMs = BasicCommands.playEffectAnimation(out, impact, targetCell.getTile());
				Thread.sleep(Math.min(waitMs, 500));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String normalizeName(String name) {
		return name == null ? "" : name.trim().toLowerCase();
	}

	void removeCardFromCurrentHand(ActorRef out, int handPosition) {
		List<Card> hand = getCurrentPlayerHand();
		int index = handPosition - 1;

		if (index < 0 || index >= hand.size()) return;

		hand.remove(index);

		if (currentPlayer == 1) {
			redrawHumanHand(out);
		}
	}

	void spendMana(ActorRef out, int player, int amount) {
		Player p = (player == 1) ? player1 : player2;
		p.setMana(Math.max(0, p.getMana() - amount));

		if (player == 1) {
			BasicCommands.setPlayer1Mana(out, p);
		} else {
			BasicCommands.setPlayer2Mana(out, p);
		}
	}

	// ---------------------------------------------------------------------
	// Combat / damage / death logic extracted from the original GameState.java
	// into CombatResolutionService. These wrappers stay here intentionally so
	// the coursework-provided state file still shows the combat entry points.
	// ---------------------------------------------------------------------

	void applyDamageToUnit(ActorRef out, Unit unit, int amount) {
		CombatResolutionService.applyDamageToUnit(this, out, unit, amount);
	}

	void syncAvatarHealthIfNeeded(ActorRef out, Unit unit) {
		CombatResolutionService.syncAvatarHealthIfNeeded(this, out, unit);
	}

	public boolean handleUnitDeathIfNeeded(ActorRef out, Unit unit) {
		return CombatResolutionService.handleUnitDeathIfNeeded(this, out, unit);
	}

	boolean isDeathAlreadyResolved(Unit unit) {
		return unit != null && deathResolvedThisStep.contains(unit.getId());
	}

	void markDeathResolved(Unit unit) {
		if (unit != null) {
			deathResolvedThisStep.add(unit.getId());
		}
	}

	void removeUnitStateFromService(Unit unit) {
		removeUnitState(unit);
	}

	void triggerDeathwatchEffectsFromService(ActorRef out, Unit deadUnit) {
		triggerDeathwatchEffects(out, deadUnit);
	}

	void summonHornWraithling(ActorRef out, int owner) {
		Unit avatar = owner == 1 ? player1Avatar : player2Avatar;
		summonRandomAdjacentWraithlingToAvatar(out, avatar, owner);
	}

	private void removeUnitState(Unit unit) {
		if (unit == null) return;

		int id = unit.getId();

		unitOwners.remove(id);
		unitCardNames.remove(id);
		movedThisTurn.remove(id);
		attackedThisTurn.remove(id);
		stunnedUntilTurn.remove(id);
	}

	private void triggerDeathwatchEffects(ActorRef out, Unit deadUnit) {
		for (BoardCell cell : allCells) {
			if (!cell.isOccupied()) continue;

			Unit watcher = cell.getOccupant();
			if (watcher == deadUnit) continue;

			String name = getUnitCardName(watcher);

			// Bad Omen: whenever ANY unit dies, gain +1 attack
			if (name.equals("bad omen")) {
				watcher.setAttack(watcher.getAttack() + 1);
				BasicCommands.setUnitAttack(out, watcher, watcher.getAttack());
			}

			// Shadow Watcher: whenever ANY unit dies, gain +1 attack and +1 health
			else if (name.equals("shadow watcher")) {
				watcher.setAttack(watcher.getAttack() + 1);
				watcher.setHealth(watcher.getHealth() + 1);
				watcher.setMaxHealth(watcher.getMaxHealth() + 1);

				BasicCommands.setUnitAttack(out, watcher, watcher.getAttack());
				BasicCommands.setUnitHealth(out, watcher, watcher.getHealth());
			}

			// Bloodmoon Priestess: whenever ANY unit dies, summon one Wraithling nearby
			else if (name.equals("bloodmoon priestess")) {
				summonRandomWraithling(out, watcher);
			}

			// Shadowdancer: whenever ANY unit dies, deal 1 damage to enemy avatar and heal self by 1
			else if (name.equals("shadowdancer")) {
				Unit enemyAvatar = (getUnitOwner(watcher) == 1) ? player2Avatar : player1Avatar;

				// damage enemy avatar
				enemyAvatar.setHealth(enemyAvatar.getHealth() - 1);
				BasicCommands.setUnitHealth(out, enemyAvatar, enemyAvatar.getHealth());
				syncAvatarHealthIfNeeded(out, enemyAvatar);

				// heal self (not above max health)
				int newHealth = Math.min(watcher.getMaxHealth(), watcher.getHealth() + 1);
				watcher.setHealth(newHealth);
				BasicCommands.setUnitHealth(out, watcher, watcher.getHealth());
			}
		}
	}

	private boolean hasProvoke(Unit unit) {
		if (unit == null) return false;
		if (unit.isProvoke()) return true;
		String name = getUnitCardName(unit);
		return name.equals("rock pulveriser")
				|| name.equals("swamp entangler")
				|| name.equals("silverguard knight")
				|| name.equals("ironcliff guardian");
	}

	private boolean hasFlying(Unit unit) {
		return normalizeCardName(unit == null ? null : unit.getCard()).equals("young flamewing");
	}

	boolean hasRush(Card card) {
		return normalizeCardName(card).equals("saberspine tiger");
	}

	boolean hasAirdrop(Card card) {
		return normalizeCardName(card).equals("ironcliff guardian");
	}

	// ---------------------------------------------------------------------
	// AI player
	// ---------------------------------------------------------------------

	public List<BoardCell> getSpellTargetCellsForAI(Card card) {
		return CardResolutionService.getSpellTargetCellsForAI(this, card);
	}

	public boolean playCardForAI(ActorRef out, Card card, BoardCell targetCell) {
		return CardResolutionService.playCardForAI(this, out, card, targetCell);
	}

	private void announceAICardPlay(ActorRef out, Card card, BoardCell targetCell) {
		if (currentPlayer != 2 || card == null) {
			return;
		}

		String cardName = card.getCardname();
		String targetDescription = describeTarget(targetCell);
		String message;

		if (card.getIsCreature()) {
			message = "AI summoned " + cardName + formatTargetSuffix(targetDescription);
		} else {
			message = "AI cast " + cardName + formatTargetSuffix(targetDescription);
		}

		System.out.println("[AI] " + message);
		BasicCommands.addPlayer1Notification(out, message, 2);
	}

	String describeTarget(BoardCell targetCell) {
		if (targetCell == null) {
			return "";
		}

		if (targetCell.isOccupied()) {
			Unit target = targetCell.getOccupant();
			if (target == player1Avatar) return "your avatar";
			if (target == player2Avatar) return "its avatar";

			String name = unitCardNames.get(target.getId());
			if (name == null || name.trim().isEmpty()) {
				return "a unit";
			}
			return name;
		}

		return "tile (" + targetCell.getX() + "," + targetCell.getY() + ")";
	}

	String formatTargetSuffix(String targetDescription) {
		if (targetDescription == null || targetDescription.isEmpty()) {
			return "";
		}
		return " on " + targetDescription;
	}

	// ---------------------------------------------------------------------
	// Triggered effect helpers extracted from the original GameState.java into
	// TriggeredEffectService. These wrappers stay here intentionally so the
	// coursework-provided GameState.java still shows how triggered abilities
	// enter the rules flow.
	// ---------------------------------------------------------------------
	private void summonRandomWraithling(ActorRef out, Unit priestess) {
		TriggeredEffectService.summonRandomWraithling(this, out, priestess);
	}

	void triggerOpeningGambit(ActorRef out, Unit unit) {
		TriggeredEffectService.triggerOpeningGambit(this, out, unit);
	}

	private void summonWraithlingBehind(ActorRef out, Unit unit) {
		TriggeredEffectService.summonWraithlingBehind(this, out, unit);
	}

	private void destroyDamagedAdjacentEnemy(ActorRef out, Unit assassin) {
		TriggeredEffectService.destroyDamagedAdjacentEnemy(this, out, assassin);
	}

	public boolean isProvoked(Unit unit) {
		BoardCell cell = getCellForUnit(unit);
		if (cell == null) return false;
		int owner = getUnitOwner(unit);
		for (BoardCell neighbor : getAdjacentCells(cell.getX(), cell.getY(), true)) {
			if (!neighbor.isOccupied()) continue;
			Unit other = neighbor.getOccupant();
			if (getUnitOwner(other) != owner && hasProvoke(other)) {
				return true;
			}
		}
		return false;
	}

	private void summonRandomAdjacentWraithlingToAvatar(ActorRef out, Unit avatar, int owner) {
		TriggeredEffectService.summonRandomAdjacentWraithlingToAvatar(this, out, avatar, owner);
	}

	private void buffAdjacentToAvatar(ActorRef out, Unit squire, int amount) {
		TriggeredEffectService.buffAdjacentToAvatar(this, out, squire, amount);
	}

}
