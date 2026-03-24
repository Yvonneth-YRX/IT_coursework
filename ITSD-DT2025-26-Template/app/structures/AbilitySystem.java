package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.*;

public class AbilitySystem {

    public static void handleUnitDied(ActorRef out, GameState gameState) {

        for (Unit unit : gameState.getUnits()) {

            Card card = unit.getCard();

            if (card == null) continue;

            if (card.getAbilities() == null) continue;

            for (Ability ability : card.getAbilities()) {

                if (ability.getTrigger().equals("UNIT_DIED")) {

                    if (ability.getEffectType().equals("GAIN_ATTACK")) {

                        int newAttack = unit.getAttack() + ability.getAmount();

                        unit.setAttack(newAttack);

                        BasicCommands.setUnitAttack(out, unit, newAttack);
                    }

                }
            }

        }

    }

}