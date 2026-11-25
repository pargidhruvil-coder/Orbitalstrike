package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * रॉकेट: राइट-क्लिक = बैठो और 5 सेकंड काउंटडाउन, फिर लॉन्च।
 * Shift + राइट-क्लिक = मंज़िल बदलो। मारो (लेफ़्ट-क्लिक) = वापस आइटम।
 */
public class RocketEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LAUNCH = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEST = SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.INT);

    public static final int COUNTDOWN = 100;
    public static final int FLIGHT = 140;
    private double padY = Double.NaN;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_LAUNCH, -1);
        this.entityData.define(DATA_DEST, Destination.SPACE.ordinal());
    }

    public int getLaunchTicks() { return this.entityData.get(DATA_LAUNCH); }
    private void setLaunchTicks(int t) { this.entityData.set(DATA_LAUNCH, t); }
    public Destination getDestination() { return Destination.byIndex(this.entityData.get(DATA_DEST)); }
    public void setDestination(Destination d) { this.entityData.set(DATA_DEST, d.ordinal()); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setDestination(Destination.byIndex(tag.getInt("Destination")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Destination", getDestination().ordinal());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() { return !this.isRemoved(); }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public double getPassengersRidingOffset() { return 1.2D; }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return this.getPassengers().isEmpty(); }

    @Override
    public boolean shouldRiderSit() { return false; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide && getLaunchTicks() < 0) {
                setDestination(getDestination().next());
                player.displayClientMessage(Component.translatable("orbitalstrike.msg.dest", getDestination().displayName()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (getLaunchTicks() >= 0 || !getPassengers().isEmpty()) return InteractionResult.PASS;
        if (!level().isClientSide && player.startRiding(this)) {
            setLaunchTicks(0);
            player.sendSystemMessage(Component.translatable("orbitalstrike.msg.boarding", getDestination().displayName())
                    .withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return true;
        if (getLaunchTicks() < 0 && source.getEntity() instanceof Player p) {
            if (!p.getAbilities().instabuild) spawnAtLocation(RocketItem.create(getDestination()));
            discard();
        }
        return true;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && getLaunchTicks() >= COUNTDOWN && passenger instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
        }
    }

    @Override
    public void tick() {
        super.tick();
        int t = getLaunchTicks();

        if (t < 0) {
            if (!onGround()) {
                setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.98));
            }
            return;
        }

        Entity rider = getFirstPassenger();
        if (!level().isClientSide) {
            if (rider == null && t < COUNTDOWN) {   // उतर गए तो लॉन्च रद्द
                setLaunchTicks(-1);
                return;
            }
            setLaunchTicks(t + 1);
        }

        if (t < COUNTDOWN) {
            countdown(t, rider);
        } else {
            int f = t - COUNTDOWN;
            if (f == 0 || Double.isNaN(padY)) padY = getY();
            double speed = Math.min(2.8, 0.02 + f * f * 0.0009);
            setDeltaMovement(0, speed, 0);
            setPos(getX(), getY() + speed, getZ());
            flight(f);
            if (!level().isClientSide && (f > FLIGHT || getY() > level().getMaxBuildHeight() + 200)) {
                arrive();
            }
        }
    }

    private void countdown(int t, Entity rider) {
        Level level = level();
        RandomSource r = level.random;
        if (level.isClientSide) {
            float p = t / (float) COUNTDOWN;
            for (int i = 0; i < 2 + p * 6; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * 0.8, getY() + 0.1, getZ() + Math.sin(a) * 0.8,
                        Math.cos(a) * 0.15 * p, 0.01, Math.sin(a) * 0.15 * p);
            }
            if (t > COUNTDOWN - 30) {
                level.addParticle(ParticleTypes.FLAME, getX() + r.nextGaussian() * 0.2, getY() - 0.1, getZ() + r.nextGaussian() * 0.2, 0, -0.1, 0);
            }
            return;
        }
        if (t % 20 == 0) {
            int sec = (COUNTDOWN - t) / 20;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 2.0F, 0.8F + (5 - sec) * 0.2F);
            if (rider instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("orbitalstrike.msg.tminus", sec).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
            }
        }
        if (t == COUNTDOWN - 40) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 3.0F, 0.5F);
        }
    }

    private void flight(int f) {
        Level level = level();
        RandomSource r = level.random;
        if (level.isClientSide) {
            for (int i = 0; i < 14; i++) {
                level.addParticle(ParticleTypes.FLAME, getX() + r.nextGaussian() * 0.25, getY() - 0.2, getZ() + r.nextGaussian() * 0.25,
                        r.nextGaussian() * 0.05, -0.8 - r.nextDouble(), r.nextGaussian() * 0.05);
            }
            for (int i = 0; i < 4; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, getX() + r.nextGaussian() * 0.4, getY() - 1.0, getZ() + r.nextGaussian() * 0.4,
                        r.nextGaussian() * 0.02, -0.05, r.nextGaussian() * 0.02);
            }
            level.addParticle(ParticleTypes.LAVA, getX(), getY() - 0.3, getZ(), 0, 0, 0);
            if (f < 50 && !Double.isNaN(padY)) {   // ज़मीन पर धुएँ का घेरा
                for (int i = 0; i < 10; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    level.addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a), padY + 0.2, getZ() + Math.sin(a),
                            Math.cos(a) * 0.6, 0.02, Math.sin(a) * 0.6);
                }
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level;
        if (f == 0) {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 2, 0.5, 0.2, 0.5, 0);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 6.0F, 0.5F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.NEUTRAL, 6.0F, 0.5F);
            if (getFirstPassenger() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("orbitalstrike.msg.liftoff").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
        if (f % 4 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 4.0F, 0.5F);
        }
        if (f % 20 == 10) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, 1.0F, 0.6F);
        }
    }

    private void arrive() {
        Entity rider = getFirstPassenger();
        Destination dest = getDestination();
        ejectPassengers();
        if (rider instanceof ServerPlayer sp) {
            SpaceTravel.travel(sp, dest);
            if (!sp.getInventory().add(RocketItem.create(dest))) {
                sp.drop(RocketItem.create(dest), false);
            }
        }
        discard();
    }
}
