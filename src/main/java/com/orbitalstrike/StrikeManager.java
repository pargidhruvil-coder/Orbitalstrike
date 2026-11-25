package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * हर ऑर्बिटल स्ट्राइक के तीन फेज़:
 * 1) CHARGE  - लाल टार्गेटिंग रिंग, आसमान से पतली बीम, तेज़ होती बीप
 * 2) IMPACT  - बड़ा धमाका + धमाकों की रिंग, बिजली, फ्लैश, शॉकवेव
 * 3) AFTERMATH - मशरूम क्लाउड धुआँ, आग, छोटे सेकेंडरी धमाके
 */
@Mod.EventBusSubscriber(modid = OrbitalStrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StrikeManager {

    private static final List<Strike> STRIKES = new ArrayList<>();
    private static final int AFTERMATH_TICKS = 60;
    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 2.0F);

    private static class Strike {
        final ServerLevel level;
        final Vec3 pos;
        final UUID owner;
        final int chargeTicks;
        int age = 0;

        Strike(ServerLevel level, Vec3 pos, UUID owner, int chargeTicks) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
            this.chargeTicks = chargeTicks;
        }
    }

    public static void schedule(ServerLevel level, Vec3 pos, UUID owner) {
        STRIKES.add(new Strike(level, pos, owner, Config.CHARGE_SECONDS.get() * 20));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STRIKES.isEmpty()) return;

        Iterator<Strike> it = STRIKES.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            if (!s.level.isLoaded(BlockPos.containing(s.pos))) {
                it.remove();
                continue;
            }
            s.age++;
            if (s.age < s.chargeTicks) {
                charging(s);
            } else if (s.age == s.chargeTicks) {
                detonate(s);
            } else if (s.age < s.chargeTicks + AFTERMATH_TICKS) {
                aftermath(s);
            } else {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        STRIKES.clear();
    }

    // ---------------- फेज़ 1: चार्जिंग ----------------
    private static void charging(Strike s) {
        ServerLevel l = s.level;
        double x = s.pos.x, y = s.pos.y, z = s.pos.z;
        float progress = (float) s.age / s.chargeTicks;

        // घूमती और सिकुड़ती लाल टार्गेटिंग रिंग
        double radius = 12.0 * (1.0 - progress) + 1.5;
        for (int i = 0; i < 36; i++) {
            double a = (Math.PI * 2 * i / 36) + s.age * 0.15;
            particles(l, RED_DUST, x + Math.cos(a) * radius, y + 0.2, z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        // क्रॉसहेयर
        for (double d = -radius; d <= radius; d += 1.0) {
            particles(l, RED_DUST, x + d, y + 0.2, z, 1, 0, 0, 0, 0);
            particles(l, RED_DUST, x, y + 0.2, z + d, 1, 0, 0, 0, 0);
        }

        // आसमान से आती बीम (धीरे-धीरे मोटी होती है)
        if (s.age % 2 == 0) {
            int count = 1 + (int) (progress * 4);
            double spread = 0.05 + progress * 0.5;
            for (double h = 0; h < 160; h += 2.5) {
                particles(l, ParticleTypes.END_ROD, x, y + h, z, count, spread, 0.3, spread, 0.0);
            }
        }

        // बीप - आख़िरी हिस्से में तेज़
        int interval = progress > 0.66F ? 4 : 10;
        if (s.age % interval == 0) {
            l.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                    4.0F, 0.8F + progress * 1.2F);
        }

        // मालिक को काउंटडाउन
        if (s.age % 20 == 0) {
            ServerPlayer p = owner(s);
            if (p != null) {
                int secondsLeft = (s.chargeTicks - s.age + 19) / 20;
                p.displayClientMessage(Component.translatable("orbitalstrike.msg.countdown", secondsLeft)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
    }

    // ---------------- फेज़ 2: धमाका ----------------
    private static void detonate(Strike s) {
        ServerLevel l = s.level;
        Vec3 c = s.pos;
        RandomSource rnd = l.random;
        float power = Config.POWER.get().floatValue();
        boolean breakBlocks = Config.BREAK_BLOCKS.get();
        Level.ExplosionInteraction mode = breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        ServerPlayer src = owner(s);

        // सफ़ेद फ्लैश + मोटी बीम
        particles(l, ParticleTypes.FLASH, c.x, c.y + 2, c.z, 6, 2, 2, 2, 0);
        for (double h = 0; h < 200; h += 1.5) {
            particles(l, ParticleTypes.END_ROD, c.x, c.y + h, c.z, 8, 1.2, 0.5, 1.2, 0.05);
        }

        // मुख्य धमाका
        l.explode(src, c.x, c.y, c.z, power, breakBlocks, mode);

        // धमाकों की रिंग
        int ring = 8;
        double r = power * 0.7;
        for (int i = 0; i < ring; i++) {
            double a = Math.PI * 2 * i / ring;
            l.explode(src, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, power * 0.55F, breakBlocks, mode);
        }

        // बिजली (सिर्फ़ दिखावटी)
        for (int i = 0; i < 6; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
            if (bolt != null) {
                bolt.moveTo(c.x + (rnd.nextDouble() - 0.5) * 14, c.y, c.z + (rnd.nextDouble() - 0.5) * 14);
                bolt.setVisualOnly(true);
                l.addFreshEntity(bolt);
            }
        }

        // पार्टिकल्स
        particles(l, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 14, power * 0.5, 2, power * 0.5, 0);
        particles(l, ParticleTypes.LAVA, c.x, c.y + 1, c.z, 120, power * 0.4, 1, power * 0.4, 0.5);
        particles(l, ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 10, power * 0.3, 0.5, power * 0.3, 0);

        // आवाज़ें
        l.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 16.0F, 0.5F);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 16.0F, 0.6F);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 10.0F, 0.5F);

        // शॉकवेव: लोग/मॉब्स दूर उड़ते हैं, खिलाड़ियों को चक्कर
        double waveRadius = power * 3.0;
        AABB box = new AABB(c, c).inflate(waveRadius);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box)) {
            Vec3 d = e.position().subtract(c);
            double dist = d.length();
            if (dist < 0.1 || dist > waveRadius) continue;
            double f = 1.0 - dist / waveRadius;
            e.push(d.x / dist * f * 3.0, 0.4 + f * 1.2, d.z / dist * f * 3.0);
            e.hurtMarked = true;
            if (e instanceof ServerPlayer sp) {
                sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            }
        }

        // आसपास आग
        if (breakBlocks) {
            int rad = (int) power;
            for (int i = 0; i < 60; i++) {
                BlockPos p = BlockPos.containing(c.x + rnd.nextInt(rad * 2 + 1) - rad, c.y + 6,
                        c.z + rnd.nextInt(rad * 2 + 1) - rad);
                for (int k = 0; k < 16 && l.isEmptyBlock(p.below()); k++) p = p.below();
                if (l.isEmptyBlock(p) && !l.isEmptyBlock(p.below())) {
                    l.setBlockAndUpdate(p, BaseFireBlock.getState(l, p));
                }
            }
        }

        if (src != null) {
            src.displayClientMessage(Component.translatable("orbitalstrike.msg.impact")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
    }

    // ---------------- फेज़ 3: मशरूम क्लाउड ----------------
    private static void aftermath(Strike s) {
        ServerLevel l = s.level;
        Vec3 c = s.pos;
        int t = s.age - s.chargeTicks;
        float power = Config.POWER.get().floatValue();
        double h = Math.min(t * 1.2, 45);

        // तना
        particles(l, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + h * 0.5, c.z, 6, 1.5, h * 0.25, 1.5, 0.02);
        // छतरी
        double cap = 4 + t * 0.2;
        particles(l, ParticleTypes.LARGE_SMOKE, c.x, c.y + h, c.z, 25, cap, 1.5, cap, 0.05);
        particles(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + h, c.z, 8, cap * 0.7, 1.0, cap * 0.7, 0.01);
        // ज़मीन पर आग
        particles(l, ParticleTypes.FLAME, c.x, c.y + 1, c.z, 15, power * 0.5, 0.5, power * 0.5, 0.03);

        // छोटे सेकेंडरी धमाके
        if (t % 8 == 0 && t <= 32) {
            RandomSource rnd = l.random;
            double ox = (rnd.nextDouble() - 0.5) * power * 2;
            double oz = (rnd.nextDouble() - 0.5) * power * 2;
            Level.ExplosionInteraction mode = Config.BREAK_BLOCKS.get()
                    ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
            l.explode(null, c.x + ox, c.y, c.z + oz, 3.0F, false, mode);
        }
    }

    // ---------------- हेल्पर ----------------
    private static ServerPlayer owner(Strike s) {
        return s.level.getServer().getPlayerList().getPlayer(s.owner);
    }

    /** longDistance = true, ताकि 512 ब्लॉक दूर से भी धमाका दिखे */
    private static void particles(ServerLevel l, ParticleOptions type, double x, double y, double z,
                                  int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : l.players()) {
            l.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }
}
