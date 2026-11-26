package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalstrike.OrbitalCannonItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** गन को दोनों हाथों से राइफ़ल की तरह पकड़ना, चार्ज पर काँपना, फ़ायर पर रिकॉइल */
public class CannonClientExtensions implements IClientItemExtensions {
    public static final CannonClientExtensions INSTANCE = new CannonClientExtensions();

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (entity.isUsingItem() && entity.getUseItem() == stack) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        float charge = player.isUsingItem() && player.getUseItem() == stack ? OrbitalCannonItem.chargeProgress(player) : 0;
        if (charge > 0) {
            float j = charge * 0.012F;
            ps.translate((player.getRandom().nextFloat() - 0.5F) * j, (player.getRandom().nextFloat() - 0.5F) * j, charge * 0.06F);
        }
        float recoil = ClientEvents.recoil(partialTick);
        if (recoil > 0) {
            ps.translate(0, recoil * 0.05F, recoil * 0.28F);
            ps.mulPose(Axis.XP.rotationDegrees(recoil * 22));
        }
        return false;
    }
}
