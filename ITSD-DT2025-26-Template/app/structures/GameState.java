package structures;

import java.util.ArrayList;
import java.util.Collections;
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
import structures.basic.Ability;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;



/**
 * Holds the ongoing game state (server-side).
 * Board rules: 9 columns x 5 rows (0-based indices: x=0..8, y=0..4).
 */
public class GameState {

	public boolean gameInitalised = false;
	public boolean something = false;

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
	// Added: board init / access helpers
	// ---------------------------------------------------------------------

	public void initBoard(ActorRef out) {
		if (cells != null) return;

		cells = new BoardCell[BOARD_WIDTH][BOARD_HEIGHT];
		allCells = new ArrayList<>(BOARD_WIDTH * BOARD_HEIGHT);
		allTiles = new ArrayList<>(BOARD_WIDTH * BOARD_HEIGHT);

		for (int y = 0; y < BOARD_HEIGHT; y++) {
			for (int x = 0; x < BOARD_WIDTH; x++) {
				Tile tile = BasicObjectBuilders.loadTile(x, y);
				BoardCell cell = new BoardCell(x, y, tile);

				cells[x][y] = cell;
				allCells.add(cell);
				allTiles.add(tile);

				lastHighlight[x][y] = Highlight.NONE;
				BasicCommands.drawTile(out, tile, 0);
			}
		}
	}

	public boolean isOnBoard(int x, int y) {
		return x >= 0 && x < BOARD_WIDTH && y >= 0 && y < BOARD_HEIGHT;
	}

	public BoardCell getCell(int x, int y) {
		if (cells == null || !isOnBoard(x, y)) return null;
		return cells[x][y];
	}

	public Tile getTile(int x, int y) {
		if (cells == null || !isOnBoard(x, y)) return null;
		return cells[x][y].getTile();
	}

	public List<Tile> getAllTiles() {
		return Collections.unmodifiableList(allTiles);
	}

	public List<BoardCell> getAllCells() {
		return Collections.unmodifiableList(allCells);
	}

	public void setLastClickedCell(BoardCell cell) {
		this.lastClickedCell = cell;
	}

	public BoardCell getLastClickedCell() {
		return lastClickedCell;
	}

	public void clearAllHighlights(ActorRef out) {
		if (cells == null) return;
		for (int y = 0; y < BOARD_HEIGHT; y++) {
			for (int x = 0; x < BOARD_WIDTH; x++) {
				if (lastHighlight[x][y] != Highlight.NONE) {
					BoardCell c = cells[x][y];
					c.setHighlight(Highlight.NONE);
					c.render(out);
					lastHighlight[x][y] = Highlight.NONE;
				}
			}
		}
	}

