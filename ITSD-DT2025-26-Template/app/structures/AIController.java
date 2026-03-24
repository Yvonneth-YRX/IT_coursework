package structures;

import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.Unit;
import structures.board.BoardCell;

public class AIController {

    private static final int AI_OWNER = 2;
    private static final int HUMAN_OWNER = 1;
    private static final int HERO_KILL_SCORE = 100000;
    private static final int HERO_PRESSURE_SCORE = 600;
    private static final int ACTION_ATTACK_BONUS = 120;
    private static final int ACTION_CARD_BONUS = 80;
    private static final int ACTION_MOVE_BONUS = 0;
    private static final int DANGER_HEALTH_THRESHOLD = 8;
    private static final int AI_MOVE_ANIMATION_WAIT_MS = 1800;
    private static final int AI_NON_MOVE_WAIT_MS = 350;

    private enum ActionType {
        ATTACK,
        CARD,
        MOVE
    }

    private static class ActionChoice {
        private final ActionType type;
        private final AttackChoice attackChoice;
        private final CardChoice cardChoice;
        private final MoveChoice moveChoice;
        private final int score;

        private ActionChoice(AttackChoice attackChoice) {
            this.type = ActionType.ATTACK;
            this.attackChoice = attackChoice;
            this.cardChoice = null;
            this.moveChoice = null;
            this.score = attackChoice.score;
        }

        private ActionChoice(CardChoice cardChoice) {
            this.type = ActionType.CARD;
            this.attackChoice = null;
            this.cardChoice = cardChoice;
            this.moveChoice = null;
            this.score = cardChoice.score;
        }

        private ActionChoice(MoveChoice moveChoice) {
            this.type = ActionType.MOVE;
            this.attackChoice = null;
            this.cardChoice = null;
            this.moveChoice = moveChoice;
            this.score = moveChoice.score;
        }
    }

    private static class AttackChoice {
        private final Unit attacker;
        private final BoardCell target;
        private final int score;

        private AttackChoice(Unit attacker, BoardCell target, int score) {
            this.attacker = attacker;
            this.target = target;
            this.score = score;
        }
    }

    private static class MoveChoice {
        private final Unit unit;
        private final BoardCell destination;
        private final int score;

        private MoveChoice(Unit unit, BoardCell destination, int score) {
            this.unit = unit;
            this.destination = destination;
            this.score = score;
        }
    }

    private static class CardChoice {
        private final Card card;
        private final BoardCell target;
        private final int score;

        private CardChoice(Card card, BoardCell target, int score) {
            this.card = card;
            this.target = target;
            this.score = score;
        }
    }

