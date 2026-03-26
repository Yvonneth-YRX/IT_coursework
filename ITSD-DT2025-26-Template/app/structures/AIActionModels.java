package structures;

import structures.basic.Card;
import structures.basic.Unit;
import structures.board.BoardCell;

/**
 * Small data carriers shared between the legacy {@link AIController} entry
 * point and the extracted planner layer. Keeping these in a dedicated file
 * avoids hiding the AI action model inside one large controller.
 */
final class AIActionModels {

    private AIActionModels() {
    }

    enum ActionType {
        ATTACK,
        CARD,
        MOVE
    }

    static final class ActionChoice {
        final ActionType type;
        final AttackChoice attackChoice;
        final CardChoice cardChoice;
        final MoveChoice moveChoice;
        final int score;

        ActionChoice(AttackChoice attackChoice) {
            this.type = ActionType.ATTACK;
            this.attackChoice = attackChoice;
            this.cardChoice = null;
            this.moveChoice = null;
            this.score = attackChoice.score;
        }

        ActionChoice(CardChoice cardChoice) {
            this.type = ActionType.CARD;
            this.attackChoice = null;
            this.cardChoice = cardChoice;
            this.moveChoice = null;
            this.score = cardChoice.score;
        }

        ActionChoice(MoveChoice moveChoice) {
            this.type = ActionType.MOVE;
            this.attackChoice = null;
            this.cardChoice = null;
            this.moveChoice = moveChoice;
            this.score = moveChoice.score;
        }
    }

    static final class AttackChoice {
        final Unit attacker;
        final BoardCell target;
        final int score;

        AttackChoice(Unit attacker, BoardCell target, int score) {
            this.attacker = attacker;
            this.target = target;
            this.score = score;
        }
    }

    static final class MoveChoice {
        final Unit unit;
        final BoardCell destination;
        final int score;

        MoveChoice(Unit unit, BoardCell destination, int score) {
            this.unit = unit;
            this.destination = destination;
            this.score = score;
        }
    }

    static final class CardChoice {
        final Card card;
        final BoardCell target;
        final int score;

        CardChoice(Card card, BoardCell target, int score) {
            this.card = card;
            this.target = target;
            this.score = score;
        }
    }
}
