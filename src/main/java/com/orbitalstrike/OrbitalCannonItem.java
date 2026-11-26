package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * नवग्रह कैनन:
 * राइट-क्लिक दबाकर रखो -> चार्ज (स्कोप + ज़ूम), पूरा चार्ज होने पर छोड़ो -> नवग्रह संरेखण + वज्र प्रहार।
 * Shift + राइट-क्लिक -> सिनेमैटिक मोड मेनू।
 */
public class OrbitalCannonItem extends Item {
    private static final DustParticleOptions LASER_SIGHT = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.15F), 0.7F);
    private static final DustParticleOptions BEAM_CORE = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.4F), 1.6F);
    private static final DustParticleOptions BEAM_GLOW = new DustParticleOptions(new Vector3f(0.7F, 0.3F, 1.0F), 2.2F);

    public OrbitalCannonItem(Properties properties) {
        super(properties);
    }

    public static int fullCharge() {
        return Config.CHARGE_SECONDS.get() * 20;
    }

    public static int getMode(ItemStack s) {
        return s.hasTag() ? Math.max(0, Math.min(2, s.getTag().getInt("Mode"))) : Timeline.LONG;
    }

    public static void setMode(ItemStack s, int mode) {
        s.getOrCreateTag().putInt("Mode", Math.max(0, Math.min(2, mode)));
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
        return Math.min(1.0F, entity.getTicksUsingItem() / (float) fullCharge());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.orbitalstrike.client.CannonModeScreen.open(getMode(stack)));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (StrikeManager.hasActiveStrike(player.getUUID())) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), ModSounds.CHARGE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        if (level.isClientSide || !(level instanceof ServerLevel server)) return;

        if (used == fullCharge()) {
            level.playSound(null, entity.blockPosition(), ModSounds.READY.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (entity instanceof Player p) {
                p.displayClientMessage(Component.translatable("orbitalstrike.msg.ready").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
        if (used % 2 == 0) {
            HitResult hit = entity.pick(Config.RANGE.get(), 0.0F, false);
            if (hit.getType() != HitResult.Type.MISS) {
                line(server, LASER_SIGHT, muzzle(entity), hit.getLocation(), 1.5);
                server.sendParticles(LASER_SIGHT, hit.getLocation().x, hit.getLocation().y + 0.1, hit.getLocation().z, 6, 0.2, 0.05, 0.2, 0);
            }
        }
        Vec3 m = muzzle(entity);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 1 + used / 10, 0.1, 0.1, 0.1, 0.05);
        if (used > fullCharge() / 2) {
            server.sendParticles(ParticleTypes.END_ROD, m.x, m.y, m.z, 1, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack) - timeLeft;
        if (!(entity instanceof Player player)) return;

        if (used < fullCharge()) {
            if (!level.isClientSide) {
                level.playSound(null, player.blockPosition(), ModSounds.FIZZLE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                player.displayClientMessage(Component.translatable("orbitalstrike.msg.weak").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        Vec3 look = player.getLookAngle();
        player.push(-look.x * 0.6, 0.12, -look.z * 0.6);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.orbitalstrike.client.ClientEvents.kick());
            return;
        }

        ServerLevel server = (ServerLevel) level;
        HitResult hit = player.pick(Config.RANGE.get(), 0.0F, false);
        if (hit.getType() == HitResult.Type.MISS) {
            player.displayClientMessage(Component.translatable("orbitalstrike.msg.no_target").withStyle(ChatFormatting.RED), true);
            level.playSound(null, player.blockPosition(), ModSounds.FIZZLE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            return;
        }
        Vec3 target = hit.getLocation();
        Vec3 from = muzzle(player);

        line(server, BEAM_GLOW, from, target, 0.6);
        line(server, BEAM_CORE, from, target, 0.35);
        line(server, ParticleTypes.END_ROD, from, target, 1.2);
        server.sendParticles(ParticleTypes.FLASH, target.x, target.y + 0.5, target.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), ModSounds.FIRE.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
        level.playSound(null, target.x, target.y, target.z, ModSounds.LOCK.get(), SoundSource.PLAYERS, 2.0F, 1.0F);

        player.getCooldowns().addCooldown(this, Config.COOLDOWN_SECONDS.get() * 20);
        if (player instanceof ServerPlayer sp) {
            StrikeManager.fire(sp, target, getMode(stack));
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
        tooltip.add(Component.translatable("orbitalstrike.tooltip.2").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("orbitalstrike.tooltip.mode_" + getMode(stack)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("orbitalstrike.tooltip.menu").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.orbitalstrike.client.CannonClientExtensions.INSTANCE);
    }
}
