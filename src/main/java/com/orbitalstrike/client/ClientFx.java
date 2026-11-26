package com.orbitalstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
    public static final ResourceLocation[] PLANET_MAP = {
            FxDraw.fx("map_moon"), FxDraw.fx("map_mars"), FxDraw.fx("map_mercury"), FxDraw.fx("map_venus"), FxDraw.fx("map_jupiter"),
            FxDraw.fx("map_saturn"), FxDraw.fx("map_rahu"), FxDraw.fx("map_ketu"), FxDraw.fx("map_sun")};
    public static final float[] PLANET_RADIUS = {2.6F, 3.0F, 2.2F, 3.4F, 6.2F, 5.0F, 4.2F, 4.2F, 9.5F};
    public static final float[] PLANET_TILT = {6, 25, 0, 177, 3, 27, 40, -30, 7};
    private static final ResourceLocation SATURN_RING = FxDraw.fx("saturn_ring");
    private static final ResourceLocation DOME = FxDraw.fx("dome");
    private static final net.minecraft.world.level.block.Block[] DEBRIS = {
            net.minecraft.world.level.block.Blocks.STONE, net.minecraft.world.level.block.Blocks.DIRT,
            net.minecraft.world.level.block.Blocks.GRASS_BLOCK, net.minecraft.world.level.block.Blocks.COBBLESTONE,
            net.minecraft.world.level.block.Blocks.DEEPSLATE, net.minecraft.world.level.block.Blocks.ANDESITE,
            net.minecraft.world.level.block.Blocks.OAK_LOG};
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

        public static final float GIANT = 36F;

        /** विशाल VIP कहाँ खड़ा है (पैर), null = नहीं दिख रहा */
        public Vec3 giantPos(float partial) {
            if (tl.vajraStart < 0) return null;
            float t = time(partial);
            if (t < tl.vajraStart || t > tl.impact + 75) return null;
            double ang = (id * 2.3) % (Math.PI * 2);
            Vec3 feet = pos.add(Math.cos(ang) * 44, -1, Math.sin(ang) * 44);
            float p = Mth.clamp((t - tl.vajraStart) / 60F, 0, 1);
            p = 1 - (1 - p) * (1 - p) * (1 - p);
            return feet.add(0, (1 - p) * 160, 0);
        }

        public float giantYaw() {
            double ang = (id * 2.3) % (Math.PI * 2);
            double dx = -Math.cos(ang), dz = -Math.sin(ang);       // टारगेट की ओर
            return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        }

        public float giantAlpha(float partial) {
            float t = time(partial);
            return Mth.clamp(Math.min((t - tl.vajraStart) / 25F, (tl.impact + 75 - t) / 30F), 0, 1);
        }

        /** हाथ ऊपर उठाना, फिर दोनों मुट्ठियाँ नीचे टारगेट पर */
        public AlienRenderer.Pose giantPose(float partial) {
            float t = time(partial);
            AlienRenderer.Pose p = new AlienRenderer.Pose();
            float raise = Mth.clamp((t - (tl.vajraStart + 55)) / 40F, 0, 1);
            raise = raise * raise * (3 - 2 * raise);
            float slam = Mth.clamp((t - (tl.impact - 12)) / 12F, 0, 1);
            slam = slam * slam;
            float arm = Mth.lerp(slam, Mth.lerp(raise, -10, -178), -55);
            p.rightArm = arm + Mth.sin(t * 0.5F) * 3 * (1 - slam);
            p.leftArm = arm - Mth.sin(t * 0.5F) * 3 * (1 - slam);
            p.rightArmOut = 6 + 10 * raise * (1 - slam);
            p.leftArmOut = 6 + 10 * raise * (1 - slam);
            p.lean = Mth.lerp(slam, -8 * raise, 38);
            p.headPitch = Mth.lerp(slam, -15 * raise, 35);
            if (t > tl.impact) {
                float rec = Mth.clamp((t - tl.impact - 15) / 40F, 0, 1);
                p.lean = Mth.lerp(rec, 38, 5);
                p.rightArm = Mth.lerp(rec, -55, -5);
                p.leftArm = Mth.lerp(rec, -55, -5);
                p.headPitch = Mth.lerp(rec, 35, -10);
            }
            return p;
        }
    }

    public static final float SHIP_SCALE = 3.5F;
    public static final float SHIP_YAW = -65F;

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

    private static void renderDebris(StrikeFx fx, PoseStack ps, Camera cam, float t) {
        Timeline tl = fx.tl;
        float liftStart = Math.max(tl.planetsStart, tl.impact - 90);
        if (t < liftStart || t > tl.impact + 50) return;
        java.util.Random r = new java.util.Random(fx.id * 7919L);
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cp = cam.getPosition();
        float lift = Mth.clamp((t - liftStart) / (tl.impact - liftStart), 0, 1);
        lift = lift * lift * (3 - 2 * lift);
        for (int k = 0; k < 48; k++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 5 + r.nextDouble() * 34;
            float size = 0.8F + r.nextFloat() * 2.0F, rise = 5 + r.nextFloat() * 22, spin = 1 + r.nextFloat() * 4;
            net.minecraft.world.level.block.Block b = DEBRIS[r.nextInt(DEBRIS.length)];
            float delay = r.nextFloat() * 0.4F;
            float myLift = Mth.clamp((lift - delay) / (1 - delay), 0, 1);
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 p = fx.pos.add(dir.scale(rad)).add(0, myLift * rise + Math.sin(t * 0.1 + k) * 0.5 * myLift - 1, 0);
            float sc = size;
            if (t > tl.impact) {
                float dt = t - tl.impact;
                p = p.add(dir.scale(dt * 1.6 * (1 + rad / 30))).add(0, dt * 1.1 - dt * dt * 0.035, 0);
                sc = size * Mth.clamp(1 - (dt - 30) / 20F, 0, 1);
            }
            if (myLift <= 0.01F || sc <= 0.02F) continue;
            ps.pushPose();
            ps.translate(p.x - cp.x, p.y - cp.y, p.z - cp.z);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t * spin * 3));
            ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(t * spin * 2));
            FxDraw.box(ps, buf, b, -sc / 2, -sc / 2, -sc / 2, sc, sc, sc);
            ps.popPose();
        }
        buf.endBatch();
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

        // ग्रह: असली 3D गोले, सूरज से रोशनी
        Vec3 sunP = fx.planetVisible(Timeline.PLANETS - 1, pt) ? fx.planetPos(Timeline.PLANETS - 1, pt) : c.add(60, 400, 40);
        FxDraw.beginOpaque();
        for (int i = 0; i < Timeline.PLANETS; i++) {
            if (!fx.planetVisible(i, pt)) continue;
            Vec3 p = fx.planetPos(i, pt);
            Vec3 L = sunP.subtract(p);
            if (L.lengthSqr() < 1) L = new Vec3(0.4, 1, 0.3);
            L = L.normalize().add(cam.getPosition().subtract(p).normalize().scale(0.9));   // कैमरे की तरफ़ से भी रोशनी
            boolean sun = i == Timeline.PLANETS - 1;
            FxDraw.sphere(ps, cam, p, PLANET_RADIUS[i], PLANET_MAP[i], t * (1.2F + i * 0.3F), PLANET_TILT[i], L, sun, fadeOut);
        }
        FxDraw.end();
        if (fx.planetVisible(5, pt)) {    // शनि की रिंग
            FxDraw.begin(false);
            RenderSystem.enableDepthTest();
            FxDraw.tiltedDisc(ps, cam, fx.planetPos(5, pt), 11.5F, SATURN_RING, PLANET_TILT[5], t * 0.5F, 0.95F * fadeOut + 0.05F);
            FxDraw.end();
        }
        if (fx.planetVisible(Timeline.PLANETS - 1, pt)) {   // सूरज की चमक
            FxDraw.begin(true);
            FxDraw.billboard(ps, cam, sunP, 26, GLOW, 1F, 0.85F, 0.5F, 0.8F * fadeOut, 0);
            FxDraw.end();
        }

        // हवा में उठता मलबा (प्रहार से पहले), फिर धमाके में चारों ओर उड़ता है
        renderDebris(fx, ps, cam, t);

        // विशाल VIP (आसमान से उतरकर दोनों मुट्ठियाँ मारता है)
        Vec3 gp = fx.giantPos(pt);
        if (gp != null) {
            float ga = fx.giantAlpha(pt);
            FxDraw.begin(true);
            FxDraw.billboard(ps, cam, gp.add(0, StrikeFx.GIANT * 1.1, 0), StrikeFx.GIANT * 1.3F, GLOW, 0.6F, 0.3F, 1F, 0.35F * ga, 0);
            FxDraw.end();
            MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
            ps.pushPose();
            Vec3 cp = cam.getPosition();
            ps.translate(-cp.x, -cp.y, -cp.z);
            AlienRenderer.render(ps, buf, AlienRenderer.ZARA, gp.x, gp.y, gp.z, fx.giantYaw(), fx.giantPose(pt), StrikeFx.GIANT, ga);
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

        // बीम के चारों ओर घूमते छल्ले
        if (beamIn > 0 && beamOut > 0) {
            float h2 = (float) (topY - c.y);
            float rr = t < tl.impact ? 2 + beamIn * 3 : 9;
            FxDraw.begin(true);
            for (int k = 0; k < 7; k++) {
                float frac = ((k / 7F) + t * 0.015F) % 1F;
                FxDraw.flat(ps, cam, c.add(0, frac * h2, 0), rr * (1.2F + 0.4F * Mth.sin(t * 0.3F + k)), RING, 0.8F, 0.5F, 1F, 0.7F * beamIn * beamOut, t * 6 + k * 40);
            }
            FxDraw.end();
        }

        // 3D शॉकवेव गुंबद
        float dm = t - tl.impact;
        if (dm >= 0 && dm < 30) {
            float p = dm / 30F;
            float R = 6 + p * com.orbitalstrike.Config.CRATER_RADIUS.get() * 2.6F;
            FxDraw.begin(true);
            RenderSystem.enableDepthTest();
            FxDraw.sphere(ps, cam, c, R, DOME, t * 3, 0, new Vec3(0, 1, 0), true, 0.55F * (1 - p));
            FxDraw.sphere(ps, cam, c, R * 0.7F, DOME, -t * 4, 0, new Vec3(0, 1, 0), true, 0.4F * (1 - p));
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
