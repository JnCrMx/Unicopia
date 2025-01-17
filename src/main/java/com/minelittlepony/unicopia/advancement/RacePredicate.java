package com.minelittlepony.unicopia.advancement;

import java.util.Set;
import java.util.function.Predicate;
import com.minelittlepony.unicopia.Race;
import com.minelittlepony.unicopia.entity.player.Pony;
import com.minelittlepony.unicopia.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.network.ServerPlayerEntity;

public record RacePredicate(Set<Race> include, Set<Race> exclude) implements Predicate<ServerPlayerEntity> {
    public static final RacePredicate EMPTY = new RacePredicate(Set.of(), Set.of());

    private static final Codec<Set<Race>> RACE_SET_CODEC = CodecUtils.setOf(Race.REGISTRY.getCodec());
    private static final Codec<RacePredicate> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RACE_SET_CODEC.fieldOf("include").forGetter(RacePredicate::include),
            RACE_SET_CODEC.fieldOf("exclude").forGetter(RacePredicate::exclude)
    ).apply(instance, RacePredicate::of));
    public static final Codec<RacePredicate> CODEC = CodecUtils.xor(BASE_CODEC, RACE_SET_CODEC.xmap(include -> of(include, Set.of()), RacePredicate::include));

    private static RacePredicate of(Set<Race> include, Set<Race> exclude) {
        if (include.isEmpty() && exclude.isEmpty()) {
            return EMPTY;
        }
        return new RacePredicate(include, exclude);
    }

    @Override
    public boolean test(ServerPlayerEntity player) {
        Race race = Pony.of(player).getSpecies();
        return (include.isEmpty() || include.contains(race)) && !(!exclude.isEmpty() && exclude.contains(race));
    }
}
