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

/** रॉकेट को Minecraft ब्लॉकों से बनाकर दिखाता है (कोई अलग मॉडल फ़ाइल नहीं चाहिए) */
public class RocketRenderer extends EntityRenderer<RocketEntity> {
    private final BlockRenderDispatcher blocks;

    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.8F;
    }

    @Override
    public void render(RocketEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        int t = e.getLaunchTicks();
        if (t > RocketEntity.COUNTDOWN - 30) {   // लॉन्च से ठीक पहले और उड़ान में हल्का कंपन
            float s = t < RocketEntity.COUNTDOWN ? 0.02F : 0.04F;
            ps.translate((e.getRandom().nextFloat() - 0.5F) * s, 0, (e.getRandom().nextFloat() - 0.5F) * s);
        }
        // इंजन
        block(ps, buf, light, Blocks.POLISHED_BLACKSTONE, 0, 0, 0.9F, 0.6F);
        block(ps, buf, light, Blocks.IRON_BLOCK, 0.6F, 0, 1.0F, 0.6F);
        // बॉडी
        block(ps, buf, light, Blocks.WHITE_CONCRETE, 1.2F, 0, 1.0F, 1.0F);
        block(ps, buf, light, Blocks.LIGHT_BLUE_CONCRETE, 2.2F, 0, 1.0F, 0.6F);   // खिड़की की पट्टी
        block(ps, buf, light, Blocks.WHITE_CONCRETE, 2.8F, 0, 1.0F, 1.0F);
        block(ps, buf, light, Blocks.WHITE_CONCRETE, 3.8F, 0, 0.9F, 0.5F);
        // नोक
        block(ps, buf, light, Blocks.RED_CONCRETE, 4.3F, 0, 0.75F, 0.45F);
        block(ps, buf, light, Blocks.RED_CONCRETE, 4.75F, 0, 0.5F, 0.4F);
        block(ps, buf, light, Blocks.RED_CONCRETE, 5.15F, 0, 0.25F, 0.35F);
        // फिन
        for (int i = 0; i < 4; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(i * 90 + 45));
            ps.translate(0, 0, 0.65F);
            box(ps, buf, light, Blocks.RED_CONCRETE, -0.08F, 0.0F, -0.2F, 0.16F, 1.4F, 0.5F);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    private void block(PoseStack ps, MultiBufferSource buf, int light, Block b, float y, float yOff, float width, float height) {
        box(ps, buf, light, b, -width / 2, y + yOff, -width / 2, width, height, width);
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
