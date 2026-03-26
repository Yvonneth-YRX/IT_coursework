package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Unit;
import structures.board.BoardCell;

/**
 * Combat-resolution logic extracted from the original {@link GameState}.
 * The state file keeps compatibility wrappers so the coursework-provided
 * source still shows where combat enters the system.
 */
public final class CombatResolutionService {

    private CombatResolutionService() {
    }

    public static boolean attackSelectedTarget(GameState gameState, ActorRef out, BoardCell targetCell) {
        if (gameState.getSelectedUnit() == null || targetCell == null || !targetCell.isOccupied()) return false;
        if (!gameState.isSelectedAttackCell(targetCell)) return false;

        Unit attacker = gameState.getSelectedUnit();
        Unit defender = targetCell.getOccupant();

        BoardCell attackerCell = gameState.getCellForUnit(attacker);
        if (attackerCell == null) return false;

        try {
            BasicCommands.playUnitAnimation(out, attacker, structures.basic.UnitAnimationType.attack);
            Thread.sleep(200);
        } catch (Exception e) {
            e.printStackTrace();
        }

        applyDamageToUnit(gameState, out, defender, attacker.getAttack());

        // Horn trigger: avatar dealt damage
        if (attacker == gameState.getPlayer1Avatar() && gameState.hasHorn(1)) {
            gameState.summonHornWraithling(out, 1);
        }

        if (attacker == gameState.getPlayer2Avatar() && gameState.hasHorn(2)) {
            gameState.summonHornWraithling(out, 2);
        }

        gameState.setAttackedThisTurn(attacker, true);

        boolean defenderDied = handleUnitDeathIfNeeded(gameState, out, defender);

        if (!defenderDied) {
            BoardCell defenderCellNow = gameState.getCellForUnit(defender);
            BoardCell attackerCellNow = gameState.getCellForUnit(attacker);

            if (defenderCellNow != null && attackerCellNow != null
                    && gameState.areAdjacent(defenderCellNow, attackerCellNow, true)) {
                try {
                    BasicCommands.playUnitAnimation(out, defender, structures.basic.UnitAnimationType.attack);
                    Thread.sleep(200);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                applyDamageToUnit(gameState, out, attacker, defender.getAttack());
                handleUnitDeathIfNeeded(gameState, out, attacker);
            }
        }

        gameState.clearSelection(out);
        gameState.clearDeathResolutionCache();
        return true;
    }

    public static void applyDamageToUnit(GameState gameState, ActorRef out, Unit unit, int amount) {
        if (unit == null) return;

        unit.setHealth(unit.getHealth() - amount);
        BasicCommands.setUnitHealth(out, unit, unit.getHealth());

        // Silverguard Knight Zeal
        if (unit == gameState.getPlayer1Avatar() || unit == gameState.getPlayer2Avatar()) {
            int damagedPlayer = (unit == gameState.getPlayer1Avatar()) ? 1 : 2;

            for (BoardCell cell : gameState.getMutableAllCells()) {
                if (!cell.isOccupied()) continue;

                Unit affectedUnit = cell.getOccupant();
                if (affectedUnit.getCard() == null) continue;

                if (affectedUnit.getCard().getCardname().equals("Silverguard Knight")
                        && gameState.getUnitOwner(affectedUnit) == damagedPlayer) {
                    int newAttack = affectedUnit.getAttack() + 2;
                    affectedUnit.setAttack(newAttack);
                    BasicCommands.setUnitAttack(out, affectedUnit, newAttack);
                }
            }
        }

        syncAvatarHealthIfNeeded(gameState, out, unit);

        if (unit == gameState.getPlayer1Avatar() && gameState.hasHorn(1)) {
            gameState.reduceHornDurability(out, 1);
        }

        if (unit == gameState.getPlayer2Avatar() && gameState.hasHorn(2)) {
            gameState.reduceHornDurability(out, 2);
        }
    }

    public static void syncAvatarHealthIfNeeded(GameState gameState, ActorRef out, Unit unit) {
        if (unit == gameState.getPlayer1Avatar()) {
            gameState.getPlayer1().setHealth(Math.max(0, unit.getHealth()));
            BasicCommands.setPlayer1Health(out, gameState.getPlayer1());
        } else if (unit == gameState.getPlayer2Avatar()) {
            gameState.getPlayer2().setHealth(Math.max(0, unit.getHealth()));
            BasicCommands.setPlayer2Health(out, gameState.getPlayer2());
        }
    }

    public static boolean handleUnitDeathIfNeeded(GameState gameState, ActorRef out, Unit unit) {
        if (unit == null || unit.getHealth() > 0) return false;
        if (gameState.isDeathAlreadyResolved(unit)) return false;

        gameState.markDeathResolved(unit);

        BoardCell cell = gameState.getCellForUnit(unit);
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

        if (unit == gameState.getPlayer1Avatar()) {
            gameState.getPlayer1().setHealth(0);
            BasicCommands.setPlayer1Health(out, gameState.getPlayer1());
            gameState.setGameOver(true);
            BasicCommands.addPlayer1Notification(out, "You Lose!", 3);
        } else if (unit == gameState.getPlayer2Avatar()) {
            gameState.getPlayer2().setHealth(0);
            BasicCommands.setPlayer2Health(out, gameState.getPlayer2());
            gameState.setGameOver(true);
            BasicCommands.addPlayer1Notification(out, "You Win!", 3);
        }

        gameState.triggerDeathwatchEffectsFromService(out, unit);
        gameState.removeUnitStateFromService(unit);
        return true;
    }
}