	public void redrawBoardAsNormal(ActorRef out) {
		if (cells == null) return;
		for (BoardCell cell : allCells) {
			cell.setHighlight(Highlight.NONE);
			cell.render(out);
			lastHighlight[cell.getX()][cell.getY()] = Highlight.NONE;
		}
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

	private void clearDeathResolutionCache() {
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

	private void applyCardStatsToUnit(Card card, Unit unit) {
		if (card == null || unit == null || card.getBigCard() == null) return;

		unit.setAttack(card.getBigCard().getAttack());
		unit.setHealth(card.getBigCard().getHealth());
		unit.setMaxHealth(card.getBigCard().getHealth());
	}

	private void applyTokenStats(Unit unit, int attack, int health) {
		if (unit == null) return;

		unit.setAttack(attack);
		unit.setHealth(health);
		unit.setMaxHealth(health);
	}

	// ---------------------------------------------------------------------
	// Board helpers
	// ---------------------------------------------------------------------

	public BoardCell getCellForUnit(Unit unit) {
		if (unit == null) return null;
		for (BoardCell cell : allCells) {
			if (cell.getOccupant() != null && cell.getOccupant().getId() == unit.getId()) {
				return cell;
			}
		}
		return null;
	}

	public List<BoardCell> getAdjacentCells(int x, int y, boolean includeDiagonal) {
		List<BoardCell> result = new ArrayList<>();
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dy == 0) continue;
				if (!includeDiagonal && Math.abs(dx) + Math.abs(dy) != 1) continue;

				int nx = x + dx;
				int ny = y + dy;
				BoardCell n = getCell(nx, ny);
				if (n != null) result.add(n);
			}
		}
		return result;
	}

	public boolean areAdjacent(BoardCell a, BoardCell b, boolean includeDiagonal) {
		if (a == null || b == null) return false;
		int dx = Math.abs(a.getX() - b.getX());
		int dy = Math.abs(a.getY() - b.getY());
		if (includeDiagonal) {
			return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
		}
		return dx + dy == 1;
	}

	// ---------------------------------------------------------------------
	// Selection clearing / hand redraw
	// ---------------------------------------------------------------------

	public void redrawPlayerHand(ActorRef out, int player) {
		if (player != 1) {
			return;
		}

		for (int pos = 1; pos <= 6; pos++) {
			BasicCommands.deleteCard(out, pos);
		}

		List<Card> hand = (player == 1) ? player1Hand : player2Hand;
		int mode = player - 1;

		for (int i = 0; i < hand.size() && i < 6; i++) {
			BasicCommands.drawCard(out, hand.get(i), i + 1, mode);
		}
	}

	public void highlightHandCard(ActorRef out, int player, int handPosition) {
		if (player != 1) {
			return;
		}

		List<Card> hand = (player == 1) ? player1Hand : player2Hand;
		int index = handPosition - 1;
		if (index >= 0 && index < hand.size()) {
			BasicCommands.drawCard(out, hand.get(index), handPosition, 1);
		}
	}

	public void drawHandCardNormal(ActorRef out, int player, int handPosition) {
		if (player != 1) {
			return;
		}

		List<Card> hand = (player == 1) ? player1Hand : player2Hand;
		int index = handPosition - 1;
		if (index >= 0 && index < hand.size()) {
			BasicCommands.drawCard(out, hand.get(index), handPosition, 0);
		}
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
		if (selectedUnit == null || targetCell == null || !targetCell.isOccupied()) return false;
		if (!isSelectedAttackCell(targetCell)) return false;

		Unit attacker = selectedUnit;
		Unit defender = targetCell.getOccupant();

		BoardCell attackerCell = getCellForUnit(attacker);
		if (attackerCell == null) return false;

		try {
			BasicCommands.playUnitAnimation(out, attacker, structures.basic.UnitAnimationType.attack);
			Thread.sleep(200);
		} catch (Exception e) {
			e.printStackTrace();
		}

		applyDamageToUnit(out, defender, attacker.getAttack());

		// Horn trigger: avatar dealt damage
		if (attacker == player1Avatar && hasHorn(1)) {
			summonRandomAdjacentWraithlingToAvatar(out, player1Avatar, 1);
		}

		if (attacker == player2Avatar && hasHorn(2)) {
			summonRandomAdjacentWraithlingToAvatar(out, player2Avatar, 2);
		}

		setAttackedThisTurn(attacker, true);

		boolean defenderDied = handleUnitDeathIfNeeded(out, defender);

		if (!defenderDied) {
			BoardCell defenderCellNow = getCellForUnit(defender);
			BoardCell attackerCellNow = getCellForUnit(attacker);

			if (defenderCellNow != null && attackerCellNow != null && areAdjacent(defenderCellNow, attackerCellNow, true)) {
				try {
					BasicCommands.playUnitAnimation(out, defender, structures.basic.UnitAnimationType.attack);
					Thread.sleep(200);
				} catch (Exception e) {
					e.printStackTrace();
				}

				applyDamageToUnit(out, attacker, defender.getAttack());
				handleUnitDeathIfNeeded(out, attacker);
			}
		}

		clearSelection(out);
		clearDeathResolutionCache();
		return true;
	}

	// ---------------------------------------------------------------------
	// Move-then-attack helper
	// ---------------------------------------------------------------------

	public boolean tryMoveThenAttackSelectedTarget(ActorRef out, BoardCell targetCell) {
		if (selectedUnit == null || targetCell == null || !targetCell.isOccupied()) return false;
		if (hasMovedThisTurn(selectedUnit) || hasAttackedThisTurn(selectedUnit)) return false;
		if (isProvoked(selectedUnit) && !hasProvoke(targetCell.getOccupant())) return false;

		BoardCell moveCell = findMoveCellThatCanAttack(selectedUnit, targetCell);
		if (moveCell == null) return false;

		queueFollowUpAttack(selectedUnit, targetCell);
		boolean moved = moveSelectedUnitTo(out, moveCell);
		if (!moved) {
			clearPendingFollowUpAttack();
		}
		return moved;
	}

	private BoardCell findMoveCellThatCanAttack(Unit unit, BoardCell targetCell) {
		if (unit == null || targetCell == null || !targetCell.isOccupied()) return null;

		BoardCell origin = getCellForUnit(unit);
		if (origin == null) return null;

		BoardCell best = null;
		int bestScore = Integer.MIN_VALUE;

		for (BoardCell candidate : getValidMoveCells(unit)) {
			if (!areAdjacent(candidate, targetCell, true)) continue;

			int score = 0;
			int deltaToTarget = Math.abs(candidate.getX() - targetCell.getX()) + Math.abs(candidate.getY() - targetCell.getY());
			int deltaFromOrigin = Math.abs(candidate.getX() - origin.getX()) + Math.abs(candidate.getY() - origin.getY());
			score -= deltaToTarget * 10;
			score -= deltaFromOrigin;

			if (best == null || score > bestScore) {
				best = candidate;
				bestScore = score;
			}
		}

		return best;
	}

	private void queueFollowUpAttack(Unit attacker, BoardCell targetCell) {
		if (attacker == null || targetCell == null) return;
		pendingFollowUpAttackUnitId = attacker.getId();
		pendingFollowUpAttackTargetX = targetCell.getX();
		pendingFollowUpAttackTargetY = targetCell.getY();
	}

	private void clearPendingFollowUpAttack() {
		pendingFollowUpAttackUnitId = null;
		pendingFollowUpAttackTargetX = null;
		pendingFollowUpAttackTargetY = null;
	}

	// ---------------------------------------------------------------------
	// Card select / summon / spell
	// ---------------------------------------------------------------------

	public void selectCard(ActorRef out, Card card, int handPosition) {
		int previousHandPosition = selectedHandPosition;

		selectedUnit = null;
		selectedMoveCells.clear();
		selectedAttackCells.clear();

		selectedCard = card;
		selectedHandPosition = handPosition;
		lastCardSelectionAtMs = System.currentTimeMillis();
		selectedCardTargetCells.clear();

		if (card == null) {
			clearSelection(out);
			return;
		}

		if (currentPlayer == 1 && previousHandPosition >= 1 && previousHandPosition != handPosition) {
			drawHandCardNormal(out, 1, previousHandPosition);
		}

		if (card.getIsCreature()) {
			selectionMode = SelectionMode.CARD_SUMMON;
			selectedCardTargetCells.addAll(getValidSummonCellsForCurrentPlayer());
			applyCardTargetHighlights(out, Highlight.VALID);
		} else {
			selectionMode = SelectionMode.CARD_SPELL;
			selectedCardTargetCells.addAll(getSpellTargetCells(card));
			Highlight mode = isHelpfulSpell(card) ? Highlight.VALID : Highlight.ATTACK;
			applyCardTargetHighlights(out, mode);
		}

		highlightHandCard(out, currentPlayer, handPosition);
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
		if (selectedCard == null || cell == null) return false;
		if (!selectedCardTargetCells.contains(cell)) return false;

		if (selectionMode == SelectionMode.CARD_SUMMON) {
			return summonCreatureCard(out, selectedCard, cell);
		} else if (selectionMode == SelectionMode.CARD_SPELL) {
			return castSpellCard(out, selectedCard, cell);
		}
		return false;
	}

	public List<BoardCell> getValidSummonCellsForCurrentPlayer() {
		Set<BoardCell> result = new LinkedHashSet<>();

		for (BoardCell cell : allCells) {
			if (!cell.isOccupied()) continue;
			Unit unit = cell.getOccupant();
			if (getUnitOwner(unit) != currentPlayer) continue;

			for (BoardCell adj : getAdjacentCells(cell.getX(), cell.getY(), true)) {
				if (adj.isEmpty()) result.add(adj);
			}
		}

		return new ArrayList<>(result);
	}

	private List<BoardCell> getSpellTargetCells(Card card) {
		List<BoardCell> result = new ArrayList<>();
		String name = normalizeCardName(card);

		if (name.equals("wraithling swarm")) {
			result.addAll(getValidSummonCellsForCurrentPlayer());
			return result;
		}

		if (name.equals("horn of the forsaken")) {
			// no target needed, use avatar tile as a dummy selectable tile
			Unit avatar = (currentPlayer == 1) ? player1Avatar : player2Avatar;
			BoardCell avatarCell = getCellForUnit(avatar);
			if (avatarCell != null) {
				result.add(avatarCell);
			}
			return result;
		}

		for (BoardCell cell : allCells) {
			if (!cell.isOccupied()) continue;

			Unit target = cell.getOccupant();
			int owner = getUnitOwner(target);

			if (name.equals("truestrike")) {
				if (owner != currentPlayer) result.add(cell);
			} else if (name.equals("beamshock")) {
				if (owner != currentPlayer && target != player1Avatar && target != player2Avatar) {
					result.add(cell);
				}
			} else if (name.equals("dark terminus")) {
				if (owner != currentPlayer && target != player1Avatar && target != player2Avatar) {
					result.add(cell);
				}
			} else if (name.equals("sundrop elixir")) {
				if (owner == currentPlayer) result.add(cell);
			}
		}

		return result;
	}

	private boolean summonCreatureCard(ActorRef out, Card card, BoardCell targetCell) {
		if (targetCell == null || !targetCell.isEmpty()) return false;

		Player player = getCurrentPlayerObject();
		if (player.getMana() < card.getManacost()) return false;

		try {
			Unit unit = BasicObjectBuilders.loadUnit(card.getUnitConfig(), allocateUnitId(), Unit.class);
			unit.setCard(card);
			applyCardStatsToUnit(card, unit);

			if (card.getCardname().equals("Rock Pulveriser") ||
					card.getCardname().equals("Swamp Entangler") ||
					card.getCardname().equals("Silverguard Knight") ||
					card.getCardname().equals("Ironcliffe Guardian")) {

				unit.setProvoke(true);
			}

			targetCell.trySetOccupant(unit);
			unit.setPositionByTile(targetCell.getTile());
			registerUnit(unit, currentPlayer, card.getCardname());

			// Draw the summoned unit first
			BasicCommands.drawUnit(out, unit, targetCell.getTile());
			Thread.sleep(50);
			BasicCommands.setUnitHealth(out, unit, unit.getHealth());
			BasicCommands.setUnitAttack(out, unit, unit.getAttack());
			Thread.sleep(50);

			// Then trigger Opening Gambit effects
			triggerOpeningGambit(out, unit);

			if (!hasRush(card)) {
				setMovedThisTurn(unit, true);
				setAttackedThisTurn(unit, true);
			}

			spendMana(out, currentPlayer, card.getManacost());
			removeCardFromCurrentHand(out, selectedHandPosition);

			clearSelection(out);
			clearDeathResolutionCache();
			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private boolean castSpellCard(ActorRef out, Card card, BoardCell targetCell) {
		Player player = getCurrentPlayerObject();
		if (player.getMana() < card.getManacost()) return false;

		String name = normalizeCardName(card);

		try {
			if (name.equals("truestrike")) {
				if (!targetCell.isOccupied()) return false;
				playSpellEffect(out, targetCell, StaticConfFiles.f1_soulshatter);
				applyDamageToUnit(out, targetCell.getOccupant(), 2);
				handleUnitDeathIfNeeded(out, targetCell.getOccupant());
			} else if (name.equals("beamshock")) {
				if (!targetCell.isOccupied()) return false;

				Unit target = targetCell.getOccupant();

				if (target == player1Avatar || target == player2Avatar) return false;

				playSpellEffect(out, targetCell, StaticConfFiles.f1_buff);
				stunUnitUntilNextTurn(target);

				BasicCommands.addPlayer1Notification(out, "Beamshock: target stunned", 2);
			} else if (name.equals("sundrop elixir")) {
				if (!targetCell.isOccupied()) return false;
				Unit target = targetCell.getOccupant();
				int newHealth = Math.min(target.getMaxHealth(), target.getHealth() + 4);
				target.setHealth(newHealth);
				BasicCommands.setUnitHealth(out, target, target.getHealth());
				syncAvatarHealthIfNeeded(out, target);
			} else if (name.equals("dark terminus")) {
				if (!targetCell.isOccupied()) return false;

				Unit victim = targetCell.getOccupant();

				if (getUnitOwner(victim) == currentPlayer) return false;
				if (victim == player1Avatar || victim == player2Avatar) return false;

				victim.setHealth(0);
				handleUnitDeathIfNeeded(out, victim);

				Unit token = BasicObjectBuilders.loadUnit(
						StaticConfFiles.wraithling,
						allocateUnitId(),
						Unit.class
				);

				applyTokenStats(token, 1, 1);

				targetCell.trySetOccupant(token);
				token.setPositionByTile(targetCell.getTile());
				registerUnit(token, currentPlayer);

				BasicCommands.drawUnit(out, token, targetCell.getTile());
				Thread.sleep(50);
				BasicCommands.setUnitHealth(out, token, token.getHealth());
				BasicCommands.setUnitAttack(out, token, token.getAttack());

				setMovedThisTurn(token, true);
				setAttackedThisTurn(token, true);
			} else if (name.equals("wraithling swarm")) {

				List<BoardCell> summonCells = getSummonCellsNearTarget(targetCell, 3);
				for (BoardCell cell : summonCells) {
					Unit token = BasicObjectBuilders.loadUnit(
							StaticConfFiles.wraithling,
							allocateUnitId(),
							Unit.class
					);

					applyTokenStats(token, 1, 1);

					cell.trySetOccupant(token);
					token.setPositionByTile(cell.getTile());
					registerUnit(token, currentPlayer, "Wraithling");

					BasicCommands.drawUnit(out, token, cell.getTile());
					Thread.sleep(50);
					BasicCommands.setUnitHealth(out, token, token.getHealth());
					BasicCommands.setUnitAttack(out, token, token.getAttack());

					setMovedThisTurn(token, true);
					setAttackedThisTurn(token, true);
				}
			} else if (name.equals("horn of the forsaken")) {

				equipHorn(currentPlayer);

				if (currentPlayer == 1) {
					BasicCommands.addPlayer1Notification(out, "Player 1 equipped Horn (3 durability)", 2);
				} else {
					BasicCommands.addPlayer1Notification(out, "Player 2 equipped Horn (3 durability)", 2);
				}

			} else {
				return false;
			}

			spendMana(out, currentPlayer, card.getManacost());
			removeCardFromCurrentHand(out, selectedHandPosition);
			clearSelection(out);
			clearDeathResolutionCache();
			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private String normalizeCardName(Card card) {
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

	private void playSpellEffect(ActorRef out, BoardCell targetCell, String impactEffectConf) {
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

	private void removeCardFromCurrentHand(ActorRef out, int handPosition) {
		List<Card> hand = getCurrentPlayerHand();
		int index = handPosition - 1;

		if (index < 0 || index >= hand.size()) return;

		hand.remove(index);

		for (int i = index; i < hand.size(); i++) {
			BasicCommands.drawCard(out, hand.get(i), i + 1, currentPlayer - 1);
		}

		BasicCommands.deleteCard(out, hand.size() + 1);
	}

	private void spendMana(ActorRef out, int player, int amount) {
		Player p = (player == 1) ? player1 : player2;
		p.setMana(Math.max(0, p.getMana() - amount));

		if (player == 1) {
			BasicCommands.setPlayer1Mana(out, p);
		} else {
			BasicCommands.setPlayer2Mana(out, p);
		}
	}

	// ---------------------------------------------------------------------
	// Damage / death / avatar sync
	// ---------------------------------------------------------------------

	private void applyDamageToUnit(ActorRef out, Unit unit, int amount) {
		if (unit == null) return;

		unit.setHealth(unit.getHealth() - amount);
		BasicCommands.setUnitHealth(out, unit, unit.getHealth());

		// ===== Silverguard Knight Zeal =====
		if (unit == player1Avatar || unit == player2Avatar) {

			int damagedPlayer = (unit == player1Avatar) ? 1 : 2;

			for (BoardCell cell : allCells) {
				if (!cell.isOccupied()) continue;

				Unit u = cell.getOccupant();
				Card c = u.getCard();

				if (c == null) continue;

				if (c.getCardname().equals("Silverguard Knight") &&
						getUnitOwner(u) == damagedPlayer) {

					int newAttack = u.getAttack() + 2;
					u.setAttack(newAttack);

					BasicCommands.setUnitAttack(out, u, newAttack);
				}
			}
		}

		syncAvatarHealthIfNeeded(out, unit);

		// Horn durability loss when avatar takes damage
		if (unit == player1Avatar && hasHorn(1)) {
			reduceHornDurability(out, 1);
		}

		if (unit == player2Avatar && hasHorn(2)) {
			reduceHornDurability(out, 2);
		}
	}

	private void syncAvatarHealthIfNeeded(ActorRef out, Unit unit) {
		if (unit == player1Avatar) {
			player1.setHealth(Math.max(0, unit.getHealth()));
			BasicCommands.setPlayer1Health(out, player1);
		} else if (unit == player2Avatar) {
			player2.setHealth(Math.max(0, unit.getHealth()));
			BasicCommands.setPlayer2Health(out, player2);
		}
	}

	public boolean handleUnitDeathIfNeeded(ActorRef out, Unit unit) {
		if (unit == null || unit.getHealth() > 0) return false;
		if (deathResolvedThisStep.contains(unit.getId())) return false;

		deathResolvedThisStep.add(unit.getId());

		BoardCell cell = getCellForUnit(unit);
		if (cell != null) {
			cell.clearOccupant();
		}

		try {
			BasicCommands.playUnitAnimation(out, unit, structures.basic.UnitAnimationType.death);
			Thread.sleep(300);
		} catch (Exception e) {
			e.printStackTrace();
		}

		BasicCommands.deleteUnit(out, unit);

		if (unit == player1Avatar) {
			player1.setHealth(0);
			BasicCommands.setPlayer1Health(out, player1);
			gameOver = true;
			BasicCommands.addPlayer1Notification(out, "You Lose!", 3);
		} else if (unit == player2Avatar) {
			player2.setHealth(0);
			BasicCommands.setPlayer2Health(out, player2);
			gameOver = true;
			BasicCommands.addPlayer1Notification(out, "You Win!", 3);
		}

		triggerDeathwatchEffects(out, unit);

		removeUnitState(unit);
		return true;
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
				|| name.equals("ironcliffe guardian");
	}

	private boolean hasFlying(Unit unit) {
		return normalizeCardName(unit == null ? null : unit.getCard()).equals("young flamewing");
	}

	private boolean hasRush(Card card) {
		return normalizeCardName(card).equals("saberspine tiger");
	}

	// ---------------------------------------------------------------------
	// AI player
	// ---------------------------------------------------------------------

	public List<BoardCell> getSpellTargetCellsForAI(Card card) {
		return getSpellTargetCells(card);
	}

	public boolean playCardForAI(ActorRef out, Card card, BoardCell targetCell) {
		if (card == null) return false;

		List<Card> hand = getCurrentPlayerHand();
		int index = hand.indexOf(card);
		if (index < 0) return false;

		selectedCard = card;
		selectedHandPosition = index + 1;

		announceAICardPlay(out, card, targetCell);

		if (card.getIsCreature()) {
			selectionMode = SelectionMode.CARD_SUMMON;
			return summonCreatureCard(out, card, targetCell);
		} else {
			selectionMode = SelectionMode.CARD_SPELL;
			return castSpellCard(out, card, targetCell);
		}
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

	private String describeTarget(BoardCell targetCell) {
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

	private String formatTargetSuffix(String targetDescription) {
		if (targetDescription == null || targetDescription.isEmpty()) {
			return "";
		}
		return " on " + targetDescription;
	}

	// ---------------------------------------------------------------------
	// Card
	// ---------------------------------------------------------------------
	private void summonRandomWraithling(ActorRef out, Unit priestess) {

		BoardCell cell = getCellForUnit(priestess);
		if (cell == null) {
			System.out.println("summonRandomWraithling: priestess cell is null");
			return;
		}

		List<BoardCell> adjacent = getAdjacentCells(cell.getX(), cell.getY(), true);
		List<BoardCell> emptyCells = new ArrayList<>();

		for (BoardCell c : adjacent) {
			if (c.isEmpty()) {
				emptyCells.add(c);
			}
		}

		System.out.println("summonRandomWraithling emptyCells size = " + emptyCells.size());

		if (emptyCells.isEmpty()) return;

		Collections.shuffle(emptyCells);
		BoardCell spawnCell = emptyCells.get(0);

		try {
			Unit token = BasicObjectBuilders.loadUnit(
					StaticConfFiles.wraithling,
					allocateUnitId(),
					Unit.class
			);

			applyTokenStats(token, 1, 1);

			spawnCell.trySetOccupant(token);
			token.setPositionByTile(spawnCell.getTile());

			registerUnit(token, getUnitOwner(priestess), "Wraithling");

			BasicCommands.drawUnit(out, token, spawnCell.getTile());
			Thread.sleep(50);
			BasicCommands.setUnitHealth(out, token, token.getHealth());
			BasicCommands.setUnitAttack(out, token, token.getAttack());

			setMovedThisTurn(token, true);
			setAttackedThisTurn(token, true);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void triggerOpeningGambit(ActorRef out, Unit unit) {

		Card card = unit.getCard();

		if (card == null) return;
		if (card.getAbilities() == null) return;

		for (Ability ability : card.getAbilities()) {

			if (!ability.getTrigger().equals("SUMMONED")) continue;

			if (ability.getEffectType().equals("SUMMON_WRAITHLING_BEHIND")) {
				summonWraithlingBehind(out, unit);
			}

			if (ability.getEffectType().equals("DESTROY_DAMAGED_ENEMY")) {
				destroyDamagedAdjacentEnemy(out, unit);
			}

			if (ability.getEffectType().equals("BUFF_ADJACENT_TO_AVATAR")) {
				buffAdjacentToAvatar(out, unit, ability.getAmount());
			}
		}
	}

	private void summonWraithlingBehind(ActorRef out, Unit unit) {

		BoardCell cell = getCellForUnit(unit);

		if (cell == null) return;

		int x = cell.getX();
		int y = cell.getY();

		int owner = getUnitOwner(unit);

		int behindX;

		if (owner == 1) {
			behindX = x - 1;
		} else {
			behindX = x + 1;
		}

		BoardCell spawnCell = getCell(behindX, y);

		if (spawnCell == null || !spawnCell.isEmpty()) return;

		try {
			Unit token = BasicObjectBuilders.loadUnit(
					StaticConfFiles.wraithling,
					allocateUnitId(),
					Unit.class
			);

			applyTokenStats(token, 1, 1);

			spawnCell.trySetOccupant(token);
			token.setPositionByTile(spawnCell.getTile());

			registerUnit(token, owner, "Wraithling");

			BasicCommands.drawUnit(out, token, spawnCell.getTile());
			Thread.sleep(50);
			BasicCommands.setUnitHealth(out, token, token.getHealth());
			BasicCommands.setUnitAttack(out, token, token.getAttack());
			Thread.sleep(50);

			setMovedThisTurn(token, true);
			setAttackedThisTurn(token, true);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void destroyDamagedAdjacentEnemy(ActorRef out, Unit assassin) {

		BoardCell cell = getCellForUnit(assassin);
		if (cell == null) {
			System.out.println("destroyDamagedAdjacentEnemy: assassin cell is null");
			return;
		}

		int owner = getUnitOwner(assassin);
		List<Unit> candidates = new ArrayList<>();

		for (BoardCell neighbor : getAdjacentCells(cell.getX(), cell.getY(), true)) {

			if (!neighbor.isOccupied()) continue;

			Unit target = neighbor.getOccupant();

			if (getUnitOwner(target) == owner) continue;

			if (target.getHealth() < target.getMaxHealth()) {
				candidates.add(target);
			}
		}

		System.out.println("destroyDamagedAdjacentEnemy candidates size = " + candidates.size());

		if (candidates.isEmpty()) return;

		Unit victim = candidates.get(0);

		victim.setHealth(0);
		handleUnitDeathIfNeeded(out, victim);
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

		BoardCell cell = getCellForUnit(avatar);
		if (cell == null) return;

		List<BoardCell> adjacent = getAdjacentCells(cell.getX(), cell.getY(), true);
		List<BoardCell> emptyCells = new ArrayList<>();

		for (BoardCell c : adjacent) {
			if (c.isEmpty()) {
				emptyCells.add(c);
			}
		}

		if (emptyCells.isEmpty()) return;

		Collections.shuffle(emptyCells);
		BoardCell spawnCell = emptyCells.get(0);

		try {
			Unit token = BasicObjectBuilders.loadUnit(
					StaticConfFiles.wraithling,
					allocateUnitId(),
					Unit.class
			);

			applyTokenStats(token, 1, 1);

			spawnCell.trySetOccupant(token);
			token.setPositionByTile(spawnCell.getTile());

			registerUnit(token, owner, "Wraithling");

			BasicCommands.drawUnit(out, token, spawnCell.getTile());
			Thread.sleep(50);
			BasicCommands.setUnitHealth(out, token, token.getHealth());
			BasicCommands.setUnitAttack(out, token, token.getAttack());

			setMovedThisTurn(token, true);
			setAttackedThisTurn(token, true);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void buffAdjacentToAvatar(ActorRef out, Unit squire, int amount) {

		int owner = getUnitOwner(squire);

		Unit avatar = (owner == 1) ? player1Avatar : player2Avatar;

		BoardCell avatarCell = getCellForUnit(avatar);
		if (avatarCell == null) return;

		List<BoardCell> adjacent = new ArrayList<>();
		if (owner == 1) {
			BoardCell front = getCell(avatarCell.getX() + 1, avatarCell.getY());
			BoardCell back = getCell(avatarCell.getX() - 1, avatarCell.getY());
			if (front != null) adjacent.add(front);
			if (back != null) adjacent.add(back);
		} else {
			BoardCell front = getCell(avatarCell.getX() - 1, avatarCell.getY());
			BoardCell back = getCell(avatarCell.getX() + 1, avatarCell.getY());
			if (front != null) adjacent.add(front);
			if (back != null) adjacent.add(back);
		}

		for (BoardCell cell : adjacent) {

			if (!cell.isOccupied()) continue;

			Unit target = cell.getOccupant();

			if (getUnitOwner(target) != owner) continue;

			int newAttack = target.getAttack() + amount;
			int newHealth = target.getHealth() + amount;
			int newMaxHealth = target.getMaxHealth() + amount;

			target.setAttack(newAttack);
			target.setHealth(newHealth);
			target.setMaxHealth(newMaxHealth);

			BasicCommands.setUnitAttack(out, target, newAttack);
			BasicCommands.setUnitHealth(out, target, newHealth);
		}
	}

}
