package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OrbitalStrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpaceTravel {
    public static final ResourceKey<Level> SPACE = key("space");
    public static final ResourceKey<Level> MOON = key("moon");
    public static final ResourceKey<Level> MARS = key("mars");
    private static final String REENTRY = "orbitalstrike_reentry";
    private static final int STATION_Y = 100;

    private static ResourceKey<Level> key(String name) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(OrbitalStrikeMod.MODID, name));
    }

    /** 1.0 = पृथ्वी जैसी ग्रेविटी */
    public static double gravity(ResourceKey<Level> dim) {
        if (dim == SPACE) return 0.03;
        if (dim == MOON) return 0.17;
        if (dim == MARS) return 0.38;
        return 1.0;
    }

    // ---------------------------------------------------------------- यात्रा
    public static void travel(ServerPlayer sp, Destination dest) {
        MinecraftServer server = sp.server;
        RandomSource rnd = sp.getRandom();
        switch (dest) {
            case SPACE -> {
                ServerLevel l = server.getLevel(SPACE);
                if (l == null) { missing(sp); return; }
                buildStation(l);
                sp.teleportTo(l, 0.5, STATION_Y + 1, 0.5, sp.getYRot(), 0);
            }
            case MOON, MARS -> {
                ServerLevel l = server.getLevel(dest == Destination.MOON ? MOON : MARS);
                if (l == null) { missing(sp); return; }
                int x = rnd.nextInt(129) - 64, z = rnd.nextInt(129) - 64;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (y <= l.getMinBuildHeight() + 2) {
                    y = 80;
                    platform(l, new BlockPos(x, y - 1, z), 3, Blocks.SMOOTH_STONE.defaultBlockState());
                }
                sp.teleportTo(l, x + 0.5, y + 45, z + 0.5, sp.getYRot(), 30);
                startReentry(sp, false);
            }
            case EARTH, EARTH_RANDOM -> {
                ServerLevel ow = server.overworld();
                BlockPos base = ow.getSharedSpawnPos();
                if (dest == Destination.EARTH && sp.getRespawnDimension() == Level.OVERWORLD && sp.getRespawnPosition() != null) {
                    base = sp.getRespawnPosition();
                }
                if (dest == Destination.EARTH_RANDOM) {
                    BlockPos best = base;
                    for (int i = 0; i < 12; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2;
                        int dist = 2500 + rnd.nextInt(7500);
                        BlockPos c = base.offset((int) (Math.cos(a) * dist), 0, (int) (Math.sin(a) * dist));
                        best = c;
                        if (!ow.getBiome(c.atY(64)).is(BiomeTags.IS_OCEAN)) break;
                    }
                    base = best;
                }
                int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
                sp.teleportTo(ow, base.getX() + 0.5, Math.min(y + 90, ow.getMaxBuildHeight() + 40), base.getZ() + 0.5, sp.getYRot(), 30);
                startReentry(sp, true);
            }
        }
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 25));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("orbitalstrike.title." + dest.id).withStyle(dest.color, ChatFormatting.BOLD)));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("orbitalstrike.subtitle." + dest.id).withStyle(ChatFormatting.WHITE)));
        sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8F, 1.0F);
        sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0F, 0.6F);
    }

    private static void missing(ServerPlayer sp) {
        sp.sendSystemMessage(Component.literal("Dimension not found - is the mod installed correctly?").withStyle(ChatFormatting.RED));
    }

    private static void startReentry(ServerPlayer sp, boolean fire) {
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 40, 0, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 45, 0, false, false));
        CompoundTag tag = sp.getPersistentData();
        tag.putInt(REENTRY, fire ? 2 : 1);
    }

    // ---------------------------------------------------------------- स्पेस स्टेशन
    private static void buildStation(ServerLevel l) {
        BlockPos c = new BlockPos(0, STATION_Y, 0);
        if (!l.getBlockState(c).isAir()) return;
        BlockState floor = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState trim = Blocks.CYAN_CONCRETE.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        int R = 9;
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 0.5) continue;
                BlockState s = d > R - 0.8 ? trim : ((x % 4 == 0 && z % 4 == 0) ? light : floor);
                l.setBlockAndUpdate(c.offset(x, 0, z), s);
                if (d > R - 0.8) l.setBlockAndUpdate(c.offset(x, 1, z), glass);
            }
        }
        // चार दिशाओं में "आर्म" और बीच में ऊँचा एंटीना
        for (int i = R + 1; i <= R + 6; i++) {
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                l.setBlockAndUpdate(c.offset(dir[0] * i, 0, dir[1] * i), Blocks.IRON_BLOCK.defaultBlockState());
                if (i == R + 6) {
                    l.setBlockAndUpdate(c.offset(dir[0] * i, 1, dir[1] * i), Blocks.END_ROD.defaultBlockState());
                    for (int s = -2; s <= 2; s++) {
                        l.setBlockAndUpdate(c.offset(dir[0] * i + dir[1] * s, -1, dir[1] * i + dir[0] * s), Blocks.BLUE_STAINED_GLASS.defaultBlockState());
                    }
                }
            }
        }
        for (int y = 1; y <= 4; y++) l.setBlockAndUpdate(c.offset(4, y, 4), Blocks.IRON_BARS.defaultBlockState());
        l.setBlockAndUpdate(c.offset(4, 5, 4), Blocks.BEACON.defaultBlockState());
        l.setBlockAndUpdate(c, floor);
    }

    private static void platform(ServerLevel l, BlockPos c, int r, BlockState s) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                l.setBlockAndUpdate(c.offset(x, 0, z), s);
    }

    // ---------------------------------------------------------------- ग्रेविटी (क्लाइंट + सर्वर दोनों)
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        double g = gravity(e.level().dimension());
        if (g >= 1.0 || e.onGround() || e.isNoGravity() || e.isInWater() || e.isPassenger()) return;
        if (e instanceof Player p && p.getAbilities().flying) return;
        if (e.hasEffect(MobEffects.SLOW_FALLING)) return;
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x, v.y + 0.08 * (1.0 - g), v.z);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        double g = gravity(event.getEntity().level().dimension());
        if (g < 1.0) event.setDistance((float) (event.getDistance() * g));
    }

    // ---------------------------------------------------------------- री-एंट्री आग और स्पेस में गिरना
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) return;
        ServerLevel l = sp.serverLevel();

        if (l.dimension() == SPACE && sp.getY() < 10) {
            buildStation(l);
            sp.teleportTo(l, 0.5, STATION_Y + 1, 0.5, sp.getYRot(), 0);
            sp.resetFallDistance();
        }

        CompoundTag tag = sp.getPersistentData();
        int mode = tag.getInt(REENTRY);
        if (mode == 0) return;
        if (sp.onGround() || sp.isInWater()) {
            tag.remove(REENTRY);
            sp.removeEffect(MobEffects.SLOW_FALLING);
            l.sendParticles(ParticleTypes.EXPLOSION, sp.getX(), sp.getY(), sp.getZ(), 6, 1.5, 0.2, 1.5, 0);
            l.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY() + 0.1, sp.getZ(), 40, 2, 0.1, 2, 0.15);
            l.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.6F);
            sp.displayClientMessage(Component.translatable("orbitalstrike.msg.landed").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            return;
        }
        if (mode == 2) {
            for (ServerPlayer viewer : l.players()) {
                l.sendParticles(viewer, ParticleTypes.FLAME, true, sp.getX(), sp.getY() - 0.3, sp.getZ(), 10, 0.5, 0.3, 0.5, 0.02);
                l.sendParticles(viewer, ParticleTypes.LAVA, true, sp.getX(), sp.getY(), sp.getZ(), 1, 0.3, 0.2, 0.3, 0);
                l.sendParticles(viewer, ParticleTypes.LARGE_SMOKE, true, sp.getX(), sp.getY() + 1.5, sp.getZ(), 4, 0.3, 0.6, 0.3, 0.01);
            }
            if (sp.tickCount % 10 == 0) {
                l.playSound(null, sp.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.6F);
            }
        } else {
            l.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY(), sp.getZ(), 2, 0.4, 0.4, 0.4, 0.01);
        }
    }
}
