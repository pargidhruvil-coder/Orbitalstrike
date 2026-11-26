package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalstrike.RocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** स्पेस शटल: सफ़ेद ऑर्बिटर + नारंगी फ़्यूल टैंक + दो सफ़ेद बूस्टर (उड़ान में अलग होकर गिरते हैं) */
public class RocketRenderer extends EntityRenderer<RocketEntity> {
    private final BlockRenderDispatcher blocks;

    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.9F;
    }

    @Override
    public void render(RocketEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        int t = e.getLaunchTicks();
        if (t > RocketEntity.COUNTDOWN - 30) {
            float s = t < RocketEntity.COUNTDOWN ? 0.02F : 0.04F;
            ps.translate((e.level().random.nextFloat() - 0.5F) * s, 0, (e.level().random.nextFloat() - 0.5F) * s);
        }
        // नारंगी बाहरी फ़्यूल टैंक (बीच में)
        box(ps, buf, light, Blocks.ORANGE_CONCRETE, -0.45F, 0.3F, -0.45F, 0.9F, 3.9F, 0.9F);
        box(ps, buf, light, Blocks.ORANGE_TERRACOTTA, -0.4F, 0.45F, -0.47F, 0.8F, 0.08F, 0.94F);
        box(ps, buf, light, Blocks.ORANGE_CONCRETE, -0.35F, 4.2F, -0.35F, 0.7F, 0.45F, 0.7F);
        box(ps, buf, light, Blocks.ORANGE_TERRACOTTA, -0.2F, 4.65F, -0.2F, 0.4F, 0.35F, 0.4F);
        // ऑर्बिटर (आगे, +Z)
        box(ps, buf, light, Blocks.WHITE_CONCRETE, -0.32F, 0.5F, 0.45F, 0.64F, 2.9F, 0.62F);
        box(ps, buf, light, Blocks.BLACK_CONCRETE, -0.33F, 0.5F, 0.44F, 0.66F, 2.9F, 0.06F);
        box(ps, buf, light, Blocks.BLACK_CONCRETE, -0.26F, 3.4F, 0.5F, 0.52F, 0.35F, 0.52F);
        box(ps, buf, light, Blocks.BLACK_CONCRETE, -0.16F, 3.75F, 0.58F, 0.32F, 0.2F, 0.34F);
        box(ps, buf, light, Blocks.GRAY_STAINED_GLASS, -0.22F, 3.15F, 1.05F, 0.44F, 0.22F, 0.04F);
        box(ps, buf, light, Blocks.WHITE_CONCRETE, -1.1F, 0.55F, 0.55F, 2.2F, 0.08F, 0.45F);
        box(ps, buf, light, Blocks.WHITE_CONCRETE, -0.8F, 0.55F, 0.55F, 1.6F, 0.08F, 0.8F);
        box(ps, buf, light, Blocks.BLACK_CONCRETE, -1.12F, 0.5F, 0.55F, 2.24F, 0.05F, 0.45F);
        box(ps, buf, light, Blocks.WHITE_CONCRETE, -0.04F, 0.5F, 1.05F, 0.08F, 0.9F, 0.5F);
        for (int i = -1; i <= 1; i++) {
            box(ps, buf, light, Blocks.POLISHED_BLACKSTONE, i * 0.2F - 0.08F, 0.28F, 0.7F, 0.16F, 0.22F, 0.16F);
        }
        // बूस्टर
        int flightT = t - RocketEntity.COUNTDOWN;
        if (flightT < 120) {
            float sep = flightT > 60 ? (flightT - 60 + partial) : 0;
            for (int s = -1; s <= 1; s += 2) {
                ps.pushPose();
                ps.translate(s * (0.78F + sep * 0.05F), -sep * sep * 0.03F, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(s * Math.min(sep * 1.5F, 40)));
                box(ps, buf, light, Blocks.WHITE_CONCRETE, -0.22F, 0.15F, -0.22F, 0.44F, 3.7F, 0.44F);
                box(ps, buf, light, Blocks.LIGHT_GRAY_CONCRETE, -0.23F, 1.2F, -0.23F, 0.46F, 0.06F, 0.46F);
                box(ps, buf, light, Blocks.LIGHT_GRAY_CONCRETE, -0.23F, 2.6F, -0.23F, 0.46F, 0.06F, 0.46F);
                box(ps, buf, light, Blocks.WHITE_CONCRETE, -0.15F, 3.85F, -0.15F, 0.3F, 0.3F, 0.3F);
                box(ps, buf, light, Blocks.LIGHT_GRAY_CONCRETE, -0.07F, 4.15F, -0.07F, 0.14F, 0.18F, 0.14F);
                box(ps, buf, light, Blocks.POLISHED_BLACKSTONE, -0.18F, -0.05F, -0.18F, 0.36F, 0.22F, 0.36F);
                ps.popPose();
            }
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    private void box(PoseStack ps, MultiBufferSource buf, int light, Block b, float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(sx, sy, sz);
        blocks.renderSingleBlock(b.defaultBlockState(), ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
