package structures;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.Card;
import structures.basic.Player;
import structures.basic.Unit;
import structures.board.BoardCell;
import structures.board.BoardCell.Highlight;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

/**
 * Extracted from the original GameState.java card-resolution code so summon,
 * spell and target-selection logic can evolve independently from the rest of
 * the state container.
 */
public final class CardResolutionService {

    private CardResolutionService() {
    }

    public static void selectCard(GameState gameState, ActorRef out, Card card, int handPosition) {
        int previousHandPosition = gameState.getSelectedHandPosition();

        gameState.setSelectedUnitInternal(null);
        gameState.getSelectedMoveCellsMutable().clear();
        gameState.getSelectedAttackCellsMutable().clear();

        gameState.setSelectedCardInternal(card);
        gameState.setSelectedHandPositionInternal(handPosition);
        gameState.markCardSelectionNow();
        gameState.getSelectedCardTargetCellsMutable().clear();

        if (card == null) {
            gameState.clearSelection(out);
            return;
        }

        if (gameState.getCurrentPlayer() == 1 && previousHandPosition >= 1 && previousHandPosition != handPosition) {
            gameState.drawHandCardNormal(out, 1, previousHandPosition);
        }

        if (card.getIsCreature()) {
            gameState.setSelectionModeCardSummon();
            gameState.getSelectedCardTargetCellsMutable().addAll(getValidSummonCellsForCurrentPlayer(gameState, card));
            applyCardTargetHighlights(gameState, out, Highlight.VALID);
        } else {
            gameState.setSelectionModeCardSpell();
            gameState.getSelectedCardTargetCellsMutable().addAll(getSpellTargetCells(gameState, card));
            Highlight mode = isHelpfulSpell(gameState, card) ? Highlight.VALID : Highlight.ATTACK;
            applyCardTargetHighlights(gameState, out, mode);
        }

        gameState.highlightHandCard(out, gameState.getCurrentPlayer(), handPosition);
    }

    public static boolean tryResolveCardActionAt(GameState gameState, ActorRef out, BoardCell cell) {
        if (gameState.getSelectedCard() == null || cell == null) {
            return false;
        }
        if (!gameState.getSelectedCardTargetCellsMutable().contains(cell)) {
            return false;
        }

        if (gameState.isSelectionModeCardSummon()) {
            return summonCreatureCardInternal(gameState, out, gameState.getSelectedCard(), cell);
        }
        if (gameState.isSelectionModeCardSpell()) {
            return castSpellCardInternal(gameState, out, gameState.getSelectedCard(), cell);
        }
        return false;
    }

    public static List<BoardCell> getValidSummonCellsForCurrentPlayer(GameState gameState) {
        return getValidSummonCellsForCurrentPlayer(gameState, null);
    }

    public static List<BoardCell> getValidSummonCellsForCurrentPlayer(GameState gameState, Card card) {
        if (gameState.hasAirdrop(card)) {
            List<BoardCell> result = new ArrayList<>();
            for (BoardCell cell : gameState.getMutableAllCells()) {
                if (cell.isEmpty()) {
                    result.add(cell);
                }
            }
            return result;
        }

        Set<BoardCell> result = new LinkedHashSet<>();
        for (BoardCell cell : gameState.getMutableAllCells()) {
            if (!cell.isOccupied()) {
                continue;
            }
            Unit unit = cell.getOccupant();
            if (gameState.getUnitOwner(unit) != gameState.getCurrentPlayer()) {
                continue;
            }

            for (BoardCell adj : gameState.getAdjacentCells(cell.getX(), cell.getY(), true)) {
                if (adj.isEmpty()) {
                    result.add(adj);
                }
            }
        }

        return new ArrayList<>(result);
    }

    public static List<BoardCell> getSpellTargetCellsForAI(GameState gameState, Card card) {
        return getSpellTargetCells(gameState, card);
    }

    public static boolean summonCreatureCard(GameState gameState, ActorRef out, Card card, BoardCell targetCell) {
        return summonCreatureCardInternal(gameState, out, card, targetCell);
    }

    public static boolean castSpellCard(GameState gameState, ActorRef out, Card card, BoardCell targetCell) {
        return castSpellCardInternal(gameState, out, card, targetCell);
    }

    public static boolean playCardForAI(GameState gameState, ActorRef out, Card card, BoardCell targetCell) {
        if (card == null) {
            return false;
        }

        List<Card> hand = gameState.getCurrentPlayerHand();
        int index = hand.indexOf(card);
        if (index < 0) {
            return false;
        }

        gameState.setSelectedCardInternal(card);
        gameState.setSelectedHandPositionInternal(index + 1);

        announceAICardPlay(gameState, out, card, targetCell);

        if (card.getIsCreature()) {
            gameState.setSelectionModeCardSummon();
            return summonCreatureCardInternal(gameState, out, card, targetCell);
        }

        gameState.setSelectionModeCardSpell();
        return castSpellCardInternal(gameState, out, card, targetCell);
    }

