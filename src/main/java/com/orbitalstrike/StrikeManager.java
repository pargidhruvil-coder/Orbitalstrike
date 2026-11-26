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
            l.playSound(null, s.pos.x, s.pos.y + 60, s.pos.z, ModSounds.VAJRA.get(), SoundSource.PLAYERS, 10.0F, 0.5F);
        }
        if (s.age == s.tl.impact - 120) {
            l.playSound(null, s.pos.x, s.pos.y + 60, s.pos.z, ModSounds.SHIP_CHARGE.get(), SoundSource.PLAYERS, 12.0F, 1.0F);
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
            debris(l, BlockPos.containing(c), radius);
            CRATERS.add(new Crater(l, BlockPos.containing(c), radius, Config.CRATER_DEPTH.get()));
        }
    }

    /** उड़ता मलबा: सतह के ब्लॉक हवा में उछलते हैं और दूर जाकर गिरते हैं */
    private static void debris(ServerLevel l, BlockPos c, int radius) {
        RandomSource r = l.random;
        int count = Math.min(220, 40 + radius * 3);
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * radius * 0.8;
            int x = c.getX() + (int) (Math.cos(a) * d), z = c.getZ() + (int) (Math.sin(a) * d);
            BlockPos top = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
            BlockState st = l.getBlockState(top);
            if (st.isAir() || st.getDestroySpeed(l, top) < 0 || !st.getFluidState().isEmpty() || l.getBlockEntity(top) != null) continue;
            net.minecraft.world.entity.item.FallingBlockEntity fb = net.minecraft.world.entity.item.FallingBlockEntity.fall(l, top, st);
            fb.dropItem = false;
            double sp = 0.8 + r.nextDouble() * 1.6;
            fb.setDeltaMovement(Math.cos(a) * sp, 1.0 + r.nextDouble() * 1.8, Math.sin(a) * sp);
            fb.hurtMarked = true;
        }
    }

    private static void aftermath(Strike s) {
        ServerLevel l = s.level;
        Vec3 c = s.pos;
        int t = s.age - s.tl.impact;
        int radius = Config.CRATER_RADIUS.get();
        double h = Math.min(t * 1.8, 90);
        particles(l, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + h * 0.5, c.z, 14, 3 + radius * 0.05, h * 0.25, 3 + radius * 0.05, 0.02);
        double cap = 8 + t * 0.4 + radius * 0.2;
        particles(l, ParticleTypes.LARGE_SMOKE, c.x, c.y + h, c.z, 50, cap, 3, cap, 0.05);
        particles(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + h - 4, c.z, 20, cap * 0.8, 4, cap * 0.8, 0.02);
        if (t < 30) particles(l, ParticleTypes.EXPLOSION, c.x, c.y + 2, c.z, 6, radius * 0.7, 2, radius * 0.7, 0);
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

    // ---------------------------------------------------------------- गड्ढा + शॉकवेव से साफ़ ज़मीन (कई टिक में)
    private static class Crater {
        final ServerLevel level;
        final BlockPos center;
        final int r;
        final int R2;       // शॉकवेव का दायरा
        final int depth;    // नीचे कितना गहरा
        int x;
        boolean carving = true;

        Crater(ServerLevel level, BlockPos center, int r, int depth) {
            this.level = level; this.center = center; this.r = r; this.R2 = r * 2; this.depth = depth; this.x = -r - 3;
        }

        /** @return true जब पूरा हो जाए */
        boolean step() {
            if (carving) {
                carve(Math.max(1, 3000 / ((2 * r + 6) * (depth + r + 6))));
                if (x > r + 3) { carving = false; x = -R2; }
                return false;
            }
            flatten(Math.max(1, 6000 / ((2 * R2 + 1) * 40)));
            return x > R2;
        }

        private void carve(int slices) {
            RandomSource rnd = level.random;
            BlockState[] scorch = {Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
                    Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.COAL_BLOCK.defaultBlockState(),
                    Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.CRYING_OBSIDIAN.defaultBlockState()};
            double ry = depth;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int n = 0; n < slices && x <= r + 3; n++, x++) {
                for (int z = -r - 3; z <= r + 3; z++) {
                    for (int y = (int) -ry - 3; y <= r; y++) {
                        double nx = x / (double) r, nz = z / (double) r;
                        double ny = y < 0 ? y / ry : y / (double) r;
                        double d = Math.sqrt(nx * nx + ny * ny + nz * nz) + (rnd.nextDouble() - 0.5) * 0.07;
                        p.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        if (!level.isInWorldBounds(p)) continue;
                        BlockState st = level.getBlockState(p);
                        if (st.isAir() || st.getDestroySpeed(level, p) < 0) continue;
                        if (d < 1.0) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        } else if (d < 1.14 && y <= 1) {
                            boolean bottom = y < -ry * 0.75;
                            BlockState s2 = bottom && rnd.nextInt(5) == 0 ? Blocks.LAVA.defaultBlockState() : scorch[rnd.nextInt(scorch.length)];
                            level.setBlock(p, s2, Block.UPDATE_CLIENTS);
                            BlockPos up = p.above();
                            if (rnd.nextInt(5) == 0 && level.getBlockState(up).isAir()) {
                                level.setBlock(up, BaseFireBlock.getState(level, up), Block.UPDATE_ALL);
                            }
                        }
                    }
                }
            }
        }

        private void flatten(int slices) {
            RandomSource rnd = level.random;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int n = 0; n < slices && x <= R2; n++, x++) {
                for (int z = -R2; z <= R2; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= r + 2 || d > R2) continue;
                    double strength = 1.0 - (d - r) / (R2 - r);
                    for (int y = 34; y >= -4; y--) {
                        p.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        if (!level.isInWorldBounds(p)) continue;
                        BlockState st = level.getBlockState(p);
                        if (st.isAir()) continue;
                        if (st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.LOGS)
                                || st.is(net.minecraft.tags.BlockTags.FLOWERS) || st.is(net.minecraft.tags.BlockTags.REPLACEABLE)
                                || st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK)) {
                            if (st.getFluidState().isEmpty()) {
                                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            }
                        } else if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM)) {
                            BlockState burnt = rnd.nextDouble() < strength ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                            level.setBlock(p, burnt, Block.UPDATE_CLIENTS);
                            if (rnd.nextDouble() < strength * 0.15 && level.getBlockState(p.above()).isAir()) {
                                level.setBlock(p.above(), BaseFireBlock.getState(level, p.above()), Block.UPDATE_ALL);
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static Component msg(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD);
    }
}
