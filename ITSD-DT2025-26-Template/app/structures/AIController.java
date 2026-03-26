package structures;

import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.Unit;
import structures.board.BoardCell;

public class AIController {

    static final int AI_OWNER = 2;
    static final int HUMAN_OWNER = 1;
    private static final int HERO_KILL_SCORE = 100000;
    private static final int HERO_PRESSURE_SCORE = 600;
    static final int ACTION_ATTACK_BONUS = 120;
    static final int ACTION_CARD_BONUS = 80;
    static final int ACTION_MOVE_BONUS = 0;
    private static final int DANGER_HEALTH_THRESHOLD = 8;
    private static final int AI_MOVE_ANIMATION_WAIT_MS = 1200;
    private static final int AI_NON_MOVE_WAIT_MS = 350;
    private static final int AI_MOVE_LOCK_TIMEOUT_MS = 2500;
    private static final int AI_MOVE_POLL_MS = 40;
    private static final int AI_POST_MOVE_SETTLE_MS = 180;
    private static final int MAX_ACTIONS_PER_TURN = 6;
    static final int MIN_ACTION_SCORE = 1;
    private static final int BLOODMOON_PRIESTESS_SCORE = 360;
    private static final int SHADOW_WATCHER_SCORE = 300;
    private static final int PRIORITY_BLOCKER_SCORE = 170;
    private static final int OVERGROWN_SHADOW_WATCHER_ATTACK = 5;
    private static final int OVERGROWN_SHADOW_WATCHER_HEALTH = 6;
    private static final int AI_HERO_CRITICAL_HEALTH = 4;
    private static final int AI_HERO_LOW_HEALTH = 7;

