package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class OrbitalCannonItem extends Item {

    public OrbitalCannonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            HitResult hit = player.pick(Config.RANGE.get(), 0.0F, false);
            if (hit.getType() == HitResult.Type.MISS) {
                player.displayClientMessage(Component.translatable("orbitalstrike.msg.no_target")
                        .withStyle(ChatFormatting.RED), true);
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                        SoundSource.PLAYERS, 1.0F, 0.5F);
                return InteractionResultHolder.fail(stack);
            }

            Vec3 target = hit.getLocation();
            StrikeManager.schedule((ServerLevel) level, target, player.getUUID());

            player.getCooldowns().addCooldown(this, Config.COOLDOWN_SECONDS.get() * 20);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.0F, 1.6F);
            player.sendSystemMessage(Component.translatable("orbitalstrike.msg.locked",
                            (int) target.x, (int) target.y, (int) target.z)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("orbitalstrike.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("orbitalstrike.tooltip.2").withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // चमकदार इफ़ेक्ट
    }
}
