package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * OP ऑर्बिटल कैनन गन:
 * राइट-क्लिक दबाकर रखो -> चार्ज (ज़ूम + लाल लेज़र साइट), 1 सेकंड बाद छोड़ो -> लेज़र शॉट,
 * फिर अंतरिक्ष से ऑर्बिटल स्ट्राइक।
 */
public class OrbitalCannonItem extends Item {
    public static final int FULL_CHARGE = 20;
    private static final DustParticleOptions LASER_SIGHT = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 0.7F);
    private static final DustParticleOptions BEAM_CORE = new DustParticleOptions(new Vector3f(0.3F, 0.95F, 1.0F), 1.6F);
    private static final DustParticleOptions BEAM_GLOW = new DustParticleOptions(new Vector3f(0.7F, 0.3F, 1.0F), 2.2F);

    public OrbitalCannonItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    public static float chargeProgress(LivingEntity entity) {
        return Math.min(1.0F, entity.getTicksUsingItem() / (float) FULL_CHARGE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.8F);
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        if (level.isClientSide || !(level instanceof ServerLevel server)) return;

        if (used == FULL_CHARGE) {
            level.playSound(null, entity.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            if (entity instanceof Player p) {
                p.displayClientMessage(Component.translatable("orbitalstrike.msg.ready").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
        if (used % 2 == 0) {
            HitResult hit = entity.pick(Config.RANGE.get(), 0.0F, false);
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 from = muzzle(entity);
                line(server, LASER_SIGHT, from, hit.getLocation(), 1.5);
                server.sendParticles(LASER_SIGHT, hit.getLocation().x, hit.getLocation().y + 0.1, hit.getLocation().z, 6, 0.2, 0.05, 0.2, 0);
            }
        }
        // चार्ज होते समय गन के आसपास ऊर्जा
        Vec3 m = muzzle(entity);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 2, 0.1, 0.1, 0.1, 0.05);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack) - timeLeft;
        if (!(entity instanceof Player player)) return;

        if (used < FULL_CHARGE) {
            if (!level.isClientSide) {
                level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.5F);
            }
            return;
        }

        // रिकॉइल (क्लाइंट पर खिलाड़ी की हरकत चलती है, इसलिए दोनों तरफ़)
        Vec3 look = player.getLookAngle();
        player.push(-look.x * 0.7, 0.15, -look.z * 0.7);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.orbitalstrike.client.ClientEvents.addShake(1.2F));
            return;
        }

        ServerLevel server = (ServerLevel) level;
        HitResult hit = player.pick(Config.RANGE.get(), 0.0F, false);
        if (hit.getType() == HitResult.Type.MISS) {
            player.displayClientMessage(Component.translatable("orbitalstrike.msg.no_target").withStyle(ChatFormatting.RED), true);
            return;
        }
        Vec3 target = hit.getLocation();
        Vec3 from = muzzle(player);

        // लेज़र शॉट
        line(server, BEAM_GLOW, from, target, 0.6);
        line(server, BEAM_CORE, from, target, 0.35);
        line(server, ParticleTypes.END_ROD, from, target, 1.2);
        server.sendParticles(ParticleTypes.FLASH, target.x, target.y + 0.5, target.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.SONIC_BOOM, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.8F);

        StrikeManager.schedule(server, target, player.getUUID());
        player.getCooldowns().addCooldown(this, Config.COOLDOWN_SECONDS.get() * 20);
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("orbitalstrike.msg.locked", (int) target.x, (int) target.y, (int) target.z)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    private static Vec3 muzzle(LivingEntity e) {
        Vec3 look = e.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return e.getEyePosition().add(look.scale(1.2)).add(right.scale(0.35)).add(0, -0.25, 0);
    }

    private static void line(ServerLevel level, ParticleOptions p, Vec3 from, Vec3 to, double step) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 dir = d.normalize();
        for (double s = 0; s < len; s += step) {
            Vec3 pos = from.add(dir.scale(s));
            for (ServerPlayer pl : level.players()) {
                level.sendParticles(pl, p, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("orbitalstrike.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("orbitalstrike.tooltip.2").withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
