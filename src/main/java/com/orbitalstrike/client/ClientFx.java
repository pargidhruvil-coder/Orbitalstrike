package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalstrike.OrbitalStrikeMod;
import com.orbitalstrike.Timeline;
import com.orbitalstrike.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** दुनिया में दिखने वाले इफ़ेक्ट: नवग्रह, जादुई घेरे, वज्र, बीम, शॉकवेव (हर नज़दीकी खिलाड़ी को दिखते हैं) */
@Mod.EventBusSubscriber(modid = OrbitalStrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientFx {

    // चंद्र, मंगल, बुध, शुक्र, बृहस्पति, शनि, राहु, केतु, सूर्य (नीचे से ऊपर)
    public static final String[] PLANET_IDS = {"chandra", "mangal", "budh", "shukra", "guru", "shani", "rahu", "ketu", "surya"};
    public static final ResourceLocation[] PLANET_TEX = {
            FxDraw.env("moon"), FxDraw.env("mars"), FxDraw.fx("mercury"), FxDraw.fx("venus"), FxDraw.fx("jupiter"),
            FxDraw.fx("saturn"), FxDraw.fx("rahu"), FxDraw.fx("ketu"), FxDraw.env("sun")};
    public static final float[] PLANET_SIZE = {3.0F, 3.5F, 2.5F, 4.0F, 7.0F, 9.0F, 5.0F, 5.0F, 11.0F};
    public static final ChatFormatting[] PLANET_COLOR = {ChatFormatting.WHITE, ChatFormatting.RED, ChatFormatting.GREEN,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.BLUE, ChatFormatting.DARK_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.GOLD};
    private static final float[] PLANET_Y = new float[Timeline.PLANETS];
    static {
        float y = 38;
        for (int i = 0; i < Timeline.PLANETS; i++) {
            if (i > 0) y += PLANET_SIZE[i - 1] + PLANET_SIZE[i] + 3;
            PLANET_Y[i] = y;
        }
    }

    private static final ResourceLocation SIGIL = FxDraw.fx("sigil");
    private static final ResourceLocation RING = FxDraw.fx("ring");
    private static final ResourceLocation BEAM = FxDraw.fx("beam");
    private static final ResourceLocation GLOW = FxDraw.fx("glow");

    public static class StrikeFx {
        public final int id;
        public final Vec3 pos;
        public final Timeline tl;
        public int age;

        StrikeFx(int id, Vec3 pos, Timeline tl, int age) {
            this.id = id; this.pos = pos; this.tl = tl; this.age = age;
        }

        public float time(float partial) { return age + partial; }

        public Vec3 planetPos(int i, float partial) {
            float t = time(partial);
            float start = tl.planetsStart + i * tl.planetStep;
            float p = Mth.clamp((t - start) / tl.planetFly, 0, 1);
            float e = 1 - (1 - p) * (1 - p) * (1 - p);
            double ang = (id * 37 + i * 1.7) % (Math.PI * 2);
            Vec3 from = pos.add(Math.cos(ang) * 260, 140, Math.sin(ang) * 260);
            Vec3 to = pos.add(0, PLANET_Y[i], 0);
            Vec3 v = from.lerp(to, e);
            return v.add(0, Math.sin(p * Math.PI) * 40, 0);
        }

        public boolean planetVisible(int i, float partial) {
            return time(partial) >= tl.planetsStart + i * tl.planetStep;
        }

        public float topY() { return PLANET_Y[Timeline.PLANETS - 1]; }

        /** वज्र की ऊँचाई (टारगेट से) — NaN = नहीं दिख रहा */
        public float vajraHeight(float partial) {
            if (tl.vajraStart < 0) return Float.NaN;
            float t = time(partial);
            if (t < tl.vajraStart || t > tl.impact) return Float.NaN;
            float dropStart = tl.impact - 30;
            if (t < dropStart) {
                float p = (t - tl.vajraStart) / (dropStart - tl.vajraStart);
                p = 1 - (1 - p) * (1 - p);
                return Mth.lerp(p, 170, 55);
            }
            float p = (t - dropStart) / 30F;
            return Mth.lerp(p * p, 55, 0);
        }
    }

    public static final Map<Integer, StrikeFx> ACTIVE = new HashMap<>();

    public static void onStrikePacket(ModNetwork.StrikeFxPacket p) {
        StrikeFx fx = ACTIVE.get(p.strikeId);
        if (fx == null) {
            fx = new StrikeFx(p.strikeId, new Vec3(p.x, p.y, p.z), Timeline.of(p.mode), p.age);
            ACTIVE.put(p.strikeId, fx);
        } else {
            fx.age = Math.max(fx.age, p.age);
        }
        if (p.shooter) CinematicDirector.startOrSync(fx);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        Iterator<StrikeFx> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            StrikeFx fx = it.next();
            fx.age++;
            if (fx.age > Math.max(fx.tl.end, fx.tl.impact + 120)) it.remove();
        }
        CinematicDirector.tickActive();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        CinematicDirector.stopActive();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            CinematicDirector.renderSpaceScene(event.getPoseStack(), event.getCamera(), event.getPartialTick());
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        PoseStack ps = event.getPoseStack();
        Camera cam = event.getCamera();
        float pt = event.getPartialTick();
        for (StrikeFx fx : ACTIVE.values()) render(fx, ps, cam, pt);
    }

    private static void render(StrikeFx fx, PoseStack ps, Camera cam, float pt) {
        Timeline tl = fx.tl;
        float t = fx.time(pt);
        float fadeOut = Mth.clamp(1 - (t - (tl.impact + 50)) / 50F, 0, 1);
        if (t < tl.planetsStart || fadeOut <= 0) return;
        Vec3 c = fx.pos;
        float aligned = Mth.clamp((t - tl.alignedAt) / 30F, 0, 1);
        double topY = c.y + fx.topY();

        // जुड़ने वाली रोशनी की डोर
        FxDraw.begin(true);
        if (aligned > 0) {
            FxDraw.cylinder(ps, cam, c.add(0, PLANET_Y[0], 0), (float) (fx.topY() - PLANET_Y[0]), 0.4F + aligned * 0.4F, BEAM,
                    1F, 0.85F, 0.5F, 0.6F * aligned * fadeOut, t * 0.03F);
            FxDraw.flat(ps, cam, new Vec3(c.x, topY + 14, c.z), 34 * aligned, SIGIL, 1F, 0.8F, 0.4F, 0.9F * aligned * fadeOut, t * 0.6F);
            FxDraw.flat(ps, cam, new Vec3(c.x, topY + 14.5, c.z), 22 * aligned, SIGIL, 0.6F, 0.8F, 1F, 0.7F * aligned * fadeOut, -t * 0.9F);
        }
        // ज़मीन पर जादुई घेरा
        float groundP = Mth.clamp((t - tl.planetsStart) / (float) Math.max(1, tl.alignedAt - tl.planetsStart), 0, 1);
        if (t < tl.impact + 5) {
            FxDraw.flat(ps, cam, c.add(0, 0.15, 0), 3 + groundP * 14, SIGIL, 1F, 0.75F, 0.35F, 0.85F * groundP, t * 0.8F);
            FxDraw.flat(ps, cam, c.add(0, 0.2, 0), 2 + groundP * 8, SIGIL, 1F, 0.3F, 0.2F, 0.6F * groundP, -t * 1.4F);
        }
        // ग्रहों की चमक
        for (int i = 0; i < Timeline.PLANETS; i++) {
            if (!fx.planetVisible(i, pt)) continue;
            Vec3 p = fx.planetPos(i, pt);
            FxDraw.billboard(ps, cam, p, PLANET_SIZE[i] * 2.2F, GLOW, 1F, 0.85F, 0.6F, (0.35F + 0.4F * aligned) * fadeOut, 0);
        }
        FxDraw.end();

        // ग्रह
        FxDraw.begin(false);
        for (int i = 0; i < Timeline.PLANETS; i++) {
            if (!fx.planetVisible(i, pt)) continue;
            Vec3 p = fx.planetPos(i, pt);
            FxDraw.billboard(ps, cam, p, PLANET_SIZE[i], PLANET_TEX[i], 1, 1, 1, fadeOut, 0);
        }
        FxDraw.end();

        // वज्र
        float vh = fx.vajraHeight(pt);
        if (!Float.isNaN(vh)) {
            Vec3 vp = c.add(0, vh, 0);
            FxDraw.begin(true);
            FxDraw.billboard(ps, cam, vp, 14, GLOW, 1F, 0.85F, 0.4F, 0.9F, 0);
            FxDraw.billboard(ps, cam, vp, 24, GLOW, 0.5F, 0.7F, 1F, 0.5F, 0);
            FxDraw.end();
            MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
            ps.pushPose();
            Vec3 cp = cam.getPosition();
            ps.translate(vp.x - cp.x, vp.y - cp.y, vp.z - cp.z);
            FxDraw.vajra(ps, buf, 3.2F, t * 4F);
            ps.popPose();
            buf.endBatch();
        }

        // ऑर्बिटल बीम
        float beamIn = Mth.clamp((t - (tl.impact - 25)) / 25F, 0, 1);
        float beamOut = Mth.clamp(1 - (t - (tl.impact + 40)) / 30F, 0, 1);
        if (beamIn > 0 && beamOut > 0) {
            float a = beamIn * beamOut;
            float pulse = 1 + 0.15F * Mth.sin(t * 1.3F);
            float h = (float) (topY - c.y) + 10;
            float rr = t < tl.impact ? 0.4F + beamIn * 1.2F : 6F * pulse;
            FxDraw.begin(true);
            FxDraw.cylinder(ps, cam, c, h, rr, BEAM, 1F, 1F, 1F, a, -t * 0.08F);
            FxDraw.cylinder(ps, cam, c, h, rr * 2.2F, BEAM, 1F, 0.8F, 0.4F, 0.55F * a, -t * 0.05F);
            FxDraw.cylinder(ps, cam, c, h, rr * 4.5F, BEAM, 0.6F, 0.4F, 1F, 0.25F * a, -t * 0.03F);
            FxDraw.billboard(ps, cam, c.add(0, 1, 0), rr * 5, GLOW, 1F, 0.9F, 0.7F, a, 0);
            FxDraw.end();
        }

        // शॉकवेव
        float sw = t - tl.impact;
        if (sw >= 0 && sw < 40) {
            float p = sw / 40F;
            FxDraw.begin(true);
            FxDraw.flat(ps, cam, c.add(0, 0.5, 0), 8 + p * 110, RING, 1F, 0.85F, 0.6F, 1 - p, 0);
            if (sw > 6) {
                float p2 = (sw - 6) / 34F;
                FxDraw.flat(ps, cam, c.add(0, 1.5, 0), 4 + p2 * 70, RING, 0.6F, 0.6F, 1F, (1 - p2) * 0.8F, 30);
            }
            FxDraw.end();
        }
    }
}