    /**
     * Original AI entry point retained in the coursework-visible file.
     * Planning responsibilities are delegated to {@link AIActionPlanner},
     * while execution still happens here so the runtime flow remains easy to
     * trace from the original controller.
     */
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
        int actionCount = 0;
        while (!gameState.isGameOver() && gameState.getCurrentPlayer() == AI_OWNER && actionCount < MAX_ACTIONS_PER_TURN) {
            AIActionModels.ActionChoice bestAction = chooseBestAction(gameState);
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

            actionCount++;
            waitForActionResolution(gameState, bestAction.type);
        }
    }

    private static void waitForActionResolution(GameState gameState, AIActionModels.ActionType actionType) throws InterruptedException {
        if (actionType != AIActionModels.ActionType.MOVE) {
            Thread.sleep(AI_NON_MOVE_WAIT_MS);
            return;
        }

        long deadline = System.currentTimeMillis() + Math.max(AI_MOVE_ANIMATION_WAIT_MS, AI_MOVE_LOCK_TIMEOUT_MS);
        while (gameState.isInputLocked() && System.currentTimeMillis() < deadline) {
            Thread.sleep(AI_MOVE_POLL_MS);
        }

        Thread.sleep(AI_POST_MOVE_SETTLE_MS);
    }

    // Compatibility wrapper: keep the planning entry point visible in the
    // original coursework AIController while delegating enumeration elsewhere.
    private static AIActionModels.ActionChoice chooseBestAction(GameState gameState) {
        return AIActionPlanner.chooseBestAction(gameState);
    }

    // Compatibility wrapper retained here so the AI flow is still readable
    // from the original controller file used in the template.
    private static AIActionModels.CardChoice chooseBestCardPlay(GameState gameState) {
        return AIActionPlanner.chooseBestCardPlay(gameState);
    }

    private static List<BoardCell> getCandidateTargets(GameState gameState, Card card) {
        return AIActionPlanner.getCandidateTargets(gameState, card);
    }

    static int scoreCardPlay(GameState gameState, Card card, BoardCell target) {
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
            score += getPriorityRemovalBonus(gameState, victim, true);
            if (victim == enemyAvatar && availableAttackDamage + 2 >= enemyHeroHealth) {
                score += HERO_KILL_SCORE / 2;
            }
        } else if (name.equals("dark terminus") && target.isOccupied()) {
            Unit victim = target.getOccupant();
            if (victim == enemyAvatar) {
                score -= 1000;
            } else {
                score += 300 + estimateThreat(gameState, victim) * 12;
                score += getPriorityRemovalBonus(gameState, victim, false) + 120;
                if (victim.getAttack() >= 4) score += 120;
                if (isAdjacentToAvatar(gameState, victim, aiAvatar)) score += 120;
                if (defensiveMode && isAdjacentToAvatar(gameState, victim, aiAvatar)) {
                    score += 220;
                }
            }
        } else if (name.equals("beamshock") && target.isOccupied()) {
            Unit victim = target.getOccupant();
            score += estimateThreat(gameState, victim) * 10;
            score += getPriorityRemovalBonus(gameState, victim, false);
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

    // Compatibility wrapper retained here for the same reason as above.
    private static AIActionModels.AttackChoice chooseBestAttack(GameState gameState) {
        return AIActionPlanner.chooseBestAttack(gameState);
    }

    // Compatibility wrapper retained here for the same reason as above.
    private static AIActionModels.MoveChoice chooseBestMove(GameState gameState) {
        return AIActionPlanner.chooseBestMove(gameState);
    }

    static int scoreAttackTarget(GameState gameState, Unit attacker, BoardCell targetCell) {
        if (attacker == null || targetCell == null || !targetCell.isOccupied()) return Integer.MIN_VALUE;
        if (attacker.getAttack() <= 0) return Integer.MIN_VALUE;

        Unit defender = targetCell.getOccupant();
        BoardCell attackerCell = gameState.getCellForUnit(attacker);
        boolean defensiveMode = shouldEnterDefensiveMode(gameState);
        boolean advantageMode = shouldEnterAdvantageMode(gameState);
        boolean aiAvatarAttacking = attacker == gameState.getPlayer2Avatar();
        if (attackerCell == null) return Integer.MIN_VALUE;

        if (defender == gameState.getPlayer1Avatar()) {
            if (defender.getHealth() <= attacker.getAttack()) {
                return HERO_KILL_SCORE;
            }
            int remainingHeroHealth = defender.getHealth() - attacker.getAttack();
            int futureDamage = getAvailableAttackDamageToHero(gameState) - attacker.getAttack();
            int score = HERO_PRESSURE_SCORE + attacker.getAttack() * 25;
            if (hasPriorityThreats(gameState)) {
                score -= 320;
            }
            if (shouldRaceHeroBecauseOfShadowWatcher(gameState)) {
                score += 260;
            }
            if (futureDamage >= remainingHeroHealth) {
                score += HERO_KILL_SCORE / 3;
            }
            if (defensiveMode) {
                score -= 220;
            }
            if (aiAvatarAttacking) {
                score += advantageMode ? 80 : -180;
            }
            return score;
        }

        int score = estimateThreat(gameState, defender) * 10;
        score += getPriorityRemovalBonus(gameState, defender, false);
        if (defender.getHealth() <= attacker.getAttack()) score += 220;

        boolean defenderDies = defender.getHealth() <= attacker.getAttack();
        boolean defenderCanCounter = gameState.areAdjacent(attackerCell, targetCell, true);
        boolean attackerDies = defenderCanCounter && !defenderDies && attacker.getHealth() <= defender.getAttack();

        if (aiAvatarAttacking && attackerDies) {
            return Integer.MIN_VALUE / 2;
        }

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

        score += scoreFocusFireTarget(gameState, attacker, defender, defenderDies, attackerDies);
        if (aiAvatarAttacking) {
            score += scoreAiAvatarAttackRisk(gameState, defender, defenderDies, attackerDies);
            if (!defenderDies && !isPriorityThreat(gameState, defender)) {
                score -= advantageMode ? 120 : 320;
            }
        }

        if (isOvergrownShadowWatcher(gameState, defender)) {
            if (attacker == gameState.getPlayer2Avatar()) {
                score -= 1200;
            } else if (attacker.getHealth() <= defender.getAttack() && !defenderDies) {
                score -= 700;
            } else if (attacker.getAttack() < defender.getHealth() && attacker.getHealth() <= defender.getAttack()) {
                score -= 500;
            } else if (defenderDies && !attackerDies) {
                score += 180;
            }
        }

        return score;
    }

    private static int scoreAiAvatarAttackRisk(GameState gameState, Unit defender, boolean defenderDies, boolean attackerDies) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        if (gameState == null || aiAvatar == null || defender == null) return 0;

        if (attackerDies) {
            return Integer.MIN_VALUE / 4;
        }

        int retaliationDamage = defenderDies ? 0 : Math.max(0, defender.getAttack());
        int projectedHealth = aiAvatar.getHealth() - retaliationDamage;
        int projectedThreat = estimateHumanThreatToAiHeroAfterExchange(gameState, defender, defenderDies);
        int nearbyEnemyCount = countEnemiesNearAiAvatarAfterExchange(gameState, defender, defenderDies);
        boolean criticalThreat = isPriorityThreat(gameState, defender) || isBlockingPriorityThreat(gameState, defender);
        boolean finishesThreat = defenderDies && criticalThreat;

        int score = 0;

        if (defenderDies) {
            score += finishesThreat ? 260 : 60;
            if (isHighestPriorityThreat(gameState, defender)) {
                score += 160;
            }
        } else {
            score -= 120;
        }

        if (retaliationDamage > 0) {
            score -= retaliationDamage * 55;
        }

        if (projectedHealth <= AI_HERO_CRITICAL_HEALTH) {
            score -= finishesThreat ? 260 : 720;
        } else if (projectedHealth <= AI_HERO_LOW_HEALTH) {
            score -= finishesThreat ? 120 : 320;
        }

        if (projectedHealth <= projectedThreat) {
            score -= finishesThreat ? 220 : 620;
        } else if (projectedHealth <= projectedThreat + 2) {
            score -= finishesThreat ? 80 : 260;
        }

        if (nearbyEnemyCount >= 2) {
            score -= nearbyEnemyCount * (projectedHealth <= AI_HERO_LOW_HEALTH ? 110 : 55);
        }

        if (shouldRaceHeroBecauseOfShadowWatcher(gameState) && !defenderDies) {
            score -= 220;
        }

        if (!criticalThreat && projectedHealth <= AI_HERO_LOW_HEALTH) {
            score -= 240;
        }

        return score;
    }

    private static int scoreFocusFireTarget(GameState gameState, Unit attacker, Unit defender, boolean defenderDies, boolean attackerDies) {
        if (gameState == null || attacker == null || defender == null) return 0;

        int score = 0;
        boolean priorityThreat = isPriorityThreat(gameState, defender);

        if (!priorityThreat) {
            if (hasAttackablePriorityThreat(gameState) && !isBlockingPriorityThreat(gameState, defender)) {
                score -= 260;
            }
            return score;
        }

        int remainingDamage = getRemainingFriendlyAttackDamageToUnit(gameState, defender);
        int remainingHealthAfterThisAttack = Math.max(0, defender.getHealth() - attacker.getAttack());

        score += 220;
        if (isHighestPriorityThreat(gameState, defender)) {
            score += 140;
        }
        if (defender.getHealth() <= 3) {
            score += 120;
        }
        if (remainingDamage >= defender.getHealth()) {
            score += 220;
        }
        if (!defenderDies && remainingHealthAfterThisAttack <= Math.max(2, remainingDamage - attacker.getAttack())) {
            score += 120;
        }
        if (defenderDies) {
            score += 320;
        }

        if (attackerDies && !defenderDies) {
            score -= 240;
            if (normalize(gameState.getUnitCardName(defender)).equals("shadow watcher")) {
                score -= 420;
            }
        }

        return score;
    }

    static int scoreMove(GameState gameState, Unit unit, BoardCell destination) {
        if (unit == null || destination == null) return Integer.MIN_VALUE;

        Unit enemyAvatar = gameState.getPlayer1Avatar();
        Unit aiAvatar = gameState.getPlayer2Avatar();
        BoardCell aiAvatarCell = gameState.getCellForUnit(aiAvatar);
        BoardCell enemyAvatarCell = gameState.getCellForUnit(enemyAvatar);
        boolean defensiveMode = shouldEnterDefensiveMode(gameState);
        boolean advantageMode = shouldEnterAdvantageMode(gameState);
        int score = 0;

        if (enemyAvatarCell != null) {
            score += Math.max(0, 25 - distance(destination, enemyAvatarCell) * 4);
        }

        BoardCell nearestPriorityThreat = getNearestPriorityThreatCell(gameState, destination);
        if (nearestPriorityThreat != null) {
            int priorityDistance = distance(destination, nearestPriorityThreat);
            score += Math.max(0, 60 - priorityDistance * 14);
            if (priorityDistance <= 1) {
                score += 220;
            } else if (priorityDistance == 2) {
                score += 90;
            }
        }

        BoardCell overgrownWatcherCell = getOvergrownShadowWatcherCell(gameState);
        if (overgrownWatcherCell != null) {
            int watcherDistance = distance(destination, overgrownWatcherCell);
            if (shouldRaceHeroBecauseOfShadowWatcher(gameState)) {
                if (enemyAvatarCell != null) {
                    score += Math.max(0, 40 - distance(destination, enemyAvatarCell) * 8);
                }
                if (watcherDistance <= 1) {
                    score -= unit == aiAvatar ? 420 : 240;
                } else if (watcherDistance == 2) {
                    score -= 120;
                }
            }
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
        if (isProvokeUnit(gameState, unit)) {
            score += scoreProvokeAdvance(gameState, destination, enemyAvatarCell);
        }
        if (defensiveMode) {
            score += scoreDefensiveRetreat(gameState, destination, aiAvatarCell, enemyAvatarCell, isProvokeUnit(gameState, unit));
        }
        if (unit == aiAvatar) {
            score -= adjacentEnemyCount * (advantageMode ? 40 : 110);
            if (enemyAvatarCell != null) {
                score += Math.max(0, (advantageMode ? 28 : 14) - distance(destination, enemyAvatarCell) * 4);
            }
            BoardCell nearestHuman = getNearestHumanUnitCell(gameState, destination);
            if (nearestHuman != null) {
                score += Math.max(0, (advantageMode ? 22 : 10) - distance(destination, nearestHuman) * 4);
            }
            score += scoreCenterControl(destination) + (advantageMode ? 12 : 0);
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
        score += getPriorityRemovalBonus(gameState, victim, true);
        if (victim.getHealth() <= damage) score += 180;
        if (isAdjacentToAvatar(gameState, victim, gameState.getPlayer2Avatar())) score += 80;
        return score;
    }

    static List<Unit> getUnitsOwnedBy(GameState gameState, int owner) {
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

        String name = normalize(gameState.getUnitCardName(unit));
        if (name.equals("bloodmoon priestess")) {
            threat += 35 + countAdjacentEmptyCells(gameState, unit) * 12;
        } else if (name.equals("shadow watcher")) {
            threat += 28;
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

    private static boolean shouldEnterAdvantageMode(GameState gameState) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        Unit humanAvatar = gameState.getPlayer1Avatar();
        if (aiAvatar == null || humanAvatar == null) return false;

        int aiHealth = aiAvatar.getHealth();
        int humanHealth = humanAvatar.getHealth();
        int aiBoard = estimateBoardStrength(gameState, AI_OWNER);
        int humanBoard = estimateBoardStrength(gameState, HUMAN_OWNER);

        return aiHealth >= humanHealth
                && aiBoard >= humanBoard + 6;
    }

    private static int estimateBoardStrength(GameState gameState, int owner) {
        int strength = 0;
        for (Unit unit : getUnitsOwnedBy(gameState, owner)) {
            if (unit == null) continue;
            strength += unit.getAttack() * 2 + unit.getHealth();
        }
        return strength;
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

    private static int estimateHumanThreatToAiHeroAfterExchange(GameState gameState, Unit exchangedTarget, boolean exchangedTargetDies) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        if (gameState == null || aiAvatar == null) return 0;

        int damage = 0;
        for (Unit unit : getUnitsOwnedBy(gameState, HUMAN_OWNER)) {
            if (unit == null) continue;
            if (unit == exchangedTarget && exchangedTargetDies) continue;
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

    private static int scoreDefensiveRetreat(GameState gameState, BoardCell destination, BoardCell aiAvatarCell, BoardCell enemyAvatarCell, boolean provokeUnit) {
        if (destination == null || aiAvatarCell == null) return 0;

        int score = 0;
        score -= countAdjacentEnemies(gameState, destination, AI_OWNER) * (provokeUnit ? 40 : 140);
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

    private static int scoreProvokeAdvance(GameState gameState, BoardCell destination, BoardCell enemyAvatarCell) {
        if (destination == null) return 0;

        int score = 0;
        int adjacentEnemies = countAdjacentEnemies(gameState, destination, AI_OWNER);
        score += adjacentEnemies * 180;

        BoardCell nearestHuman = getNearestHumanUnitCell(gameState, destination);
        if (nearestHuman != null) {
            int dist = distance(destination, nearestHuman);
            score += Math.max(0, 24 - dist * 8);
            if (dist <= 1) {
                score += 180;
            } else if (dist == 2) {
                score += 80;
            }
        }

        if (enemyAvatarCell != null) {
            int heroDist = distance(destination, enemyAvatarCell);
            score += Math.max(0, 18 - heroDist * 5);
            if (heroDist <= 1) {
                score += 120;
            }
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

    private static int countEnemiesNearAiAvatarAfterExchange(GameState gameState, Unit exchangedTarget, boolean exchangedTargetDies) {
        Unit aiAvatar = gameState.getPlayer2Avatar();
        BoardCell aiCell = gameState.getCellForUnit(aiAvatar);
        if (gameState == null || aiCell == null) return 0;

        int count = 0;
        for (BoardCell neighbor : gameState.getAdjacentCells(aiCell.getX(), aiCell.getY(), true)) {
            if (!neighbor.isOccupied()) continue;
            Unit occupant = neighbor.getOccupant();
            if (occupant == exchangedTarget && exchangedTargetDies) continue;
            if (gameState.getUnitOwner(occupant) == HUMAN_OWNER) {
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

    private static int scoreCenterControl(BoardCell cell) {
        if (cell == null) return 0;
        int dx = Math.abs(cell.getX() - 4);
        int dy = Math.abs(cell.getY() - 2);
        return Math.max(0, 18 - (dx * 4 + dy * 5));
    }

    private static boolean isProvokeUnit(GameState gameState, Unit unit) {
        if (gameState == null || unit == null) return false;
        String name = normalize(gameState.getUnitCardName(unit));
        return name.equals("rock pulveriser")
                || name.equals("swamp entangler")
                || name.equals("silverguard knight")
                || name.equals("ironcliff guardian");
    }

    private static boolean hasPriorityThreats(GameState gameState) {
        return getHighestPriorityThreatCell(gameState) != null;
    }

    private static boolean hasAttackablePriorityThreat(GameState gameState) {
        if (gameState == null) return false;

        for (Unit attacker : getUnitsOwnedBy(gameState, AI_OWNER)) {
            if (attacker == null) continue;
            if (gameState.isUnitStunned(attacker)) continue;
            if (attacker.getAttack() <= 0) continue;
            if (gameState.hasAttackedThisTurn(attacker)) continue;

            for (BoardCell targetCell : gameState.getValidAttackCells(attacker)) {
                if (!targetCell.isOccupied()) continue;
                if (isPriorityThreat(gameState, targetCell.getOccupant())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int getPriorityRemovalBonus(GameState gameState, Unit unit, boolean spellReach) {
        if (gameState == null || unit == null) return 0;

        String name = normalize(gameState.getUnitCardName(unit));
        if (name.equals("bloodmoon priestess")) {
            int bonus = BLOODMOON_PRIESTESS_SCORE + countAdjacentEmptyCells(gameState, unit) * 30;
            if (spellReach) bonus += 120;
            return bonus;
        }
        if (name.equals("shadow watcher")) {
            if (isOvergrownShadowWatcher(gameState, unit)) {
                return 120;
            }
            return SHADOW_WATCHER_SCORE + (spellReach ? 80 : 0);
        }
        if (isBlockingPriorityThreat(gameState, unit)) {
            return PRIORITY_BLOCKER_SCORE;
        }
        return 0;
    }

    private static boolean isBlockingPriorityThreat(GameState gameState, Unit unit) {
        if (gameState == null || unit == null) return false;
        if (gameState.getUnitOwner(unit) != HUMAN_OWNER) return false;
        if (isPriorityThreat(gameState, unit)) return false;

        BoardCell cell = gameState.getCellForUnit(unit);
        if (cell == null) return false;

        for (BoardCell neighbor : gameState.getAdjacentCells(cell.getX(), cell.getY(), true)) {
            if (!neighbor.isOccupied()) continue;
            Unit adjacent = neighbor.getOccupant();
            if (gameState.getUnitOwner(adjacent) != HUMAN_OWNER) continue;
            if (isPriorityThreat(gameState, adjacent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPriorityThreat(GameState gameState, Unit unit) {
        if (gameState == null || unit == null) return false;
        String name = normalize(gameState.getUnitCardName(unit));
        return name.equals("bloodmoon priestess") || name.equals("shadow watcher");
    }

    private static boolean isOvergrownShadowWatcher(GameState gameState, Unit unit) {
        if (gameState == null || unit == null) return false;
        String name = normalize(gameState.getUnitCardName(unit));
        return name.equals("shadow watcher")
                && unit.getAttack() >= OVERGROWN_SHADOW_WATCHER_ATTACK
                && unit.getHealth() >= OVERGROWN_SHADOW_WATCHER_HEALTH;
    }

    private static BoardCell getOvergrownShadowWatcherCell(GameState gameState) {
        if (gameState == null) return null;
        for (Unit unit : getUnitsOwnedBy(gameState, HUMAN_OWNER)) {
            if (!isOvergrownShadowWatcher(gameState, unit)) continue;
            BoardCell cell = gameState.getCellForUnit(unit);
            if (cell != null) {
                return cell;
            }
        }
        return null;
    }

    private static boolean shouldRaceHeroBecauseOfShadowWatcher(GameState gameState) {
        if (gameState == null) return false;
        BoardCell watcherCell = getOvergrownShadowWatcherCell(gameState);
        if (watcherCell == null) return false;

        Unit humanAvatar = gameState.getPlayer1Avatar();
        if (humanAvatar == null) return false;

        int heroDamage = getAvailableAttackDamageToHero(gameState);
        return heroDamage >= 3 || humanAvatar.getHealth() <= 10;
    }

    private static BoardCell getHighestPriorityThreatCell(GameState gameState) {
        if (gameState == null) return null;

        BoardCell best = null;
        int bestWeight = Integer.MIN_VALUE;

        for (Unit unit : getUnitsOwnedBy(gameState, HUMAN_OWNER)) {
            if (!isPriorityThreat(gameState, unit)) continue;
            BoardCell cell = gameState.getCellForUnit(unit);
            if (cell == null) continue;

            String name = normalize(gameState.getUnitCardName(unit));
            int weight = name.equals("bloodmoon priestess")
                    ? 1000 + countAdjacentEmptyCells(gameState, unit) * 20
                    : 900;

            if (weight > bestWeight) {
                bestWeight = weight;
                best = cell;
            }
        }

        return best;
    }

    private static boolean isHighestPriorityThreat(GameState gameState, Unit unit) {
        if (gameState == null || unit == null) return false;
        BoardCell highestPriorityCell = getHighestPriorityThreatCell(gameState);
        BoardCell unitCell = gameState.getCellForUnit(unit);
        return highestPriorityCell != null && highestPriorityCell == unitCell;
    }

    private static BoardCell getNearestPriorityThreatCell(GameState gameState, BoardCell from) {
        if (gameState == null || from == null) return null;

        BoardCell best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestWeight = Integer.MIN_VALUE;

        for (Unit unit : getUnitsOwnedBy(gameState, HUMAN_OWNER)) {
            if (!isPriorityThreat(gameState, unit)) continue;
            BoardCell cell = gameState.getCellForUnit(unit);
            if (cell == null) continue;

            int distance = distance(from, cell);
            int weight = normalize(gameState.getUnitCardName(unit)).equals("bloodmoon priestess") ? 2 : 1;
            if (distance < bestDistance || (distance == bestDistance && weight > bestWeight)) {
                best = cell;
                bestDistance = distance;
                bestWeight = weight;
            }
        }

        return best;
    }

    private static int countAdjacentEmptyCells(GameState gameState, Unit unit) {
        if (gameState == null || unit == null) return 0;
        BoardCell cell = gameState.getCellForUnit(unit);
        if (cell == null) return 0;

        int count = 0;
        for (BoardCell neighbor : gameState.getAdjacentCells(cell.getX(), cell.getY(), true)) {
            if (neighbor.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static int getRemainingFriendlyAttackDamageToUnit(GameState gameState, Unit target) {
        if (gameState == null || target == null) return 0;

        int totalDamage = 0;
        for (Unit attacker : getUnitsOwnedBy(gameState, AI_OWNER)) {
            if (attacker == null) continue;
            if (gameState.isUnitStunned(attacker)) continue;
            if (attacker.getAttack() <= 0) continue;
            if (gameState.hasAttackedThisTurn(attacker)) continue;

            for (BoardCell cell : gameState.getValidAttackCells(attacker)) {
                if (!cell.isOccupied()) continue;
                if (cell.getOccupant() == target) {
                    totalDamage += attacker.getAttack();
                    break;
                }
            }
        }
        return totalDamage;
    }

    private static BoardCell getNearestHumanUnitCell(GameState gameState, BoardCell from) {
        if (gameState == null || from == null) return null;

        BoardCell best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Unit unit : getUnitsOwnedBy(gameState, HUMAN_OWNER)) {
            if (unit == null) continue;
            BoardCell cell = gameState.getCellForUnit(unit);
            if (cell == null) continue;
            int dist = distance(from, cell);
            if (dist < bestDist) {
                bestDist = dist;
                best = cell;
            }
        }

        return best;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static int distance(BoardCell a, BoardCell b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}
