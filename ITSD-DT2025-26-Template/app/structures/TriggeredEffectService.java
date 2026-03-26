package structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Ability;
import structures.basic.Card;
import structures.basic.Unit;
import structures.board.BoardCell;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

/**
 * Triggered-effect logic extracted from the original {@link GameState}.
 * The original state file keeps bridge methods so markers can still follow
 * the gameplay flow from coursework-provided source files.
 */
public final class TriggeredEffectService {

    private TriggeredEffectService() {
    }

    public static void triggerOpeningGambit(GameState gameState, ActorRef out, Unit unit) {
        Card card = unit.getCard();

        if (card == null || card.getAbilities() == null) {
            return;
        }

        for (Ability ability : card.getAbilities()) {
            if (!"SUMMONED".equals(ability.getTrigger())) continue;

            if ("SUMMON_WRAITHLING_BEHIND".equals(ability.getEffectType())) {
                summonWraithlingBehind(gameState, out, unit);
            }

            if ("DESTROY_DAMAGED_ENEMY".equals(ability.getEffectType())) {
                destroyDamagedAdjacentEnemy(gameState, out, unit);
            }

            if ("BUFF_ADJACENT_TO_AVATAR".equals(ability.getEffectType())) {
                buffAdjacentToAvatar(gameState, out, unit, ability.getAmount());
            }
        }
    }

    public static void summonRandomWraithling(GameState gameState, ActorRef out, Unit priestess) {
        BoardCell cell = gameState.getCellForUnit(priestess);
        if (cell == null) {
            System.out.println("summonRandomWraithling: priestess cell is null");
            return;
        }

        List<BoardCell> emptyCells = getEmptyAdjacentCells(gameState, cell);
        System.out.println("summonRandomWraithling emptyCells size = " + emptyCells.size());
        if (emptyCells.isEmpty()) return;

        Collections.shuffle(emptyCells);
        spawnWraithling(gameState, out, emptyCells.get(0), gameState.getUnitOwner(priestess));
    }

    public static void summonRandomAdjacentWraithlingToAvatar(GameState gameState, ActorRef out, Unit avatar, int owner) {
        BoardCell cell = gameState.getCellForUnit(avatar);
        if (cell == null) return;

        List<BoardCell> emptyCells = getEmptyAdjacentCells(gameState, cell);
        if (emptyCells.isEmpty()) return;

        Collections.shuffle(emptyCells);
        spawnWraithling(gameState, out, emptyCells.get(0), owner);
    }

    public static void summonWraithlingBehind(GameState gameState, ActorRef out, Unit unit) {
        BoardCell cell = gameState.getCellForUnit(unit);
        if (cell == null) return;

        int owner = gameState.getUnitOwner(unit);
        int behindX = owner == 1 ? cell.getX() - 1 : cell.getX() + 1;
        BoardCell spawnCell = gameState.getCell(behindX, cell.getY());

        if (spawnCell == null || !spawnCell.isEmpty()) return;
        spawnWraithling(gameState, out, spawnCell, owner);
    }

    public static void destroyDamagedAdjacentEnemy(GameState gameState, ActorRef out, Unit assassin) {
        BoardCell cell = gameState.getCellForUnit(assassin);
        if (cell == null) {
            System.out.println("destroyDamagedAdjacentEnemy: assassin cell is null");
            return;
        }

        int owner = gameState.getUnitOwner(assassin);
        List<Unit> candidates = new ArrayList<>();

        for (BoardCell neighbor : gameState.getAdjacentCells(cell.getX(), cell.getY(), true)) {
            if (!neighbor.isOccupied()) continue;

            Unit target = neighbor.getOccupant();
            if (gameState.getUnitOwner(target) == owner) continue;
            if (target == gameState.getPlayer1Avatar() || target == gameState.getPlayer2Avatar()) continue;

            if (target.getHealth() < target.getMaxHealth()) {
                candidates.add(target);
            }
        }

        System.out.println("destroyDamagedAdjacentEnemy candidates size = " + candidates.size());
        if (candidates.isEmpty()) return;

        Unit victim = candidates.get(0);
        victim.setHealth(0);
        gameState.handleUnitDeathIfNeeded(out, victim);
    }

    public static void buffAdjacentToAvatar(GameState gameState, ActorRef out, Unit squire, int amount) {
        int owner = gameState.getUnitOwner(squire);
        Unit avatar = owner == 1 ? gameState.getPlayer1Avatar() : gameState.getPlayer2Avatar();
        BoardCell avatarCell = gameState.getCellForUnit(avatar);
        if (avatarCell == null) return;

        List<BoardCell> candidateCells = new ArrayList<>();
        if (owner == 1) {
            BoardCell front = gameState.getCell(avatarCell.getX() + 1, avatarCell.getY());
            BoardCell back = gameState.getCell(avatarCell.getX() - 1, avatarCell.getY());
            if (front != null) candidateCells.add(front);
            if (back != null) candidateCells.add(back);
        } else {
            BoardCell front = gameState.getCell(avatarCell.getX() - 1, avatarCell.getY());
            BoardCell back = gameState.getCell(avatarCell.getX() + 1, avatarCell.getY());
            if (front != null) candidateCells.add(front);
            if (back != null) candidateCells.add(back);
        }

        for (BoardCell effectCell : candidateCells) {
            if (!effectCell.isOccupied()) continue;

            Unit target = effectCell.getOccupant();
            if (gameState.getUnitOwner(target) != owner) continue;

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

    private static List<BoardCell> getEmptyAdjacentCells(GameState gameState, BoardCell origin) {
        List<BoardCell> emptyCells = new ArrayList<>();
        for (BoardCell cell : gameState.getAdjacentCells(origin.getX(), origin.getY(), true)) {
            if (cell.isEmpty()) {
                emptyCells.add(cell);
            }
        }
        return emptyCells;
    }

    private static void spawnWraithling(GameState gameState, ActorRef out, BoardCell spawnCell, int owner) {
        try {
            Unit token = BasicObjectBuilders.loadUnit(
                    StaticConfFiles.wraithling,
                    gameState.allocateUnitId(),
                    Unit.class
            );

            gameState.applyTokenStats(token, 1, 1);

            spawnCell.trySetOccupant(token);
            token.setPositionByTile(spawnCell.getTile());

            gameState.registerUnit(token, owner, "Wraithling");

            BasicCommands.drawUnit(out, token, spawnCell.getTile());
            Thread.sleep(50);
            BasicCommands.setUnitHealth(out, token, token.getHealth());
            BasicCommands.setUnitAttack(out, token, token.getAttack());
            Thread.sleep(50);

            gameState.setMovedThisTurn(token, true);
            gameState.setAttackedThisTurn(token, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
