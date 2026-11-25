package com.orbitalstrike;

import com.orbitalstrike.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * नवग्रह स्ट्राइक का सर्वर हिस्सा: टाइमिंग, तूफ़ान/बिजली, प्रहार, गड्ढा, डैमेज।
 * दिखने वाले बड़े इफ़ेक्ट (ग्रह, वज्र, बीम) क्लाइंट पर StrikeFxPacket से बनते हैं।
 */
@Mod.EventBusSubscriber(modid = OrbitalStrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StrikeManager {
    private static final List<Strike> STRIKES = new ArrayList<>();
    private static final List<Crater> CRATERS = new ArrayList<>();
    private static int nextId = 1;
    private static final double FX_RANGE = 400;

    private static class Strike {
        final int id;
        final ServerLevel level;
        final Vec3 pos;
        final UUID owner;
        final Timeline tl;
        int age = 0;

        Strike(int id, ServerLevel level, Vec3 pos, UUID owner, Timeline tl) {
            this.id = id; this.level = level; this.pos = pos; this.owner = owner; this.tl = tl;
        }
    }

    public static boolean hasActiveStrike(UUID owner) {
        for (Strike s : STRIKES) if (s.owner.equals(owner) && s.age < s.tl.end) return true;
        return false;
    }

    public static void fire(ServerPlayer shooter, Vec3 target, int mode) {
        Strike s = new Strike(nextId++, shooter.serverLevel(), target, shooter.getUUID(), Timeline.of(mode));
        STRIKES.add(s);
        broadcast(s);
    }

    private static void broadcast(Strike s) {
        boolean cine = Config.CINEMATIC.get();
        for (ServerPlayer p : s.level.players()) {
            boolean isShooter = p.getUUID().equals(s.owner);
            if (isShooter || p.position().distanceTo(s.pos) < FX_RANGE) {
                ModNetwork.sendTo(p, new ModNetwork.StrikeFxPacket(s.id, s.pos.x, s.pos.y, s.pos.z, s.tl.mode, isShooter && cine, s.age));
            }
        }
    }

    /** Space दबाया -> सीधे प्रहार से थोड़ा पहले */
    public static void skip(ServerPlayer p) {
        for (Strike s : STRIKES) {
            if (s.owner.equals(p.getUUID()) && s.age < s.tl.impact - 40) {
                s.age = s.tl.impact - 40;
                broadcast(s);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<Strike> it = STRIKES.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            s.age++;
            Timeline tl = s.tl;
            int arr = s.age - tl.planetsStart - tl.planetFly;
            if (arr >= 0 && arr % tl.planetStep == 0 && arr / tl.planetStep < Timeline.PLANETS) {
                s.level.playSound(null, s.pos.x, s.pos.y + 40, s.pos.z, ModSounds.ALIGN.get(), SoundSource.PLAYERS, 6.0F,
                        0.7F + 0.08F * (arr / tl.planetStep));
            }
            if (s.age == tl.alignedAt) {
                s.level.playSound(null, s.pos.x, s.pos.y + 40, s.pos.z, ModSounds.DIVINE.get(), SoundSource.PLAYERS, 8.0F, 1.2F);
            }
            if (tl.vajraStart >= 0 && s.age >= tl.vajraStart && s.age < tl.impact) {
                storm(s);
            }
            if (s.age == tl.impact - 25) {
                s.level.playSound(null, s.pos.x, s.pos.y, s.pos.z, ModSounds.BEAM.get(), SoundSource.PLAYERS, 10.0F, 1.0F);
            }
            if (s.age == tl.impact) {
                detonate(s);
            }
            if (s.age > tl.impact && s.age < tl.impact + 80) {
                aftermath(s);
            }
            if (s.age >= Math.max(tl.end, tl.impact + 80)) {
                it.remove();
            }
        }

        Iterator<Crater> ci = CRATERS.iterator();
        while (ci.hasNext()) {
            if (ci.next().step()) ci.remove();
        }
    }

    // ---------------------------------------------------------------- तूफ़ान: बादल + बिजली
    private static void storm(Strike s) {
        ServerLevel l = s.level;
        RandomSource r = l.random;
        int t = s.age - s.tl.vajraStart;
        if (t == 0) {
            l.playSound(null, s.pos.x, s.pos.y + 30, s.pos.z, ModSounds.DIVINE.get(), SoundSource.PLAYERS, 10.0F, 0.8F);
            l.playSound(null, s.pos.x, s.pos.y + 30, s.pos.z, ModSounds.VAJRA.get(), SoundSource.PLAYERS, 8.0F, 0.7F);
        }
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 35;
            particles(l, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, s.pos.x + Math.cos(a) * d, s.pos.y + 45 + r.nextGaussian() * 3,
                    s.pos.z + Math.sin(a) * d, 1, 2, 0.5, 2, 0.01);
        }
        if (t % 14 == 0) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
            if (bolt != null) {
                double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 25;
                bolt.moveTo(s.pos.x + Math.cos(a) * d, s.pos.y, s.pos.z + Math.sin(a) * d);
                bolt.setVisualOnly(true);
                l.addFreshEntity(bolt);
            }
        }
    }

    // ---------------------------------------------------------------- प्रहार
    private static void detonate(Strike s) {
        ServerLevel l = s.level;
        Vec3 c = s.pos;
        RandomSource rnd = l.random;
        int radius = Config.CRATER_RADIUS.get();
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(s.owner);

        particles(l, ParticleTypes.FLASH, c.x, c.y + 2, c.z, 8, 3, 3, 3, 0);
        particles(l, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 20, radius * 0.5, 3, radius * 0.5, 0);
        particles(l, ParticleTypes.LAVA, c.x, c.y + 1, c.z, 200, radius * 0.4, 2, radius * 0.4, 0.6);
        particles(l, ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 14, radius * 0.4, 1, radius * 0.4, 0);
        for (int i = 0; i < 8; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
            if (bolt != null) {
                bolt.moveTo(c.x + rnd.nextGaussian() * 8, c.y, c.z + rnd.nextGaussian() * 8);
                bolt.setVisualOnly(true);
                l.addFreshEntity(bolt);
            }
        }
        l.playSound(null, c.x, c.y, c.z, ModSounds.IMPACT.get(), SoundSource.PLAYERS, 16.0F, 1.0F);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 16.0F, 0.5F);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 12.0F, 0.5F);

        // डैमेज + शॉकवेव
        double dmgR = radius * 1.6;
        float maxDmg = Config.IMPACT_DAMAGE.get().floatValue();
        AABB box = new AABB(c, c).inflate(dmgR * 2);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box)) {
            if (Config.PROTECT_OWNER.get() && e.getUUID().equals(s.owner)) continue;
            Vec3 d = e.position().subtract(c);
            double dist = d.length();
            if (dist < dmgR) {
                float dmg = (float) (maxDmg * (1.0 - dist / dmgR) + 4);
                e.hurt(owner != null ? l.damageSources().explosion(owner, owner) : l.damageSources().explosion(null, null), dmg);
                e.setSecondsOnFire(8);
            }
            if (dist < dmgR * 2 && dist > 0.1) {
                double f = 1.0 - dist / (dmgR * 2);
                e.push(d.x / dist * f * 4, 0.5 + f * 1.5, d.z / dist * f * 4);
                e.hurtMarked = true;
                if (e instanceof ServerPlayer sp) sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            }
        }

        if (Config.BREAK_BLOCKS.get()) {
            CRATERS.add(new Crater(l, BlockPos.containing(c), radius));
        }
    }

    private static void aftermath(Strike s) {
        ServerLevel l = s.level;
        Vec3 c = s.pos;
        int t = s.age - s.tl.impact;
        int radius = Config.CRATER_RADIUS.get();
        double h = Math.min(t * 1.3, 55);
        particles(l, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + h * 0.5, c.z, 8, 2, h * 0.25, 2, 0.02);
        double cap = 5 + t * 0.25;
        particles(l, ParticleTypes.LARGE_SMOKE, c.x, c.y + h, c.z, 30, cap, 2, cap, 0.05);
        particles(l, ParticleTypes.FLAME, c.x, c.y + 1, c.z, 20, radius * 0.6, 0.5, radius * 0.6, 0.03);
    }

    private static void particles(ServerLevel l, ParticleOptions type, double x, double y, double z,
                                  int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : l.players()) {
            l.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    // ---------------------------------------------------------------- शूटर की सुरक्षा
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!Config.PROTECT_OWNER.get()) return;
        Entity e = event.getEntity();
        for (Strike s : STRIKES) {
            if (s.owner.equals(e.getUUID()) && s.age < s.tl.impact + 60) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        STRIKES.clear();
        CRATERS.clear();
    }

    // ---------------------------------------------------------------- गड्ढा (कई टिक में, ताकि लैग न हो)
    private static class Crater {
        final ServerLevel level;
        final BlockPos center;
        final int r;
        int x;

        Crater(ServerLevel level, BlockPos center, int r) {
            this.level = level; this.center = center; this.r = r; this.x = -r - 2;
        }

        /** @return true जब पूरा हो जाए */
        boolean step() {
            RandomSource rnd = level.random;
            BlockState[] scorch = {Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
                    Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState()};
            double ry = r * 0.6;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int n = 0; n < 2 && x <= r + 2; n++, x++) {
                for (int z = -r - 2; z <= r + 2; z++) {
                    for (int y = (int) -ry - 2; y <= r; y++) {
                        double nx = x / (double) r, nz = z / (double) r;
                        double ny = y < 0 ? y / ry : y / (double) r;
                        double d = Math.sqrt(nx * nx + ny * ny + nz * nz) + (rnd.nextDouble() - 0.5) * 0.06;
                        p.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        if (!level.isInWorldBounds(p)) continue;
                        BlockState st = level.getBlockState(p);
                        if (st.isAir() || st.getDestroySpeed(level, p) < 0) continue;
                        if (d < 1.0) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        } else if (d < 1.12 && y <= 1) {
                            level.setBlock(p, scorch[rnd.nextInt(scorch.length)], Block.UPDATE_CLIENTS);
                            BlockPos up = p.above();
                            if (rnd.nextInt(6) == 0 && level.getBlockState(up).isAir()) {
                                level.setBlock(up, BaseFireBlock.getState(level, up), Block.UPDATE_ALL);
                            }
                        }
                    }
                }
            }
            return x > r + 2;
        }
    }

    @SuppressWarnings("unused")
    private static Component msg(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD);
    }
}
