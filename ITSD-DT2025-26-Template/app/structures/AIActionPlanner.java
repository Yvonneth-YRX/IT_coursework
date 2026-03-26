package structures;

import java.util.ArrayList;
import java.util.List;

import structures.basic.Card;
import structures.basic.Unit;
import structures.board.BoardCell;

/**
 * Decision-planning layer extracted from {@link AIController}.
 * The original controller remains as the visible coursework entry point,
 * while this class isolates action enumeration so scoring/execution concerns
 * are less coupled.
 */
final class AIActionPlanner {

    private AIActionPlanner() {
    }

    static AIActionModels.ActionChoice chooseBestAction(GameState gameState) {
        AIActionModels.AttackChoice bestAttack = chooseBestAttack(gameState);
        AIActionModels.CardChoice bestCard = chooseBestCardPlay(gameState);
        AIActionModels.MoveChoice bestMove = chooseBestMove(gameState);

        AIActionModels.ActionChoice bestAction = null;

        if (bestAttack != null) {
            bestAction = new AIActionModels.ActionChoice(bestAttack);
        }
        if (bestCard != null && (bestAction == null || bestCard.score > bestAction.score)) {
            bestAction = new AIActionModels.ActionChoice(bestCard);
        }
        if (bestMove != null && (bestAction == null || bestMove.score > bestAction.score)) {
            bestAction = new AIActionModels.ActionChoice(bestMove);
        }

        if (bestAction != null && bestAction.score < AIController.MIN_ACTION_SCORE) {
            return null;
        }

        return bestAction;
    }

    static AIActionModels.CardChoice chooseBestCardPlay(GameState gameState) {
        List<Card> handCopy = new ArrayList<>(gameState.getPlayer2Hand());
        AIActionModels.CardChoice bestChoice = null;

        for (Card card : handCopy) {
            if (card == null) continue;
            if (gameState.getPlayer2().getMana() < card.getManacost()) continue;

            List<BoardCell> targets = getCandidateTargets(gameState, card);
            for (BoardCell target : targets) {
                int score = AIController.scoreCardPlay(gameState, card, target) + AIController.ACTION_CARD_BONUS;
                if (bestChoice == null || score > bestChoice.score) {
                    bestChoice = new AIActionModels.CardChoice(card, target, score);
                }
            }
        }

        return bestChoice;
    }

    static AIActionModels.AttackChoice chooseBestAttack(GameState gameState) {
        AIActionModels.AttackChoice bestAttack = null;
        List<Unit> aiUnits = AIController.getUnitsOwnedBy(gameState, AIController.AI_OWNER);

        for (Unit unit : aiUnits) {
            if (gameState.isGameOver()) return null;
            if (unit == null) continue;
            if (gameState.isUnitStunned(unit)) continue;
            if (unit.getAttack() <= 0) continue;
            if (gameState.hasAttackedThisTurn(unit)) continue;

            List<BoardCell> attackCells = gameState.getValidAttackCells(unit);
            for (BoardCell target : attackCells) {
                int score = AIController.scoreAttackTarget(gameState, unit, target) + AIController.ACTION_ATTACK_BONUS;
                if (bestAttack == null || score > bestAttack.score) {
                    bestAttack = new AIActionModels.AttackChoice(unit, target, score);
                }
            }
        }

        return bestAttack;
    }

    static AIActionModels.MoveChoice chooseBestMove(GameState gameState) {
        AIActionModels.MoveChoice bestMove = null;
        List<Unit> aiUnits = AIController.getUnitsOwnedBy(gameState, AIController.AI_OWNER);

        for (Unit unit : aiUnits) {
            if (unit == null) continue;
            if (gameState.isUnitStunned(unit)) continue;
            if (gameState.hasMovedThisTurn(unit)) continue;

            List<BoardCell> moveCells = gameState.getValidMoveCells(unit);
            for (BoardCell destination : moveCells) {
                int score = AIController.scoreMove(gameState, unit, destination) + AIController.ACTION_MOVE_BONUS;
                if (bestMove == null || score > bestMove.score) {
                    bestMove = new AIActionModels.MoveChoice(unit, destination, score);
                }
            }
        }

        return bestMove;
    }

    static List<BoardCell> getCandidateTargets(GameState gameState, Card card) {
        if (card == null) return new ArrayList<>();
        if (card.getIsCreature()) {
            return gameState.getValidSummonCellsForCurrentPlayer(card);
        }
        return gameState.getSpellTargetCellsForAI(card);
    }
}
