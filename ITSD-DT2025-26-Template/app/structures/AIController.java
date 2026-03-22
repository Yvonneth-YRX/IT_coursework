package structures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.Unit;
import structures.board.BoardCell;

public class AIController {

    public static void takeTurn(ActorRef out, GameState gameState) {
        if (gameState.isGameOver()) return;
        if (gameState.getCurrentPlayer() != 2) return;

        try {
            BasicCommands.addPlayer1Notification(out, "AI Turn...", 2);
            Thread.sleep(400);

            playCards(out, gameState);
            Thread.sleep(300);

            moveAndAttackWithUnits(out, gameState);
            Thread.sleep(300);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------
    // 1. AI tries to play cards first
    // ------------------------------------------------------------
    private static void playCards(ActorRef out, GameState gameState) throws Exception {
        boolean playedSomething = true;

        while (playedSomething && !gameState.isGameOver() && gameState.getCurrentPlayer() == 2) {
            playedSomething = false;

            List<Card> handCopy = new ArrayList<>(gameState.getPlayer2Hand());

            // sort by mana cost descending so AI prefers bigger cards
            handCopy.sort(Comparator.comparingInt(Card::getManacost).reversed());

            for (Card card : handCopy) {
                if (card == null) continue;
                if (gameState.getPlayer2().getMana() < card.getManacost()) continue;

                BoardCell target = chooseCardTarget(gameState, card);

                if (target != null) {
                    boolean success = gameState.playCardForAI(out, card, target);
                    if (success) {
                        playedSomething = true;
                        Thread.sleep(300);
                        break;
                    }
                }
            }
        }
    }

    private static BoardCell chooseCardTarget(GameState gameState, Card card) {
        if (card == null) return null;

        // Creature card -> summon near enemy avatar
        if (card.getIsCreature()) {
            List<BoardCell> summonCells = gameState.getValidSummonCellsForCurrentPlayer();
            if (summonCells.isEmpty()) return null;

            Unit enemyAvatar = gameState.getPlayer1Avatar();
            BoardCell enemyAvatarCell = gameState.getCellForUnit(enemyAvatar);
            if (enemyAvatarCell == null) return summonCells.get(0);

            return summonCells.stream()
                    .min(Comparator.comparingInt(c -> distance(c, enemyAvatarCell)))
                    .orElse(summonCells.get(0));
        }

        // Spell card -> choose best valid spell target
        List<BoardCell> spellTargets = gameState.getSpellTargetCellsForAI(card);
        if (spellTargets == null || spellTargets.isEmpty()) return null;

        String name = card.getCardname().trim().toLowerCase();

        // Damage spell: prefer enemy avatar, otherwise lowest-health enemy
        if (name.equals("truestrike")) {
            for (BoardCell cell : spellTargets) {
                if (cell.isOccupied() && cell.getOccupant() == gameState.getPlayer1Avatar()) {
                    return cell;
                }
            }
            return spellTargets.stream()
                    .filter(BoardCell::isOccupied)
                    .min(Comparator.comparingInt(c -> c.getOccupant().getHealth()))
                    .orElse(spellTargets.get(0));
        }

        // Destroy spell: prefer strongest enemy non-avatar, otherwise first enemy
        if (name.equals("dark terminus")) {
            BoardCell best = spellTargets.stream()
                    .filter(BoardCell::isOccupied)
                    .filter(c -> c.getOccupant() != gameState.getPlayer1Avatar())
                    .max(Comparator.comparingInt(c -> c.getOccupant().getAttack() + c.getOccupant().getHealth()))
                    .orElse(null);

            if (best != null) return best;

            for (BoardCell cell : spellTargets) {
                if (cell.isOccupied()) return cell;
            }
            return spellTargets.get(0);
        }

        // Stun spell: prefer enemy with highest attack
        if (name.equals("beamshock")) {
            return spellTargets.stream()
                    .filter(BoardCell::isOccupied)
                    .max(Comparator.comparingInt(c -> c.getOccupant().getAttack()))
                    .orElse(spellTargets.get(0));
        }

        // Heal spell: prefer most damaged friendly unit
        if (name.equals("sundrop elixir")) {
            return spellTargets.stream()
                    .filter(BoardCell::isOccupied)
                    .max(Comparator.comparingInt(c -> c.getOccupant().getMaxHealth() - c.getOccupant().getHealth()))
                    .orElse(spellTargets.get(0));
        }

        // Summon token spell -> pick closest tile to enemy avatar
        if (name.equals("wraithling swarm") || name.equals("horn of the forsaken")) {
            BoardCell enemyAvatarCell = gameState.getCellForUnit(gameState.getPlayer1Avatar());
            if (enemyAvatarCell == null) return spellTargets.get(0);

            return spellTargets.stream()
                    .min(Comparator.comparingInt(c -> distance(c, enemyAvatarCell)))
                    .orElse(spellTargets.get(0));
        }

        return spellTargets.get(0);
    }

    // ------------------------------------------------------------
    // 2. AI moves and attacks with all available units
    // ------------------------------------------------------------
    private static void moveAndAttackWithUnits(ActorRef out, GameState gameState) throws Exception {
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, 2);

        for (Unit unit : aiUnits) {
            if (gameState.isGameOver()) return;
            if (gameState.getCurrentPlayer() != 2) return;
            if (unit == null) continue;
            if (gameState.isUnitStunned(unit)) continue;

            boolean alreadyFinished =
                    gameState.hasMovedThisTurn(unit) && gameState.hasAttackedThisTurn(unit);
            if (alreadyFinished) continue;

            // First try direct attack
            List<BoardCell> attackCells = gameState.getValidAttackCells(unit);
            if (!gameState.hasAttackedThisTurn(unit) && !attackCells.isEmpty()) {
                gameState.selectUnit(out, unit);
                Thread.sleep(150);
                BoardCell bestTarget = chooseBestAttackTarget(gameState, attackCells);
                if (bestTarget != null) {
                    gameState.attackSelectedTarget(out, bestTarget);
                    Thread.sleep(350);
                    continue;
                }
            }

            // Otherwise move toward enemy avatar
            if (!gameState.hasMovedThisTurn(unit)) {
                List<BoardCell> moveCells = gameState.getValidMoveCells(unit);
                if (!moveCells.isEmpty()) {
                    BoardCell enemyAvatarCell = gameState.getCellForUnit(gameState.getPlayer1Avatar());
                    if (enemyAvatarCell != null) {
                        BoardCell bestMove = moveCells.stream()
                                .min(Comparator.comparingInt(c -> distance(c, enemyAvatarCell)))
                                .orElse(moveCells.get(0));

                        gameState.selectUnit(out, unit);
                        Thread.sleep(150);
                        gameState.moveSelectedUnitTo(out, bestMove);
                        Thread.sleep(350);
                    }
                }
            }

            // After moving, try attack again
            if (!gameState.hasAttackedThisTurn(unit)) {
                List<BoardCell> attackAfterMove = gameState.getValidAttackCells(unit);
                if (!attackAfterMove.isEmpty()) {
                    gameState.selectUnit(out, unit);
                    Thread.sleep(150);
                    BoardCell bestTarget = chooseBestAttackTarget(gameState, attackAfterMove);
                    if (bestTarget != null) {
                        gameState.attackSelectedTarget(out, bestTarget);
                        Thread.sleep(350);
                    }
                }
            }

            gameState.clearSelection(out);
            Thread.sleep(100);
        }
    }

    private static BoardCell chooseBestAttackTarget(GameState gameState, List<BoardCell> targets) {
        if (targets == null || targets.isEmpty()) return null;

        // Always prioritize enemy avatar
        for (BoardCell cell : targets) {
            if (cell.isOccupied() && cell.getOccupant() == gameState.getPlayer1Avatar()) {
                return cell;
            }
        }

        // Otherwise pick lowest-health enemy
        return targets.stream()
                .filter(BoardCell::isOccupied)
                .min(Comparator.comparingInt(c -> c.getOccupant().getHealth()))
                .orElse(targets.get(0));
    }

    private static List<Unit> getUnitsOwnedBy(GameState gameState, int owner) {
        List<Unit> result = new ArrayList<>();

        for (BoardCell cell : gameState.getAllCells()) {
            if (!cell.isOccupied()) continue;
            Unit unit = cell.getOccupant();
            if (gameState.getUnitOwner(unit) == owner) {
                result.add(unit);
            }
        }

        return result;
    }

    private static int distance(BoardCell a, BoardCell b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}