    private static boolean isHelpfulSpell(GameState gameState, Card card) {
        String name = gameState.normalizeCardName(card);
        return name.equals("sundrop elixir") || name.equals("wraithling swarm") || name.equals("horn of the forsaken");
    }

    private static void applyCardTargetHighlights(GameState gameState, ActorRef out, Highlight highlightMode) {
        gameState.clearAllHighlights(out);

        for (BoardCell cell : gameState.getSelectedCardTargetCellsMutable()) {
            cell.setHighlight(highlightMode);
            cell.render(out);
            gameState.getLastHighlightGrid()[cell.getX()][cell.getY()] = highlightMode;
        }
    }

    private static List<BoardCell> getSpellTargetCells(GameState gameState, Card card) {
        List<BoardCell> result = new ArrayList<>();
        String name = gameState.normalizeCardName(card);

        if (name.equals("wraithling swarm")) {
            result.addAll(getValidSummonCellsForCurrentPlayer(gameState));
            return result;
        }

        if (name.equals("horn of the forsaken")) {
            Unit avatar = (gameState.getCurrentPlayer() == 1) ? gameState.getPlayer1Avatar() : gameState.getPlayer2Avatar();
            BoardCell avatarCell = gameState.getCellForUnit(avatar);
            if (avatarCell != null) {
                result.add(avatarCell);
            }
            return result;
        }

        for (BoardCell cell : gameState.getMutableAllCells()) {
            if (!cell.isOccupied()) {
                continue;
            }

            Unit target = cell.getOccupant();
            int owner = gameState.getUnitOwner(target);

            if (name.equals("truestrike") || name.equals("beamshock") || name.equals("dark terminus")) {
                if (owner != gameState.getCurrentPlayer() && target != gameState.getPlayer1Avatar() && target != gameState.getPlayer2Avatar()) {
                    result.add(cell);
                }
            } else if (name.equals("sundrop elixir")) {
                if (owner == gameState.getCurrentPlayer() && target != gameState.getPlayer1Avatar() && target != gameState.getPlayer2Avatar()) {
                    result.add(cell);
                }
            }
        }

        return result;
    }

