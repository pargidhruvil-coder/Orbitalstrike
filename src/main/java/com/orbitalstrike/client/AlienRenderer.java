package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalstrike.OrbitalStrikeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** ज़ोरो और ज़ारा: Minecraft कैरेक्टर जैसे 3D एलियन, हाथ-सिर के एनिमेशन के साथ */
public final class AlienRenderer {
    private AlienRenderer() {}

    public static final ResourceLocation ZORO = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/entity/alien_zoro.png");
    public static final ResourceLocation ZARA = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/entity/alien_zara.png");
    /** VIP Dhruvil की आँखें (सिर्फ़ आँख वाले पिक्सेल) — Enderman जैसी चमक के लिए */
    public static final ResourceLocation ZARA_EYES = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/entity/alien_zara_eyes.png");
    /** Herobrine: गेम की अपनी Steve स्किन + सफ़ेद चमकती आँखें (कोई स्किन फ़ाइल कॉपी नहीं) */
    public static final ResourceLocation HEROBRINE = new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");
    public static final ResourceLocation HEROBRINE_EYES = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/entity/herobrine_eyes.png");
    /** शिप के अंदर वाले दोनों एलियन: ज़ोरो (हरी आँखें) और ज़मज़म (नारंगी आँखें) */
    public static final ResourceLocation ZAMZAM = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/entity/alien_zamzam.png");
    private static final ResourceLocation ZORO_EYES = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/entity/alien_zoro_eyes.png");
    private static final ResourceLocation ZAMZAM_EYES = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/entity/alien_zamzam_eyes.png");

    private static ResourceLocation eyesFor(ResourceLocation tex) {
        if (tex.equals(ZARA)) return ZARA_EYES;
        if (tex.equals(HEROBRINE)) return HEROBRINE_EYES;
        if (tex.equals(ZORO)) return ZORO_EYES;
        if (tex.equals(ZAMZAM)) return ZAMZAM_EYES;
        return null;
    }

    private static PlayerModel<LivingEntity> model;

    private static PlayerModel<LivingEntity> model() {
        if (model == null) {
            model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        return model;
    }

    /** एनिमेशन की स्थिति */
    public static class Pose {
        public float headYaw, headPitch;          // डिग्री
        public float rightArm, leftArm;           // आगे-पीछे (डिग्री, ऋणात्मक = ऊपर)
        public float rightArmOut, leftArmOut;     // बाहर की ओर (डिग्री)
        public float bob;                         // ऊपर-नीचे
        public float rightLeg, leftLeg;           // दौड़ते पैर (डिग्री)
        public float lean;                        // आगे झुकना (डिग्री)
    }

    public static void render(PoseStack ps, MultiBufferSource buf, ResourceLocation tex, double x, double y, double z,
                              float bodyYaw, Pose p) {
        render(ps, buf, tex, x, y, z, bodyYaw, p, 1.05F, 1F);
    }

    public static void render(PoseStack ps, MultiBufferSource buf, ResourceLocation tex, double x, double y, double z,
                              float bodyYaw, Pose p, float scale, float alpha) {
        PlayerModel<LivingEntity> m = model();
        m.young = false;
        m.crouching = false;
        m.riding = false;
        m.head.xRot = p.headPitch * Mth.DEG_TO_RAD;
        m.head.yRot = p.headYaw * Mth.DEG_TO_RAD;
        m.head.zRot = 0;
        m.body.xRot = 0; m.body.yRot = 0; m.body.zRot = 0;
        m.rightArm.xRot = p.rightArm * Mth.DEG_TO_RAD; m.rightArm.yRot = 0; m.rightArm.zRot = p.rightArmOut * Mth.DEG_TO_RAD;
        m.leftArm.xRot = p.leftArm * Mth.DEG_TO_RAD; m.leftArm.yRot = 0; m.leftArm.zRot = -p.leftArmOut * Mth.DEG_TO_RAD;
        m.rightLeg.xRot = p.rightLeg * Mth.DEG_TO_RAD; m.rightLeg.yRot = 0; m.rightLeg.zRot = 0;
        m.leftLeg.xRot = p.leftLeg * Mth.DEG_TO_RAD; m.leftLeg.yRot = 0; m.leftLeg.zRot = 0;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);

        ps.pushPose();
        ps.translate(x, y + p.bob * scale, z);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        if (p.lean != 0) ps.mulPose(Axis.XP.rotationDegrees(-p.lean));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(scale, scale, scale);
        ps.translate(0.0F, -1.501F, 0.0F);
        RenderType rt = alpha < 0.99F ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex);
        m.renderToBuffer(ps, buf.getBuffer(rt), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
        ResourceLocation eyes = eyesFor(tex);
        if (eyes != null && alpha > 0.5F) {
            m.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(eyes)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
        ps.popPose();
    }
}
