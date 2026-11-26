package com.orbitalstrike.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.orbitalstrike.OrbitalStrikeMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** छोटे-छोटे ड्रॉइंग हेल्पर: बिलबोर्ड, समतल डिस्क, बीम सिलेंडर, ब्लॉक से बनी चीज़ें */
public final class FxDraw {
    private FxDraw() {}

    public static ResourceLocation fx(String n) {
        return new ResourceLocation(OrbitalStrikeMod.MODID, "textures/fx/" + n + ".png");
    }

    public static ResourceLocation env(String n) {
        return new ResourceLocation(OrbitalStrikeMod.MODID, "textures/environment/" + n + ".png");
    }

    public static void begin(boolean additive) {
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public static void end() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** ठोस (अपारदर्शी) चीज़ों के लिए: depth लिखो */
    public static void beginOpaque() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /**
     * असली 3D गोला (equirectangular नक्शे के साथ), रोशनी और छाया।
     * lightDir = दुनिया में रोशनी की दिशा। emissive = खुद चमकता (सूरज)।
     */
    public static void sphere(PoseStack ps, Camera cam, Vec3 pos, float r, ResourceLocation tex, float spinDeg, float tiltDeg,
                              Vec3 lightDir, boolean emissive, float alpha) {
        if (alpha <= 0.003F || r <= 0.01F) return;
        ps.pushPose();
        Vec3 c = cam.getPosition();
        ps.translate(pos.x - c.x, pos.y - c.y, pos.z - c.z);
        ps.mulPose(Axis.ZP.rotationDegrees(tiltDeg));
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        Vec3 L = lightDir.normalize().zRot((float) Math.toRadians(tiltDeg)).yRot((float) -Math.toRadians(spinDeg));
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        final int LAT = 18, LON = 32;
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int[][] corners = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] k : corners) {
                    double th = Math.PI * k[0] / LAT, ph = 2 * Math.PI * k[1] / LON;
                    float nx = (float) (Math.sin(th) * Math.cos(ph)), ny = (float) Math.cos(th), nz = (float) (Math.sin(th) * Math.sin(ph));
                    float sh = emissive ? 1F : 0.42F + 0.7F * (float) Math.max(0, nx * L.x + ny * L.y + nz * L.z);
                    sh = Math.min(1F, sh);
                    bb.vertex(m, nx * r, ny * r, nz * r).uv(k[1] / (float) LON, k[0] / (float) LAT).color(sh, sh, sh, alpha).endVertex();
                }
            }
        }
        Tesselator.getInstance().end();
        ps.popPose();
    }

    /** झुकी हुई समतल डिस्क (शनि की रिंग) */
    public static void tiltedDisc(PoseStack ps, Camera cam, Vec3 pos, float size, ResourceLocation tex, float tiltDeg, float spinDeg, float shade) {
        ps.pushPose();
        Vec3 c = cam.getPosition();
        ps.translate(pos.x - c.x, pos.y - c.y, pos.z - c.z);
        ps.mulPose(Axis.ZP.rotationDegrees(tiltDeg));
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.vertex(m, -size, 0, -size).uv(0, 0).color(shade, shade, shade, 1F).endVertex();
        bb.vertex(m, size, 0, -size).uv(1, 0).color(shade, shade, shade, 1F).endVertex();
        bb.vertex(m, size, 0, size).uv(1, 1).color(shade, shade, shade, 1F).endVertex();
        bb.vertex(m, -size, 0, size).uv(0, 1).color(shade, shade, shade, 1F).endVertex();
        Tesselator.getInstance().end();
        ps.popPose();
    }

    /** कैमरा-सापेक्ष निर्देशांकों में टाइल होने वाला पैनल: o से a और b दिशा (b = ऊपर) */
    public static void panel(PoseStack ps, ResourceLocation tex, double ox, double oy, double oz, double ax, double ay, double az,
                             double bx, double by, double bz, float us, float vs, float r, float g, float bl) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.vertex(m, (float) ox, (float) oy, (float) oz).uv(0, vs).color(r, g, bl, 1F).endVertex();
        bb.vertex(m, (float) (ox + ax), (float) (oy + ay), (float) (oz + az)).uv(us, vs).color(r, g, bl, 1F).endVertex();
        bb.vertex(m, (float) (ox + ax + bx), (float) (oy + ay + by), (float) (oz + az + bz)).uv(us, 0).color(r, g, bl, 1F).endVertex();
        bb.vertex(m, (float) (ox + bx), (float) (oy + by), (float) (oz + bz)).uv(0, 0).color(r, g, bl, 1F).endVertex();
        Tesselator.getInstance().end();
    }

    /** दीवार पर लगी (स्थिर दिशा वाली) चौकोर स्क्रीन, कैमरा-सापेक्ष निर्देशांक */
    public static void wallQuad(PoseStack ps, ResourceLocation tex, double cx, double cy, double cz, float yawDeg, float half,
                                float rollDeg, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(Axis.YP.rotationDegrees(yawDeg));
        if (rollDeg != 0) ps.mulPose(Axis.ZP.rotationDegrees(rollDeg));
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.vertex(m, -half, -half, 0).uv(0, 1).color(r, g, b, a).endVertex();
        bb.vertex(m, half, -half, 0).uv(1, 1).color(r, g, b, a).endVertex();
        bb.vertex(m, half, half, 0).uv(1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, -half, half, 0).uv(0, 0).color(r, g, b, a).endVertex();
        Tesselator.getInstance().end();
        ps.popPose();
    }

    /** कैमरे की तरफ़ मुँह वाला चौकोर */
    public static void billboard(PoseStack ps, Camera cam, Vec3 pos, float size, ResourceLocation tex,
                                 float r, float g, float b, float a, float roll) {
        if (a <= 0.003F) return;
        ps.pushPose();
        Vec3 c = cam.getPosition();
        ps.translate(pos.x - c.x, pos.y - c.y, pos.z - c.z);
        ps.mulPose(cam.rotation());
        if (roll != 0) ps.mulPose(Axis.ZP.rotationDegrees(roll));
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.vertex(m, -size, -size, 0).uv(1, 1).color(r, g, b, a).endVertex();
        bb.vertex(m, -size, size, 0).uv(1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, size, size, 0).uv(0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, size, -size, 0).uv(0, 1).color(r, g, b, a).endVertex();
        Tesselator.getInstance().end();
        ps.popPose();
    }

    /** ज़मीन के समानांतर (क्षैतिज) चौकोर, जैसे जादुई घेरा या शॉकवेव */
    public static void flat(PoseStack ps, Camera cam, Vec3 pos, float size, ResourceLocation tex,
                            float r, float g, float b, float a, float rotDeg) {
        if (a <= 0.003F) return;
        ps.pushPose();
        Vec3 c = cam.getPosition();
        ps.translate(pos.x - c.x, pos.y - c.y, pos.z - c.z);
        ps.mulPose(Axis.YP.rotationDegrees(rotDeg));
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.vertex(m, -size, 0, -size).uv(0, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, size, 0, -size).uv(1, 0).color(r, g, b, a).endVertex();
        bb.vertex(m, size, 0, size).uv(1, 1).color(r, g, b, a).endVertex();
        bb.vertex(m, -size, 0, size).uv(0, 1).color(r, g, b, a).endVertex();
        Tesselator.getInstance().end();
        ps.popPose();
    }

    /** ऊपर से नीचे आती रोशनी की बीम */
    public static void cylinder(PoseStack ps, Camera cam, Vec3 bottom, float height, float radius, ResourceLocation tex,
                                float r, float g, float b, float a, float scroll) {
        if (a <= 0.003F || radius <= 0.001F) return;
        ps.pushPose();
        Vec3 c = cam.getPosition();
        ps.translate(bottom.x - c.x, bottom.y - c.y, bottom.z - c.z);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int sides = 16;
        float v0 = scroll, v1 = scroll + height / 24F;
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2 / sides, a1 = (i + 1) * Math.PI * 2 / sides;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            bb.vertex(m, x0, 0, z0).uv(0, v1).color(r, g, b, a).endVertex();
            bb.vertex(m, x1, 0, z1).uv(1, v1).color(r, g, b, a).endVertex();
            bb.vertex(m, x1, height, z1).uv(1, v0).color(r, g, b, a).endVertex();
            bb.vertex(m, x0, height, z0).uv(0, v0).color(r, g, b, a).endVertex();
        }
        Tesselator.getInstance().end();
        ps.popPose();
    }

    // ------------------------------------------------------------------ ब्लॉकों से बनी चीज़ें
    public static void box(PoseStack ps, MultiBufferSource buf, Block block, float x, float y, float z, float sx, float sy, float sz) {
        BlockRenderDispatcher br = Minecraft.getInstance().getBlockRenderer();
        ps.pushPose();
        ps.translate(x, y, z);
        ps.scale(sx, sy, sz);
        br.renderSingleBlock(block.defaultBlockState(), ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static void centered(PoseStack ps, MultiBufferSource buf, Block b, float y, float w, float h) {
        box(ps, buf, b, -w / 2, y, -w / 2, w, h, w);
    }

    /** सुनहरा वज्र (दोनों सिरों पर तीन-तीन नोक) */
    public static void vajra(PoseStack ps, MultiBufferSource buf, float scale, float spinDeg) {
        ps.pushPose();
        ps.scale(scale, scale, scale);
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        // बीच की पकड़
        centered(ps, buf, Blocks.GOLD_BLOCK, -0.6F, 0.45F, 1.2F);
        centered(ps, buf, Blocks.DIAMOND_BLOCK, -0.15F, 0.6F, 0.3F);
        for (int end = 0; end < 2; end++) {
            ps.pushPose();
            if (end == 1) ps.mulPose(Axis.XP.rotationDegrees(180));
            centered(ps, buf, Blocks.GOLD_BLOCK, 0.6F, 0.8F, 0.35F);           // कमल जैसा आधार
            centered(ps, buf, Blocks.RAW_GOLD_BLOCK, 0.95F, 0.6F, 0.3F);
            centered(ps, buf, Blocks.GOLD_BLOCK, 1.25F, 0.28F, 1.9F);          // बीच की नोक
            centered(ps, buf, Blocks.SHROOMLIGHT, 3.15F, 0.2F, 0.35F);
            for (int k = 0; k < 4; k++) {                                         // चार घुमावदार नोकें
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(k * 90));
                ps.translate(0, 1.0F, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(-28));
                box(ps, buf, Blocks.GOLD_BLOCK, 0.1F, 0, -0.1F, 0.2F, 1.5F, 0.2F);
                ps.translate(0.2F, 1.5F, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(45));
                box(ps, buf, Blocks.GOLD_BLOCK, -0.05F, 0, -0.1F, 0.2F, 0.9F, 0.2F);
                box(ps, buf, Blocks.SHROOMLIGHT, -0.05F, 0.85F, -0.1F, 0.2F, 0.2F, 0.2F);
                ps.popPose();
            }
            ps.popPose();
        }
        ps.popPose();
    }

    /** अंतरिक्ष वाले सीन का उपग्रह "गरुड़-7" */
    public static void satellite(PoseStack ps, MultiBufferSource buf, float blink) {
        box(ps, buf, Blocks.IRON_BLOCK, -1.0F, -0.6F, -0.6F, 2.0F, 1.2F, 1.2F);
        box(ps, buf, Blocks.GOLD_BLOCK, -1.05F, -0.62F, -0.62F, 0.1F, 1.24F, 1.24F);
        box(ps, buf, Blocks.WHITE_CONCRETE, 0.95F, -0.5F, -0.5F, 0.6F, 1.0F, 1.0F);
        for (int s = -1; s <= 1; s += 2) {
            box(ps, buf, Blocks.IRON_BARS, -0.05F, -0.05F, s > 0 ? 0.6F : -1.2F, 0.1F, 0.1F, 0.6F);
            box(ps, buf, Blocks.BLUE_CONCRETE, -1.2F, -0.04F, s > 0 ? 1.2F : -5.2F, 2.4F, 0.08F, 4.0F);
            box(ps, buf, Blocks.LIGHT_BLUE_STAINED_GLASS, -1.25F, 0.05F, s > 0 ? 1.15F : -5.25F, 2.5F, 0.04F, 4.1F);
        }
        box(ps, buf, Blocks.WHITE_CONCRETE, 1.5F, -0.9F, -0.9F, 0.15F, 1.8F, 1.8F);   // डिश
        box(ps, buf, Blocks.END_ROD, 1.65F, -0.1F, -0.1F, 0.8F, 0.2F, 0.2F);
        if (blink > 0.5F) box(ps, buf, Blocks.REDSTONE_BLOCK, -0.3F, 0.6F, -0.15F, 0.3F, 0.3F, 0.3F);
    }
}
