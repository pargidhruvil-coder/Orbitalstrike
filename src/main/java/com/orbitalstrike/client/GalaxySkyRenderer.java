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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * गैलेक्सी आसमान: 6-फ़ेस स्काईबॉक्स (तारे, मिल्की-वे, नेबुला) + ग्रह, स्पाइरल गैलेक्सी, ब्लैक होल, तारों का गुच्छा।
 */
public class GalaxySkyRenderer {
    public enum Kind { SPACE, MOON, MARS }

    private static final ResourceLocation[] FACES = new ResourceLocation[6];
    static {
        for (int i = 0; i < 6; i++) FACES[i] = tex("galaxy_" + i);
    }
    private static final ResourceLocation SUN = tex("sun");
    private static final ResourceLocation EARTH = tex("earth");
    private static final ResourceLocation MOON = tex("moon");
    private static final ResourceLocation MARS = tex("mars");
    private static final ResourceLocation SPIRAL = tex("spiral_galaxy");
    private static final ResourceLocation BH_DISK = tex("black_hole_disk");
    private static final ResourceLocation BH_CORE = tex("black_hole_core");
    private static final ResourceLocation CLUSTER = tex("cluster");

    private static ResourceLocation tex(String n) {
        return new ResourceLocation(OrbitalStrikeMod.MODID, "textures/environment/" + n + ".png");
    }

    public static void render(Kind kind, ClientLevel level, float partial, PoseStack ps) {
        float time = (level.getGameTime() % 2400000L) + partial;

        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 0.004F));   // पूरा आसमान बहुत धीरे घूमता है
        renderSkybox(ps, 1.0F, false);

        switch (kind) {
            case SPACE -> {
                body(ps, SUN, 30, 55, 0, 0, 16, true, 1F);
                body(ps, SPIRAL, 145, 50, time * 0.01F, 62, 48, true, 1F);
                body(ps, CLUSTER, 315, 35, 0, 0, 13, true, 1F);
                blackHole(ps, 250, 68, time, 34);
                body(ps, MARS, 80, 78, 0, 0, 6, false, 1F);
                body(ps, MOON, 205, 98, 0, 0, 13, false, 1F);
                body(ps, EARTH, 0, 128, time * 0.02F, 0, 72, false, 1F);
            }
            case MOON -> {
                body(ps, SUN, 300, 50, 0, 0, 12, true, 1F);
                body(ps, SPIRAL, 120, 40, time * 0.01F, 60, 30, true, 0.9F);
                body(ps, CLUSTER, 200, 25, 0, 0, 9, true, 1F);
                blackHole(ps, 40, 72, time, 18);
                body(ps, MARS, 250, 70, 0, 0, 3.5F, false, 1F);
                body(ps, EARTH, 60, 40, time * 0.02F, 0, 22, false, 1F);
            }
            case MARS -> {
                body(ps, SUN, 330, 55, 0, 0, 9, true, 1F);
                body(ps, SPIRAL, 160, 35, time * 0.01F, 55, 26, true, 0.9F);
                blackHole(ps, 60, 50, time, 30);
                body(ps, CLUSTER, 260, 30, 0, 0, 10, true, 1F);
                body(ps, MOON, 100, 30, 0, 0, 4, false, 1F);   // फ़ोबोस
                body(ps, MOON, 220, 60, 0, 0, 2.5F, false, 1F); // डीमोस
                body(ps, EARTH, 20, 70, 0, 0, 2.2F, false, 1F);
            }
        }
        ps.popPose();

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    /** स्काईबॉक्स: vanilla renderEndSky जैसा, 6 अलग टेक्सचर के साथ */
    public static void renderSkybox(PoseStack ps, float alpha, boolean additive) {
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        Tesselator t = Tesselator.getInstance();
        BufferBuilder b = t.getBuilder();
        for (int i = 0; i < 6; i++) {
            ps.pushPose();
            if (i == 1) ps.mulPose(Axis.XP.rotationDegrees(90));
            if (i == 2) ps.mulPose(Axis.XP.rotationDegrees(-90));
            if (i == 3) ps.mulPose(Axis.XP.rotationDegrees(180));
            if (i == 4) ps.mulPose(Axis.ZP.rotationDegrees(90));
            if (i == 5) ps.mulPose(Axis.ZP.rotationDegrees(-90));
            RenderSystem.setShaderTexture(0, FACES[i]);
            Matrix4f m = ps.last().pose();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            b.vertex(m, -100, -100, -100).uv(0, 0).endVertex();
            b.vertex(m, -100, -100, 100).uv(0, 1).endVertex();
            b.vertex(m, 100, -100, 100).uv(1, 1).endVertex();
            b.vertex(m, 100, -100, -100).uv(1, 0).endVertex();
            t.end();
            ps.popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * yaw/pitch = आसमान में दिशा (pitch 0 = सिर के ऊपर, 90 = क्षितिज, 180 = पैरों के नीचे)
     * spin = अपनी जगह घूमना, tilt = झुकाव (गैलेक्सी/डिस्क को तिरछा दिखाने के लिए)
     */
    private static void body(PoseStack ps, ResourceLocation tex, float yaw, float pitch, float spin, float tilt,
                             float size, boolean additive, float alpha) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0, 100, 0);
        if (tilt != 0) ps.mulPose(Axis.XP.rotationDegrees(tilt));
        if (spin != 0) ps.mulPose(Axis.YP.rotationDegrees(spin));
        quad(ps, tex, size, additive, alpha);
        ps.popPose();
    }

    private static void blackHole(PoseStack ps, float yaw, float pitch, float time, float size) {
        // घूमती हुई चमकीली डिस्क (तिरछी), फिर बीच में गहरा गोला
        body(ps, BH_DISK, yaw, pitch, time * 0.15F, 68, size, false, 1F);
        body(ps, BH_DISK, yaw, pitch, -time * 0.07F, 68, size * 0.8F, true, 0.5F);
        body(ps, BH_CORE, yaw, pitch, 0, 0, size * 0.26F, false, 1F);
    }

    private static void quad(PoseStack ps, ResourceLocation tex, float s, boolean additive, float alpha) {
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f m = ps.last().pose();
        Tesselator t = Tesselator.getInstance();
        BufferBuilder b = t.getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, -s, 0, -s).uv(0, 0).endVertex();
        b.vertex(m, s, 0, -s).uv(1, 0).endVertex();
        b.vertex(m, s, 0, s).uv(1, 1).endVertex();
        b.vertex(m, -s, 0, s).uv(0, 1).endVertex();
        t.end();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
