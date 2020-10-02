package com.minelittlepony.unicopia.entity.player;

import com.minelittlepony.unicopia.Race;
import com.minelittlepony.unicopia.USounds;
import com.minelittlepony.unicopia.ability.FlightPredicate;
import com.minelittlepony.unicopia.ability.magic.Spell;
import com.minelittlepony.unicopia.entity.EntityPhysics;
import com.minelittlepony.unicopia.entity.player.MagicReserves.Bar;
import com.minelittlepony.unicopia.util.NbtSerialisable;
import com.minelittlepony.unicopia.util.MutableVector;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PlayerPhysics extends EntityPhysics<Pony> implements Tickable, Motion, NbtSerialisable {

    private int ticksInAir;

    private float thrustScale = 0;

    public boolean isFlyingEither = false;
    public boolean isFlyingSurvival = false;

    private Vec3d lastPos = Vec3d.ZERO;

    private final PlayerDimensions dimensions;

    public PlayerPhysics(Pony pony) {
        super(pony);
        dimensions = new PlayerDimensions(pony, this);
    }

    @Override
    public float getGravityModifier() {
        if (pony.getOwner().getAttributes() == null) {
            // may be null due to order of execution in the contructor.
            // Will have the default (1) here in any case, so it's safe to ignore the attribute a this point.
            return super.getGravityModifier();
        }
        return super.getGravityModifier() * (float)pony.getOwner().getAttributeValue(PlayerAttributes.ENTITY_GRAVTY_MODIFIER);
    }

    @Override
    public PlayerDimensions getDimensions() {
        return dimensions;
    }

    @Override
    public boolean isFlying() {
        return isFlyingSurvival && !pony.getOwner().isFallFlying() && !pony.getOwner().hasVehicle();
    }

    @Override
    public void tick() {
        PlayerEntity entity = pony.getOwner();

        if (isGravityNegative() && !entity.isSneaking() && entity.isInSneakingPose()) {
            float currentHeight = entity.getDimensions(entity.getPose()).height;
            float sneakingHeight = entity.getDimensions(EntityPose.STANDING).height;

            entity.setPos(entity.getX(), entity.getY() + currentHeight - sneakingHeight, entity.getZ());
            entity.setPose(EntityPose.STANDING);
        }

        final MutableVector velocity = new MutableVector(entity.getVelocity());

        boolean creative = entity.abilities.creativeMode || pony.getOwner().isSpectator();

        boolean canFly = checkCanFly();

        if (!creative) {
            entity.abilities.flying |= (canFly || entity.abilities.allowFlying) && isFlyingEither;

            if ((entity.isOnGround() && entity.isSneaking()) || entity.isTouchingWater() || entity.horizontalCollision  || entity.verticalCollision) {
                entity.abilities.flying = false;
            }
        }

        isFlyingSurvival = entity.abilities.flying && !creative;
        isFlyingEither = isFlyingSurvival || (creative && entity.abilities.flying);

        if (pony.getPhysics().isGravityNegative()) {
            entity.setOnGround(!entity.world.isAir(new BlockPos(entity.getX(), entity.getY() + entity.getHeight() + 0.5F, entity.getZ())));

            if (entity.isOnGround() || entity.horizontalCollision) {
                entity.abilities.flying = false;
                isFlyingEither = false;
                isFlyingSurvival = false;
            }
        }

        if (isFlying()) {
            int level = pony.getLevel().get() + 1;

            if (ticksInAir++ > (level * 100)) {
                Bar mana = pony.getMagicalReserves().getMana();

                mana.add((int)(-getHorizontalMotion(entity) * 50 / level));

                if (mana.getPercentFill() < 0.2) {
                    pony.getMagicalReserves().getExertion().add(2);
                    pony.getMagicalReserves().getEnergy().add(2 + (int)(getHorizontalMotion(entity) * 5));

                    if (mana.getPercentFill() < 0.1 && ticksInAir % 10 == 0) {
                        float exhaustion = (0.3F * ticksInAir) / 70;
                        if (entity.isSprinting()) {
                            exhaustion *= 3.11F;
                        }

                        entity.addExhaustion(exhaustion);
                    }
                }
            }

            entity.fallDistance = 0;

            moveFlying(entity, velocity);
            if (entity.world.hasRain(entity.getBlockPos())) {
                applyTurbulance(entity, velocity);
            }

            if (entity.world.isClient && ticksInAir % 20 == 0 && entity.getVelocity().length() < 0.29) {
                entity.playSound(getWingSound(), 0.5F, 1);
                thrustScale = 1;
            }
            velocity.y -= 0.02;
        } else {
            ticksInAir = 0;

            if (!creative) {

                double horMotion = getHorizontalMotion(entity);
                double motion = entity.getPos().subtract(lastPos).lengthSquared();

                if (velocity.y > 0 && (horMotion > 0.2 || (motion > 0.2 && velocity.y < -0.2))) {
                    entity.abilities.flying = true;
                    isFlyingEither = true;
                    isFlyingSurvival = true;

                    velocity.y += horMotion + 0.3;
                    applyThrust(entity, velocity);
                }
            }
        }

        lastPos = new Vec3d(entity.getX(), 0, entity.getZ());

        entity.setVelocity(velocity.toImmutable());
    }

    private SoundEvent getWingSound() {
        return pony.getSpecies() == Race.CHANGELING ? USounds.CHANGELING_BUZZ : USounds.WING_FLAP;
    }

    protected void moveFlying(PlayerEntity player, MutableVector velocity) {
        applyThrust(player, velocity);

        double motion = getHorizontalMotion(player);

        float forward = 0.000015F * (1 + (pony.getLevel().get() / 10F)) * (float)Math.sqrt(motion);

        // vertical drop due to gravity
        forward += 0.005F;

        velocity.y -= (getGravityModifier() * 0.01F) / Math.max(motion * 100, 1);

        velocity.x += - forward * MathHelper.sin(player.yaw * 0.017453292F);
        velocity.z += forward * MathHelper.cos(player.yaw * 0.017453292F);
    }

    protected void applyThrust(PlayerEntity player, MutableVector velocity) {
        if (pony.sneakingChanged() && player.isSneaking()) {
            thrustScale = 1;
            player.playSound(getWingSound(), 0.5F, 1);
        } else {
            thrustScale *= 0.1889F;
        }

        float thrustStrength = 0.135F * thrustScale;
        Vec3d direction = player.getRotationVec(1).normalize().multiply(thrustStrength);

        velocity.x += direction.x;
        velocity.z += direction.z;
        velocity.y += direction.y * 2.45 + Math.abs(direction.y) * 10;

        if (player.isSneaking()) {
            velocity.y += 0.4 - 0.25;
            if (pony.sneakingChanged()) {
                velocity.y += 0.75;
            }
        } else {
            velocity.y -= 0.1;
        }

    }

    protected void applyTurbulance(Entity player, MutableVector velocity) {
        float glance = 360 * player.world.random.nextFloat();
        float forward = 0.015F * player.world.random.nextFloat() *  player.world.getRainGradient(1);

        if (player.world.random.nextInt(30) == 0) {
            forward *= 10;
        }
        if (player.world.random.nextInt(30) == 0) {
            forward *= 10;
        }
        if (player.world.random.nextInt(40) == 0) {
            forward *= 100;
        }

        if (player.world.isThundering() && player.world.random.nextInt(60) == 0) {
            velocity.y += forward * 3;
        }

        if (forward >= 1) {
            player.world.playSound(null, player.getBlockPos(), USounds.WIND_RUSH, SoundCategory.AMBIENT, 3, 1);
        }

        if (forward > 4) {
            forward = 4;
        }

        velocity.x += - forward * MathHelper.sin((player.yaw + glance) * 0.017453292F);
        velocity.z += forward * MathHelper.cos((player.yaw + glance) * 0.017453292F);
    }

    protected double getHorizontalMotion(Entity e) {
        return Entity.squaredHorizontalLength(e.getPos().subtract(lastPos));
    }

    private boolean checkCanFly() {
        if (pony.getOwner().abilities.creativeMode || pony.getOwner().isSpectator()) {
            return true;
        }

        if (pony.hasSpell()) {
            Spell effect = pony.getSpell(true);
            if (!effect.isDead() && effect instanceof FlightPredicate) {
                return ((FlightPredicate)effect).checkCanFly(pony);
            }
        }

        return pony.getSpecies().canFly();
    }

    public void updateFlightStat(boolean flying) {
        PlayerEntity entity = pony.getOwner();

        boolean canFly = checkCanFly();

        if (canFly || entity.abilities.allowFlying) {
            entity.abilities.flying |= flying;

            isFlyingSurvival = entity.abilities.flying;
        } else {
            entity.abilities.flying = false;
            isFlyingSurvival = false;
        }
    }

    @Override
    public void toNBT(CompoundTag compound) {
        super.toNBT(compound);
        compound.putBoolean("isFlying", isFlyingSurvival);
        compound.putBoolean("isFlyingEither", isFlyingEither);
        compound.putInt("ticksInAir", ticksInAir);
    }

    @Override
    public void fromNBT(CompoundTag compound) {
        super.fromNBT(compound);
        isFlyingSurvival = compound.getBoolean("isFlying");
        isFlyingEither = compound.getBoolean("isFlyingEither");
        ticksInAir = compound.getInt("ticksInAir");

        pony.getOwner().calculateDimensions();
    }
}