    public static void takeTurn(ActorRef out, GameState gameState) {
        if (gameState.isGameOver()) return;
        if (gameState.getCurrentPlayer() != AI_OWNER) return;

        try {
            BasicCommands.addPlayer1Notification(out, "AI Turn...", 2);
            Thread.sleep(400);
            takeBestActions(out, gameState);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void takeBestActions(ActorRef out, GameState gameState) throws Exception {
        while (!gameState.isGameOver() && gameState.getCurrentPlayer() == AI_OWNER) {
            ActionChoice bestAction = chooseBestAction(gameState);
            if (bestAction == null) {
                break;
            }

            boolean success = false;
            switch (bestAction.type) {
                case ATTACK:
                    gameState.selectUnit(out, bestAction.attackChoice.attacker);
                    Thread.sleep(120);
                    success = gameState.attackSelectedTarget(
                            out,
                            bestAction.attackChoice.target);
                    break;
                case CARD:
                    success = gameState.playCardForAI(
                            out,
                            bestAction.cardChoice.card,
                            bestAction.cardChoice.target);
                    break;
                case MOVE:
                    gameState.selectUnit(out, bestAction.moveChoice.unit);
                    Thread.sleep(120);
                    success = gameState.moveSelectedUnitTo(
                            out,
                            bestAction.moveChoice.destination);
                    break;
                default:
                    break;
            }

            if (!success) {
                break;
            }

            Thread.sleep(bestAction.type == ActionType.MOVE ? AI_MOVE_ANIMATION_WAIT_MS : AI_NON_MOVE_WAIT_MS);
        }
    }

    private static ActionChoice chooseBestAction(GameState gameState) {
        AttackChoice bestAttack = chooseBestAttack(gameState);
        CardChoice bestCard = chooseBestCardPlay(gameState);
        MoveChoice bestMove = chooseBestMove(gameState);

        ActionChoice bestAction = null;

        if (bestAttack != null) {
            bestAction = new ActionChoice(bestAttack);
        }
        if (bestCard != null && (bestAction == null || bestCard.score > bestAction.score)) {
            bestAction = new ActionChoice(bestCard);
        }
        if (bestMove != null && (bestAction == null || bestMove.score > bestAction.score)) {
            bestAction = new ActionChoice(bestMove);
        }

        return bestAction;
    }

    private static CardChoice chooseBestCardPlay(GameState gameState) {
        List<Card> handCopy = new ArrayList<>(gameState.getPlayer2Hand());
        CardChoice bestChoice = null;

        for (Card card : handCopy) {
            if (card == null) continue;
            if (gameState.getPlayer2().getMana() < card.getManacost()) continue;

            List<BoardCell> targets = getCandidateTargets(gameState, card);
            for (BoardCell target : targets) {
                int score = scoreCardPlay(gameState, card, target) + ACTION_CARD_BONUS;
                if (bestChoice == null || score > bestChoice.score) {
                    bestChoice = new CardChoice(card, target, score);
                }
            }
        }

        return bestChoice;
    }

    private static List<BoardCell> getCandidateTargets(GameState gameState, Card card) {
        if (card == null) return new ArrayList<>();
        if (card.getIsCreature()) {
            return gameState.getValidSummonCellsForCurrentPlayer();
        }
        return gameState.getSpellTargetCellsForAI(card);
    }

    private static int scoreCardPlay(GameState gameState, Card card, BoardCell target) {
        if (card == null || target == null) return Integer.MIN_VALUE;

        String name = normalize(card.getCardname());
        Unit enemyAvatar = gameState.getPlayer1Avatar();
        Unit aiAvatar = gameState.getPlayer2Avatar();
        BoardCell enemyAvatarCell = gameState.getCellForUnit(enemyAvatar);
        boolean defensiveMode = shouldEnterDefensiveMode(gameState);
        int score = card.getManacost() * 5;
        int enemyHeroHealth = enemyAvatar == null ? 99 : enemyAvatar.getHealth();
        int availableAttackDamage = getAvailableAttackDamageToHero(gameState);

        if (card.getIsCreature()) {
            score += 150;
            if (enemyAvatarCell != null) {
                score += Math.max(0, 20 - distance(target, enemyAvatarCell) * 3);
            }
            score += countAdjacentEnemies(gameState, target, AI_OWNER) * 18;
            score -= countAdjacentEnemies(gameState, target, HUMAN_OWNER) * 10;
            if (enemyAvatarCell != null && distance(target, enemyAvatarCell) <= 2) {
                score += 50;
            }
            if (defensiveMode) {
                score += defensiveSummonScore(gameState, target);
            }
            return score;
        }

        if (name.equals("truestrike") && target.isOccupied()) {
            Unit victim = target.getOccupant();
            if (victim == enemyAvatar && victim.getHealth() <= 2) return HERO_KILL_SCORE;
            score += evaluateDamageSpellTarget(gameState, victim, 2, true);
            if (victim == enemyAvatar && availableAttackDamage + 2 >= enemyHeroHealth) {
                score += HERO_KILL_SCORE / 2;
            }
        } else if (name.equals("dark terminus") && target.isOccupied()) {
            Unit victim = target.getOccupant();
            if (victim == enemyAvatar) {
                score -= 1000;
            } else {
                score += 300 + estimateThreat(gameState, victim) * 12;
                if (victim.getAttack() >= 4) score += 120;
                if (isAdjacentToAvatar(gameState, victim, aiAvatar)) score += 120;
                if (defensiveMode && isAdjacentToAvatar(gameState, victim, aiAvatar)) {
                    score += 220;
                }
            }
        } else if (name.equals("beamshock") && target.isOccupied()) {
            Unit victim = target.getOccupant();
            score += estimateThreat(gameState, victim) * 10;
            if (isAdjacentToAvatar(gameState, victim, aiAvatar)) score += 120;
            if (canThreatenHeroNextTurn(gameState, victim)) score += 80;
            if (defensiveMode && isAdjacentToAvatar(gameState, victim, aiAvatar)) {
                score += 180;
            }
        } else if (name.equals("sundrop elixir") && target.isOccupied()) {
            Unit ally = target.getOccupant();
            int missingHealth = ally.getMaxHealth() - ally.getHealth();
            score += missingHealth * 18;
            if (ally == aiAvatar) score += 120;
            if (ally.getHealth() <= 3) score += 80;
            if (isAdjacentToAvatar(gameState, ally, enemyAvatar)) score += 30;
            if (defensiveMode && ally == aiAvatar) {
                score += 260;
            }
        } else if (name.equals("wraithling swarm") || name.equals("horn of the forsaken")) {
            if (enemyAvatarCell != null) {
                score += Math.max(0, 18 - distance(target, enemyAvatarCell) * 3);
            }
            score += countAdjacentEnemies(gameState, target, AI_OWNER) * 12;
            score += countAdjacentAllies(gameState, target, AI_OWNER) * 8;
            if (defensiveMode) {
                score += defensiveSummonScore(gameState, target);
            }
        }

        return score;
    }

    private static AttackChoice chooseBestAttack(GameState gameState) {
        AttackChoice bestAttack = null;
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, AI_OWNER);

        for (Unit unit : aiUnits) {
            if (gameState.isGameOver()) return null;
            if (unit == null) continue;
            if (gameState.isUnitStunned(unit)) continue;

            if (gameState.hasAttackedThisTurn(unit)) continue;

            List<BoardCell> attackCells = gameState.getValidAttackCells(unit);
            for (BoardCell target : attackCells) {
                int score = scoreAttackTarget(gameState, unit, target) + ACTION_ATTACK_BONUS;
                if (bestAttack == null || score > bestAttack.score) {
                    bestAttack = new AttackChoice(unit, target, score);
                }
            }
        }

        return bestAttack;
    }

    private static MoveChoice chooseBestMove(GameState gameState) {
        MoveChoice bestMove = null;
        List<Unit> aiUnits = getUnitsOwnedBy(gameState, AI_OWNER);

        for (Unit unit : aiUnits) {
            if (unit == null) continue;
            if (gameState.isUnitStunned(unit)) continue;
            if (gameState.hasMovedThisTurn(unit)) continue;

            List<BoardCell> moveCells = gameState.getValidMoveCells(unit);
            for (BoardCell destination : moveCells) {
                int score = scoreMove(gameState, unit, destination) + ACTION_MOVE_BONUS;
                if (bestMove == null || score > bestMove.score) {
                    bestMove = new MoveChoice(unit, destination, score);
                }
            }
        }

        return bestMove;
    }

    private static int scoreAttackTarget(GameState gameState, Unit attacker, BoardCell targetCell) {
        if (attacker == null || targetCell == null || !targetCell.isOccupied()) return Integer.MIN_VALUE;

        Unit defender = targetCell.getOccupant();
        BoardCell attackerCell = gameState.getCellForUnit(attacker);
        boolean defensiveMode = shouldEnterDefensiveMode(gameState);
        if (attackerCell == null) return Integer.MIN_VALUE;

        if (defender == gameState.getPlayer1Avatar()) {
            if (defender.getHealth() <= attacker.getAttack()) {
                return HERO_KILL_SCORE;
            }
            int remainingHeroHealth = defender.getHealth() - attacker.getAttack();
            int futureDamage = getAvailableAttackDamageToHero(gameState) - attacker.getAttack();
            int score = HERO_PRESSURE_SCORE + attacker.getAttack() * 25;
            if (futureDamage >= remainingHeroHealth) {
                score += HERO_KILL_SCORE / 3;
            }
            if (defensiveMode) {
                score -= 220;
            }
            return score;
        }

        int score = estimateThreat(gameState, defender) * 10;
        if (defender.getHealth() <= attacker.getAttack()) score += 220;

        boolean defenderCanCounter = gameState.areAdjacent(attackerCell, targetCell, true);
        boolean attackerDies = defenderCanCounter && attacker.getHealth() <= defender.getAttack();
        boolean defenderDies = defender.getHealth() <= attacker.getAttack();

        if (attackerDies && !defenderDies) {
            score -= estimateThreat(gameState, attacker) * 9;
            score -= 180;
        } else if (attackerDies) {
            score -= Math.max(40, estimateThreat(gameState, attacker) * 4);
        } else {
            score += 60;
        }

        if (isAdjacentToAvatar(gameState, defender, gameState.getPlayer2Avatar())) {
            score += 100;
        }

        if (canThreatenHeroNextTurn(gameState, defender)) {
            score += 60;
        }
        if (defensiveMode && isAdjacentToAvatar(gameState, defender, gameState.getPlayer2Avatar())) {
            score += 260;
        }

        return score;
    }

    private static int scoreMove(GameState gameState, Unit unit, BoardCell destination) {
        if (unit == null || destination == null) return Integer.MIN_VALUE;

        Unit enemyAvatar = gameState.getPlayer1Avatar();
        Unit aiAvatar = gameState.getPlayer2Avatar();
        BoardCell aiAvatarCell = gameState.getCellForUnit(aiAvatar);
        BoardCell enemyAvatarCell = gameState.getCellForUnit(enemyAvatar);
        boolean defensiveMode = shouldEnterDefensiveMode(gameState);
        int score = 0;

        if (enemyAvatarCell != null) {
            score += Math.max(0, 25 - distance(destination, enemyAvatarCell) * 4);
        }

        int adjacentEnemyCount = countAdjacentEnemies(gameState, destination, AI_OWNER);
        int adjacentAllyCount = countAdjacentEnemies(gameState, destination, HUMAN_OWNER);

        score += adjacentEnemyCount * 70;
        score -= adjacentAllyCount * 18;

        if (isAdjacentToCell(destination, gameState.getCellForUnit(aiAvatar))) {
            score += 35;
        }

        if (enemyAvatarCell != null && isAdjacentToCell(destination, enemyAvatarCell)) {
            score += HERO_PRESSURE_SCORE;
        }

        score += countThreatenedEnemiesFromCell(gameState, destination, unit.getAttack()) * 35;
        if (enemyAvatarCell != null && couldAttackHeroSoon(destination, enemyAvatarCell)) {
            score += 160;
        }
        if (defensiveMode) {
            score += scoreDefensiveRetreat(gameState, destination, aiAvatarCell, enemyAvatarCell);
        }
        return score;
    }

    private static int evaluateDamageSpellTarget(GameState gameState, Unit victim, int damage, boolean canHitHero) {
        if (victim == null) return Integer.MIN_VALUE;
        if (victim == gameState.getPlayer1Avatar()) {
            if (!canHitHero) return Integer.MIN_VALUE;
            if (victim.getHealth() <= damage) return HERO_KILL_SCORE;
            return HERO_PRESSURE_SCORE + damage * 20;
        }

        int score = estimateThreat(gameState, victim) * 10;
        if (victim.getHealth() <= damage) score += 180;
        if (isAdjacentToAvatar(gameState, victim, gameState.getPlayer2Avatar())) score += 80;
        return score;
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

    private static int estimateThreat(GameState gameState, Unit unit) {
        if (unit == null) return 0;
        int threat = unit.getAttack() * 3 + unit.getHealth();

        if (unit == gameState.getPlayer1Avatar()) {
            threat += 25;
        }

        if (isAdjacentToAvatar(gameState, unit, gameState.getPlayer2Avatar())) {
            threat += 15;
        }

        return threat;
    }

    private static int getAvailableAttackDamageToHero(GameState gameState) {
        int damage = 0;
        for (Unit unit : getUnitsOwnedBy(gameState, AI_OWNER)) {
            if (unit == null) continue;
            if (gameState.isUnitStunned(unit)) continue;
            if (gameState.hasAttackedThisTurn(unit)) continue;

            List<BoardCell> attackCells = gameState.getValidAttackCells(unit);
            for (BoardCell cell : attackCells) {
                if (cell.isOccupied() && cell.getOccupant() == gameState.getPlayer1Avatar()) {
                    damage += unit.getAttack();
                    break;
                }
            }
        }
        return damage;
    }

    private static boolean shouldEnterDefensiveMode(GameState gameState) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        if (aiAvatar == null) return false;

        int aiHealth = aiAvatar.getHealth();
        int humanThreat = estimateHumanThreatToAiHero(gameState);
        int nearbyEnemies = countEnemiesNearAiAvatar(gameState);

        return aiHealth <= DANGER_HEALTH_THRESHOLD
                && (humanThreat >= aiHealth - 1 || nearbyEnemies >= 2);
    }

    private static int estimateHumanThreatToAiHero(GameState gameState) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        if (aiAvatar == null) return 0;

        int damage = 0;
        for (Unit unit : getUnitsOwnedBy(gameState, HUMAN_OWNER)) {
            if (unit == null) continue;
            if (gameState.isUnitStunned(unit)) continue;

            BoardCell unitCell = gameState.getCellForUnit(unit);
            BoardCell aiCell = gameState.getCellForUnit(aiAvatar);
            if (unitCell == null || aiCell == null) continue;

            if (distance(unitCell, aiCell) <= 2) {
                damage += unit.getAttack();
            }
        }
        return damage;
    }

