package com.minelittlepony.unicopia.advancement;

import com.google.gson.JsonObject;
import com.minelittlepony.unicopia.Race;
import com.minelittlepony.unicopia.entity.player.Pony;

import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.EntityPredicate.Extended;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

public class RaceChangeCriterion extends AbstractCriterion<RaceChangeCriterion.Conditions> {

    private static final Identifier ID = new Identifier("unicopia", "player_change_race");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    protected Conditions conditionsFromJson(JsonObject json, Extended playerPredicate, AdvancementEntityPredicateDeserializer deserializer) {
        return new Conditions(playerPredicate, Race.fromName(JsonHelper.getString(json, "race")));
    }

    public void trigger(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity) {
            test((ServerPlayerEntity)player, c -> c.test((ServerPlayerEntity)player));
        }
    }

    public static class Conditions extends AbstractCriterionConditions {
        private final Race race;

        public Conditions(Extended playerPredicate, Race race) {
            super(ID, playerPredicate);
            this.race = race;
        }

        public boolean test(ServerPlayerEntity player) {
            return Pony.of(player).getSpecies() == race;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer serializer) {
            JsonObject json = super.toJson(serializer);
            json.addProperty("race", race.name().toLowerCase());

            return json;
        }
    }
}