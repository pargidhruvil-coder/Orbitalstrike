package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class RocketItem extends Item {
    public RocketItem(Properties properties) {
        super(properties);
    }

    public static Destination getDestination(ItemStack stack) {
        return stack.hasTag() ? Destination.byIndex(stack.getTag().getInt("Destination")) : Destination.SPACE;
    }

    public static void setDestination(ItemStack stack, Destination d) {
        stack.getOrCreateTag().putInt("Destination", d.ordinal());
    }

    public static ItemStack create(Destination d) {
        ItemStack s = new ItemStack(ModItems.ROCKET.get());
        setDestination(s, d);
        return s;
    }

    private static void cycle(Player player, ItemStack stack) {
        Destination next = getDestination(stack).next();
        setDestination(stack, next);
        player.displayClientMessage(Component.translatable("orbitalstrike.msg.dest", next.displayName()), true);
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.4F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) cycle(player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player != null && player.isSecondaryUseActive()) {
            if (!level.isClientSide) cycle(player, stack);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
            RocketEntity rocket = new RocketEntity(ModEntities.ROCKET.get(), level);
            rocket.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player != null ? player.getYRot() : 0, 0);
            rocket.setDestination(getDestination(stack));
            level.addFreshEntity(rocket);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.8F);
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("orbitalstrike.msg.dest", getDestination(stack).displayName()));
        tooltip.add(Component.translatable("orbitalstrike.rocket.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("orbitalstrike.rocket.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("orbitalstrike.rocket.tip3").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