    private static boolean summonCreatureCardInternal(GameState gameState, ActorRef out, Card card, BoardCell targetCell) {
        if (targetCell == null || !targetCell.isEmpty()) {
            return false;
        }

        Player player = gameState.getCurrentPlayerObject();
        if (player.getMana() < card.getManacost()) {
            return false;
        }

        try {
            Unit unit = BasicObjectBuilders.loadUnit(card.getUnitConfig(), gameState.allocateUnitId(), Unit.class);
            unit.setCard(card);
            gameState.applyCardStatsToUnit(card, unit);

            if (card.getCardname().equals("Rock Pulveriser")
                    || card.getCardname().equals("Swamp Entangler")
                    || card.getCardname().equals("Silverguard Knight")
                    || card.getCardname().equals("Ironcliff Guardian")) {
                unit.setProvoke(true);
            }

            targetCell.trySetOccupant(unit);
            unit.setPositionByTile(targetCell.getTile());
            gameState.registerUnit(unit, gameState.getCurrentPlayer(), card.getCardname());

            gameState.playSummonAnimation(out, targetCell);

            BasicCommands.drawUnit(out, unit, targetCell.getTile());
            Thread.sleep(50);
            BasicCommands.setUnitHealth(out, unit, unit.getHealth());
            BasicCommands.setUnitAttack(out, unit, unit.getAttack());
            Thread.sleep(50);

            gameState.triggerOpeningGambit(out, unit);

            if (!gameState.hasRush(card)) {
                gameState.setMovedThisTurn(unit, true);
                gameState.setAttackedThisTurn(unit, true);
            }

            gameState.spendMana(out, gameState.getCurrentPlayer(), card.getManacost());
            gameState.removeCardFromCurrentHand(out, gameState.getSelectedHandPosition());

            gameState.clearSelection(out);
            gameState.clearDeathResolutionCache();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean castSpellCardInternal(GameState gameState, ActorRef out, Card card, BoardCell targetCell) {
        Player player = gameState.getCurrentPlayerObject();
        if (player.getMana() < card.getManacost()) {
            return false;
        }

        String name = gameState.normalizeCardName(card);

        try {
            if (name.equals("truestrike")) {
                if (!targetCell.isOccupied()) {
                    return false;
                }
                Unit target = targetCell.getOccupant();
                if (target == gameState.getPlayer1Avatar() || target == gameState.getPlayer2Avatar()) {
                    return false;
                }
                gameState.playSpellEffect(out, targetCell, StaticConfFiles.f1_soulshatter);
                gameState.applyDamageToUnit(out, target, 2);
                gameState.handleUnitDeathIfNeeded(out, target);
            } else if (name.equals("beamshock")) {
                if (!targetCell.isOccupied()) {
                    return false;
                }

                Unit target = targetCell.getOccupant();
                if (target == gameState.getPlayer1Avatar() || target == gameState.getPlayer2Avatar()) {
                    return false;
                }

                gameState.playSpellEffect(out, targetCell, StaticConfFiles.f1_buff);
                gameState.stunUnitUntilNextTurn(target);
                BasicCommands.addPlayer1Notification(out, "Beamshock: target stunned", 2);
            } else if (name.equals("sundrop elixir")) {
                if (!targetCell.isOccupied()) {
                    return false;
                }
                Unit target = targetCell.getOccupant();
                if (target == gameState.getPlayer1Avatar() || target == gameState.getPlayer2Avatar()) {
                    return false;
                }
                int newHealth = Math.min(target.getMaxHealth(), target.getHealth() + 5);
                target.setHealth(newHealth);
                BasicCommands.setUnitHealth(out, target, target.getHealth());
                gameState.syncAvatarHealthIfNeeded(out, target);
            } else if (name.equals("dark terminus")) {
                if (!targetCell.isOccupied()) {
                    return false;
                }

                Unit victim = targetCell.getOccupant();
                if (gameState.getUnitOwner(victim) == gameState.getCurrentPlayer()) {
                    return false;
                }
                if (victim == gameState.getPlayer1Avatar() || victim == gameState.getPlayer2Avatar()) {
                    return false;
                }

                victim.setHealth(0);
                gameState.handleUnitDeathIfNeeded(out, victim);

                Unit token = BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, gameState.allocateUnitId(), Unit.class);
                gameState.applyTokenStats(token, 1, 1);

                targetCell.trySetOccupant(token);
                token.setPositionByTile(targetCell.getTile());
                gameState.registerUnit(token, gameState.getCurrentPlayer());

                BasicCommands.drawUnit(out, token, targetCell.getTile());
                Thread.sleep(50);
                BasicCommands.setUnitHealth(out, token, token.getHealth());
                BasicCommands.setUnitAttack(out, token, token.getAttack());

                gameState.setMovedThisTurn(token, true);
                gameState.setAttackedThisTurn(token, true);
            } else if (name.equals("wraithling swarm")) {
                List<BoardCell> summonCells = getSummonCellsNearTarget(gameState, targetCell, 3);
                for (BoardCell cell : summonCells) {
                    Unit token = BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, gameState.allocateUnitId(), Unit.class);
                    gameState.applyTokenStats(token, 1, 1);

                    cell.trySetOccupant(token);
                    token.setPositionByTile(cell.getTile());
                    gameState.registerUnit(token, gameState.getCurrentPlayer(), "Wraithling");

                    BasicCommands.drawUnit(out, token, cell.getTile());
                    Thread.sleep(50);
                    BasicCommands.setUnitHealth(out, token, token.getHealth());
                    BasicCommands.setUnitAttack(out, token, token.getAttack());

                    gameState.setMovedThisTurn(token, true);
                    gameState.setAttackedThisTurn(token, true);
                }
            } else if (name.equals("horn of the forsaken")) {
                gameState.equipHorn(gameState.getCurrentPlayer());

                if (gameState.getCurrentPlayer() == 1) {
                    BasicCommands.addPlayer1Notification(out, "Player 1 equipped Horn (3 durability)", 2);
                } else {
                    BasicCommands.addPlayer1Notification(out, "Player 2 equipped Horn (3 durability)", 2);
                }
            } else {
                return false;
            }

            gameState.spendMana(out, gameState.getCurrentPlayer(), card.getManacost());
            gameState.removeCardFromCurrentHand(out, gameState.getSelectedHandPosition());
            gameState.clearSelection(out);
            gameState.clearDeathResolutionCache();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static List<BoardCell> getSummonCellsNearTarget(GameState gameState, BoardCell targetCell, int maxCount) {
        List<BoardCell> result = new ArrayList<>();
        if (targetCell == null || maxCount <= 0) {
            return result;
        }

        List<BoardCell> validCells = getValidSummonCellsForCurrentPlayer(gameState);
        if (!validCells.contains(targetCell)) {
            return result;
        }

        result.add(targetCell);

        for (BoardCell cell : gameState.getAdjacentCells(targetCell.getX(), targetCell.getY(), true)) {
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

    private static void announceAICardPlay(GameState gameState, ActorRef out, Card card, BoardCell targetCell) {
        if (gameState.getCurrentPlayer() != 2 || card == null) {
            return;
        }

        String cardName = card.getCardname();
        String targetDescription = gameState.describeTarget(targetCell);
        String message;

        if (card.getIsCreature()) {
            message = "AI summoned " + cardName + gameState.formatTargetSuffix(targetDescription);
        } else {
            message = "AI cast " + cardName + gameState.formatTargetSuffix(targetDescription);
        }

        System.out.println("[AI] " + message);
        BasicCommands.addPlayer1Notification(out, message, 2);
    }
}
