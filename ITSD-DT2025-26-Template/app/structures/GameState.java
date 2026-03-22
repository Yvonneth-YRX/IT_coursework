package structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
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

	// ---- Deck / Hand ----
	private List<Card> player1Deck = new ArrayList<>();
	private List<Card> player2Deck = new ArrayList<>();
	private List<Card> player1Hand = new ArrayList<>();
	private List<Card> player2Hand = new ArrayList<>();

	// ---- Unit ownership + turn flags ----
	private final Map<Integer, Integer> unitOwners = new HashMap<>();
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

	private int nextUnitId = 100;

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

	public List<Card> getPlayer1Deck() { return player1Deck; }
	public List<Card> getPlayer2Deck() { return player2Deck; }
	public List<Card> getPlayer1Hand() { return player1Hand; }
	public List<Card> getPlayer2Hand() { return player2Hand; }

	public Unit getSelectedUnit() { return selectedUnit; }
	public Card getSelectedCard() { return selectedCard; }

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

	public void switchPlayer() {
		currentPlayer = (currentPlayer == 1) ? 2 : 1;
	}

	public void increaseTurn() {
		turnNumber++;
	}

	public void setGameOver(boolean gameOver) {
		this.gameOver = gameOver;
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
		if (unit == null) return;
		unitOwners.put(unit.getId(), owner);
		movedThisTurn.put(unit.getId(), false);
		attackedThisTurn.put(unit.getId(), false);
	}

	public int getUnitOwner(Unit unit) {
		if (unit == null) return 0;
		return unitOwners.getOrDefault(unit.getId(), 0);
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
		return stunnedUntilTurn.containsKey(unit.getId());
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
		redrawPlayerHand(out, player);
		List<Card> hand = (player == 1) ? player1Hand : player2Hand;
		int index = handPosition - 1;
		if (index >= 0 && index < hand.size()) {
			BasicCommands.drawCard(out, hand.get(index), handPosition, 1);
		}
	}

	public void clearSelection(ActorRef out) {
		selectedUnit = null;
		selectedCard = null;
		selectedHandPosition = -1;
		selectionMode = SelectionMode.NONE;

		selectedMoveCells.clear();
		selectedAttackCells.clear();
		selectedCardTargetCells.clear();

		clearAllHighlights(out);
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

		for (BoardCell cell : getAdjacentCells(origin.getX(), origin.getY(), true)) {
			if (cell.isOccupied() && getUnitOwner(cell.getOccupant()) != getUnitOwner(unit)) {
				result.add(cell);
			}
		}
		return result;
	}

	public boolean moveSelectedUnitTo(ActorRef out, BoardCell destination) {
		if (selectedUnit == null || destination == null) return false;
		if (!isSelectedMoveCell(destination)) return false;

		BoardCell origin = getCellForUnit(selectedUnit);
		if (origin == null || !destination.isEmpty()) return false;

		origin.clearOccupant();
		destination.trySetOccupant(selectedUnit);
		selectedUnit.setPositionByTile(destination.getTile());

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
		return true;
	}

	// ---------------------------------------------------------------------
	// Card select / summon / spell
	// ---------------------------------------------------------------------

	public void selectCard(ActorRef out, Card card, int handPosition) {
		selectedUnit = null;
		selectedMoveCells.clear();
		selectedAttackCells.clear();

		selectedCard = card;
		selectedHandPosition = handPosition;
		selectedCardTargetCells.clear();

		if (card == null) {
			clearSelection(out);
			return;
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
			Unit avatar = (currentPlayer == 1) ? player1Avatar : player2Avatar;
			BoardCell avatarCell = getCellForUnit(avatar);
			if (avatarCell != null) {
				for (BoardCell adj : getAdjacentCells(avatarCell.getX(), avatarCell.getY(), true)) {
					if (adj.isEmpty()) result.add(adj);
				}
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
				if (owner != currentPlayer) result.add(cell);
			} else if (name.equals("dark terminus")) {
				if (owner != currentPlayer) result.add(cell);
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
			applyCardStatsToUnit(card, unit);

			targetCell.trySetOccupant(unit);
			unit.setPositionByTile(targetCell.getTile());
			registerUnit(unit, currentPlayer);

			BasicCommands.drawUnit(out, unit, targetCell.getTile());
			Thread.sleep(50);
			BasicCommands.setUnitHealth(out, unit, unit.getHealth());
			BasicCommands.setUnitAttack(out, unit, unit.getAttack());

			setMovedThisTurn(unit, true);
			setAttackedThisTurn(unit, true);

			spendMana(out, currentPlayer, card.getManacost());
			removeCardFromCurrentHand(out, selectedHandPosition);

			clearSelection(out);
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
				applyDamageToUnit(out, targetCell.getOccupant(), 2);
				handleUnitDeathIfNeeded(out, targetCell.getOccupant());
			} else if (name.equals("beamshock")) {
				if (!targetCell.isOccupied()) return false;
				stunUnitUntilNextTurn(targetCell.getOccupant());
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

				targetCell.clearOccupant();
				victim.setHealth(0);
				handleUnitDeathIfNeeded(out, victim);

				Unit token = BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, allocateUnitId(), Unit.class);
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
				List<BoardCell> summonCells = getValidSummonCellsForCurrentPlayer();
				int limit = Math.min(3, summonCells.size());

				for (int i = 0; i < limit; i++) {
					BoardCell cell = summonCells.get(i);
					Unit token = BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, allocateUnitId(), Unit.class);
					applyTokenStats(token, 1, 1);
					cell.trySetOccupant(token);
					token.setPositionByTile(cell.getTile());
					registerUnit(token, currentPlayer);

					BasicCommands.drawUnit(out, token, cell.getTile());
					Thread.sleep(50);
					BasicCommands.setUnitHealth(out, token, token.getHealth());
					BasicCommands.setUnitAttack(out, token, token.getAttack());

					setMovedThisTurn(token, true);
					setAttackedThisTurn(token, true);
				}
			} else if (name.equals("horn of the forsaken")) {
				// Simplified temporary implementation:
				// summon 1 wraithling adjacent to your avatar.
				if (targetCell == null || !targetCell.isEmpty()) return false;

				Unit token = BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, allocateUnitId(), Unit.class);
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

				BasicCommands.addPlayer1Notification(out, "Horn: simplified summon version", 2);
			} else {
				return false;
			}

			spendMana(out, currentPlayer, card.getManacost());
			removeCardFromCurrentHand(out, selectedHandPosition);
			clearSelection(out);
			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private String normalizeCardName(Card card) {
		return card.getCardname().trim().toLowerCase();
	}

	private void removeCardFromCurrentHand(ActorRef out, int handPosition) {
		List<Card> hand = getCurrentPlayerHand();
		int index = handPosition - 1;
		if (index >= 0 && index < hand.size()) {
			hand.remove(index);
		}
		redrawPlayerHand(out, currentPlayer);
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
		syncAvatarHealthIfNeeded(out, unit);
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

		unitOwners.remove(unit.getId());
		movedThisTurn.remove(unit.getId());
		attackedThisTurn.remove(unit.getId());
		stunnedUntilTurn.remove(unit.getId());
		return true;
	}
}