    private static int countEnemiesNearAiAvatar(GameState gameState) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        BoardCell aiCell = gameState.getCellForUnit(aiAvatar);
        if (aiCell == null) return 0;

        int count = 0;
        for (BoardCell neighbor : gameState.getAdjacentCells(aiCell.getX(), aiCell.getY(), true)) {
            if (neighbor.isOccupied() && gameState.getUnitOwner(neighbor.getOccupant()) == HUMAN_OWNER) {
                count++;
            }
        }
        return count;
    }

    private static int defensiveSummonScore(GameState gameState, BoardCell target) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        BoardCell aiCell = gameState.getCellForUnit(aiAvatar);
        if (aiCell == null || target == null) return 0;

        int score = 0;
        if (distance(target, aiCell) <= 1) {
            score += 180;
        } else if (distance(target, aiCell) == 2) {
            score += 90;
        }

        score += countAdjacentEnemies(gameState, target, AI_OWNER) * 40;
        return score;
    }

    private static int scoreDefensiveRetreat(GameState gameState, BoardCell destination, BoardCell aiAvatarCell, BoardCell enemyAvatarCell) {
        if (destination == null || aiAvatarCell == null) return 0;

        int score = 0;
        score -= countAdjacentEnemies(gameState, destination, AI_OWNER) * 140;
        score += countAdjacentAllies(gameState, destination, AI_OWNER) * 45;

        int distFromAi = distance(destination, aiAvatarCell);
        if (distFromAi <= 1) {
            score += 130;
        } else if (distFromAi == 2) {
            score += 60;
        }

        if (enemyAvatarCell != null) {
            score -= Math.max(0, 20 - distance(destination, enemyAvatarCell) * 5);
        }

        return score;
    }

    private static int countAdjacentEnemies(GameState gameState, BoardCell center, int ownerOfCenter) {
        if (center == null) return 0;
        int enemyOwner = ownerOfCenter == AI_OWNER ? HUMAN_OWNER : AI_OWNER;
        int count = 0;

        for (BoardCell neighbor : gameState.getAdjacentCells(center.getX(), center.getY(), true)) {
            if (neighbor.isOccupied() && gameState.getUnitOwner(neighbor.getOccupant()) == enemyOwner) {
                count++;
            }
        }

        return count;
    }

    private static int countAdjacentAllies(GameState gameState, BoardCell center, int owner) {
        if (center == null) return 0;
        int count = 0;

        for (BoardCell neighbor : gameState.getAdjacentCells(center.getX(), center.getY(), true)) {
            if (neighbor.isOccupied() && gameState.getUnitOwner(neighbor.getOccupant()) == owner) {
                count++;
            }
        }

        return count;
    }

    private static int countThreatenedEnemiesFromCell(GameState gameState, BoardCell origin, int attack) {
        if (origin == null) return 0;
        int count = 0;

        for (BoardCell neighbor : gameState.getAdjacentCells(origin.getX(), origin.getY(), true)) {
            if (!neighbor.isOccupied()) continue;
            Unit unit = neighbor.getOccupant();
            if (gameState.getUnitOwner(unit) != HUMAN_OWNER) continue;
            if (unit == gameState.getPlayer1Avatar() || unit.getHealth() <= attack) {
                count++;
            }
        }

        return count;
    }

    private static boolean canThreatenHeroNextTurn(GameState gameState, Unit unit) {
        if (unit == null) return false;
        BoardCell unitCell = gameState.getCellForUnit(unit);
        BoardCell heroCell = gameState.getCellForUnit(gameState.getPlayer2Avatar());
        if (unitCell == null || heroCell == null) return false;
        return distance(unitCell, heroCell) <= 2;
    }

    private static boolean couldAttackHeroSoon(BoardCell from, BoardCell heroCell) {
        if (from == null || heroCell == null) return false;
        return distance(from, heroCell) <= 2;
    }

    private static boolean isAdjacentToAvatar(GameState gameState, Unit unit, Unit avatar) {
        if (unit == null || avatar == null) return false;
        BoardCell unitCell = gameState.getCellForUnit(unit);
        BoardCell avatarCell = gameState.getCellForUnit(avatar);
        return isAdjacentToCell(unitCell, avatarCell);
    }

    private static boolean isAdjacentToCell(BoardCell a, BoardCell b) {
        if (a == null || b == null) return false;
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static int distance(BoardCell a, BoardCell b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}
