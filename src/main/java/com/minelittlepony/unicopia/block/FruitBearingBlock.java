package com.minelittlepony.unicopia.block;

import java.util.*;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.minelittlepony.unicopia.USounds;
import com.minelittlepony.unicopia.ability.EarthPonyKickAbility.Buckable;
import com.minelittlepony.unicopia.compat.seasons.FertilizableUtil;
import com.minelittlepony.unicopia.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.event.GameEvent;

public class FruitBearingBlock extends LeavesBlock implements TintedBlock, Buckable {
    public static final MapCodec<FruitBearingBlock> CODEC = RecordCodecBuilder.<FruitBearingBlock>mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("overlay").forGetter(b -> b.overlay),
            CodecUtils.supplierOf(Registries.BLOCK.getCodec()).fieldOf("fruit").forGetter(b -> b.fruit),
            CodecUtils.supplierOf(ItemStack.CODEC).fieldOf("rotten_fruit").forGetter(b -> b.rottenFruitSupplier),
            BedBlock.createSettingsCodec()
    ).apply(instance, FruitBearingBlock::new));
    public static final IntProperty AGE = Properties.AGE_25;
    public static final int WITHER_AGE = 15;
    public static final EnumProperty<Stage> STAGE = EnumProperty.of("stage", Stage.class);

    public static final List<FruitBearingBlock> REGISTRY = new ArrayList<>();

    protected final Supplier<Block> fruit;
    protected final Supplier<ItemStack> rottenFruitSupplier;

    protected final int overlay;

    public FruitBearingBlock(int overlay, Supplier<Block> fruit, Supplier<ItemStack> rottenFruitSupplier, Settings settings) {
        super(settings
                .ticksRandomly()
                .nonOpaque()
                .allowsSpawning(BlockConstructionUtils::canSpawnOnLeaves)
                .suffocates(BlockConstructionUtils::never)
                .blockVision(BlockConstructionUtils::never));
        setDefaultState(getDefaultState().with(STAGE, Stage.IDLE));
        this.overlay = overlay;
        this.fruit = fruit;
        this.rottenFruitSupplier = rottenFruitSupplier;
        FlammableBlockRegistry.getDefaultInstance().add(this, 30, 60);
    }

    @Override
    public MapCodec<? extends FruitBearingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(STAGE).add(AGE);
    }

    @Override
    public boolean hasRandomTicks(BlockState state) {
        return true;
    }

    protected boolean shouldAdvance(Random random) {
        return true;
    }

    public BlockState getPlacedFruitState(Random random) {
        return fruit.get().getDefaultState();
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        super.randomTick(state, world, pos, random);

        if (shouldDecay(state) || state.get(PERSISTENT)) {
            return;
        }

        if (world.getBaseLightLevel(pos, 0) > 8) {
            BlockSoundGroup group = getSoundGroup(state);
            int steps = FertilizableUtil.getGrowthSteps(world, pos, state, random);
            while (steps-- > 0) {
                if (!shouldAdvance(random)) {
                    continue;
                }

                if (state.get(STAGE) == Stage.FRUITING) {
                    state = state.cycle(AGE);
                    if (state.get(AGE) > 20) {
                        state = state.with(AGE, 0).cycle(STAGE);
                    }
                } else {
                    state = state.with(AGE, 0).cycle(STAGE);
                }
                world.setBlockState(pos, state, Block.NOTIFY_ALL);
                BlockPos fruitPosition = pos.down();

                Stage stage = state.get(STAGE);

                if (stage == Stage.FRUITING && isPositionValidForFruit(state, pos)) {
                    if (world.isAir(fruitPosition)) {
                        world.setBlockState(fruitPosition, getPlacedFruitState(random), Block.NOTIFY_ALL);
                    }
                }

                BlockState fruitState = world.getBlockState(fruitPosition);

                if (stage == Stage.WITHERING && fruitState.isOf(fruit.get())) {
                    if (world.random.nextInt(2) == 0) {
                        Block.dropStack(world, fruitPosition, rottenFruitSupplier.get());
                    } else {
                        Block.dropStacks(fruitState, world, fruitPosition, fruitState.hasBlockEntity() ? world.getBlockEntity(fruitPosition) : null, null, ItemStack.EMPTY);
                    }

                    if (world.removeBlock(fruitPosition, false)) {
                        world.emitGameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Emitter.of(fruitState));
                    }

                    world.playSound(null, pos, USounds.ITEM_APPLE_ROT, SoundCategory.BLOCKS, group.getVolume(), group.getPitch());
                }
            }
        }
    }

    @Override
    public List<ItemStack> onBucked(ServerWorld world, BlockState state, BlockPos pos) {
        world.setBlockState(pos, state.with(STAGE, Stage.IDLE).with(AGE, 0));

        pos = pos.down();
        state = world.getBlockState(pos);
        if (state.isOf(fruit.get()) && state.getBlock() instanceof Buckable buckable) {
            return buckable.onBucked(world, state, pos);
        }
        return List.of();
    }

    @Override
    public int getTint(BlockState state, @Nullable BlockRenderView world, @Nullable BlockPos pos, int foliageColor) {
        return TintedBlock.blend(foliageColor, overlay);
    }

    public boolean isPositionValidForFruit(BlockState state, BlockPos pos) {
        return state.getRenderingSeed(pos) % 3 == 1;
    }

    public enum Stage implements StringIdentifiable {
        IDLE,
        FLOWERING,
        FRUITING,
        WITHERING;

        private static final Stage[] VALUES = values();

        public Stage getNext() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        @Override
        public String asString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
