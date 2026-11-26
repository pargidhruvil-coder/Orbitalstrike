package com.orbitalstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalstrike.ModSounds;
import com.orbitalstrike.Timeline;
import com.orbitalstrike.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * नवग्रह कैनन सिनेमैटिक (v4):
 * अंतरिक्ष -> एलियन शिप की ओर उड़ान -> दरवाज़ा खुलता है -> अंदर ज़ोरो और ज़ारा -> कोड, लाल बटन
 * -> नवग्रह संरेखण -> शिप की तोप -> प्रहार -> खिलाड़ी का चेहरा।
 */
public class CinematicDirector {
    private static CinematicDirector active;

    private record Line(int start, int dur, String speaker, ChatFormatting color, String key) {}

    private record Shot(int start, Vec3 from, Vec3 to, Vec3 lookFrom, Vec3 lookTo) {}

    private enum Phase { SPACE, APPROACH, INTERIOR, ORBIT, PLANETS, SHIP, IMPACT, END }

    // अंतरिक्ष वाले सीन में शिप की जगह और पैमाना
    private static final Vec3 SHIP = new Vec3(-30, -12, -260);
    private static final float SHIP_SCALE = 3.0F;
    // अंदर के सीन में एलियन की जगह
    private static final Vec3 ZORO_POS = new Vec3(1.4, 0, -1.6);
    private static final Vec3 ZARA_POS = new Vec3(-1.4, 0, -1.6);
    private static final Vec3 BUTTON = new Vec3(3.3, 1.05, 1.0);   // अलग खड़े स्टैंड पर बड़ा लाल बटन

    private final ClientFx.StrikeFx fx;
    private final Timeline tl;
    private final Vec3 eye;
    private final float playerYaw;
    private final Marker cam;
    private final CameraType savedType;
    private final List<Line> lines = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    private Phase lastPhase = null;
    private Object lastShot = null;
    private int lastAge = -1;
    private int lastChars = -1;
    private Line lastLine = null;
    private final int ending;
    private Vec3 sceneCam = Vec3.ZERO, sceneCamO = Vec3.ZERO;

    // ---------------------------------------------------------------- चालू / बंद
    public static boolean isActive() { return active != null; }

    public static void startOrSync(ClientFx.StrikeFx fx) {
        if (active != null && active.fx == fx) return;
        stopActive();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        active = new CinematicDirector(fx);
        mc.setScreen(new CinematicScreen());
    }

    public static void tickActive() {
        if (active != null) active.tick();
    }

    public static void stopActive() {
        if (active == null) return;
        CinematicDirector d = active;
        active = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        mc.options.setCameraType(d.savedType);
        if (mc.screen instanceof CinematicScreen) mc.setScreen(null);
    }

    public static void requestSkip() {
        if (active != null && active.fx.age < active.tl.impact - 40) {
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.SkipPacket());
        }
    }

    private CinematicDirector(ClientFx.StrikeFx fx) {
        Minecraft mc = Minecraft.getInstance();
        this.fx = fx;
        this.tl = fx.tl;
        this.eye = mc.player.getEyePosition();
        this.playerYaw = mc.player.getYRot();
        this.savedType = mc.options.getCameraType();
        this.ending = 14 + (fx.id % 3);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        this.cam = EntityType.MARKER.create(mc.level);
        if (cam != null) {
            cam.setPos(eye);
            mc.setCameraEntity(cam);
        }
        buildScript();
    }

    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }

    private void buildScript() {
        ChatFormatting zo = ChatFormatting.GREEN, za = ChatFormatting.LIGHT_PURPLE;
        if (tl.mode == Timeline.LONG) {
            lines.add(new Line(262, 70, "zara", za, "z1"));
            lines.add(new Line(345, 70, "zoro", zo, "z2"));
            lines.add(new Line(425, 62, "zara", za, "z3"));
            lines.add(new Line(488, 30, "zoro", zo, "z4"));
            lines.add(new Line(519, 20, "zoro", zo, "z4b"));
            lines.add(new Line(540, 32, "zara", za, "z5"));
            lines.add(new Line(605, 60, "zoro", zo, "z6"));
            lines.add(new Line(692, 45, "zara", za, "z7"));
            lines.add(new Line(742, 55, "zoro", zo, "z8"));
            lines.add(new Line(802, 50, "zara", za, "z8b"));
            lines.add(new Line(tl.vajraStart + 20, 70, "zara", za, "z9"));
            lines.add(new Line(tl.impact - 45, 45, "zara", za, "z10"));

            Vec3 zoroHead = ZORO_POS.add(0, 1.75, 0), zaraHead = ZARA_POS.add(0, 1.75, 0);
            shots.add(new Shot(250, v(0, 1.8, 3.6), v(0, 1.7, 2.5), v(0, 1.3, -3), v(0, 1.4, -3)));
            shots.add(new Shot(340, v(2.7, 1.75, 0.4), v(2.4, 1.75, 0.1), zoroHead, zoroHead));
            shots.add(new Shot(420, v(-2.7, 1.75, 0.4), v(-2.4, 1.75, 0.1), zaraHead, zaraHead));
            shots.add(new Shot(490, v(0, 2.6, 3.4), v(0.6, 2.4, 2.9), v(0, 1.2, -1), v(0.5, 1.2, 0)));
            shots.add(new Shot(540, v(1.6, 2.2, 3.5), v(1.9, 2.0, 3.2), BUTTON.add(-0.3, 0.3, -0.3), BUTTON.add(0, 0.2, 0)));
            shots.add(new Shot(575, v(-2.6, 2.5, 3.3), v(-2.2, 2.4, 3.0), v(1.5, 1.0, -0.5), v(-1.4, 1.0, -2.0)));
            shots.add(new Shot(600, v(-0.9, 2.6, -0.5), v(-1.2, 2.4, -1.0), v(-1.4, 0.9, -2.6), v(-1.4, 0.9, -2.6)));
            shots.add(new Shot(690, v(-1.4, 1.7, 1.7), v(-1.4, 1.7, 1.4), v(-1.4, 1.75, -0.4), v(-1.4, 1.75, -0.4)));
            shots.add(new Shot(735, v(0, 0.9, 1.6), v(0, 0.95, 1.3), v(0, 1.9, -0.8), v(0, 1.95, -0.8)));
        } else if (tl.mode == Timeline.MEDIUM) {
            lines.add(new Line(tl.vajraStart + 5, 55, "zara", za, "z9"));
            lines.add(new Line(tl.impact - 45, 45, "zara", za, "z10"));
        }
        lines.add(new Line(tl.impact + 72, Math.max(40, tl.end - tl.impact - 75), "player", ChatFormatting.WHITE, "l" + ending));
    }

    // ---------------------------------------------------------------- चरण
    private Phase phase(int a) {
        if (tl.mode == Timeline.LONG && a < tl.planetsStart) {
            if (a < Timeline.APPROACH_START) return Phase.SPACE;
            if (a < Timeline.INTERIOR_START) return Phase.APPROACH;
            if (a < Timeline.ORBIT_START) return Phase.INTERIOR;
            return Phase.ORBIT;
        }
        int planetsEnd = tl.vajraStart >= 0 ? tl.vajraStart : tl.impact - 30;
        if (a < planetsEnd) return Phase.PLANETS;
        if (a < tl.impact - 30) return Phase.SHIP;
        if (a < tl.impact + 70) return Phase.IMPACT;
        return Phase.END;
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientFx.ACTIVE.containsValue(fx)) {
            stopActive();
            return;
        }
        int a = fx.age;
        if (a >= tl.end) {
            stopActive();
            return;
        }
        if (mc.screen == null) mc.setScreen(new CinematicScreen());

        Phase ph = phase(a);
        boolean cut = ph != lastPhase || a < lastAge || a > lastAge + 2;
        lastPhase = ph;
        if (cam != null) camera(ph, a, cut);

        for (int t = Math.max(lastAge + 1, a - 1); t <= a; t++) sounds(t);
        lastAge = a;

        if (a == tl.impact) {
            ClientEvents.flash(1.0F);
            ClientEvents.addShake(3.0F);
        } else if (a > tl.impact - 30 && a < tl.impact) {
            ClientEvents.addShake(0.25F);
        } else if (a > tl.impact && a < tl.impact + 60) {
            ClientEvents.addShake(0.35F);
        }
    }

    private void sounds(int t) {
        if (tl.mode == Timeline.LONG) {
            if (t == 5 || t == 60) play(ModSounds.SHIP_HUM.get(), 0.7F, 0.3F);
            if (t == 140) play(ModSounds.WHOOSH.get(), 0.8F, 1F);
            if (t == 195) play(ModSounds.DOOR.get(), 1F, 1F);
            if (t >= 250 && t < tl.planetsStart && (t - 250) % 60 == 0) play(ModSounds.SHIP_HUM.get(), 1F, 0.35F);
            if (t == 262) play(ModSounds.COMM_RING.get(), 1.2F, 0.8F);
            if (t >= 500 && t < 690 && (t - 500) % 48 == 0) play(ModSounds.ALARM.get(), 1F, 0.7F);
            if (t == 610 || t == 625 || t == 640 || t == 655) play(ModSounds.KEYPAD.get(), 1F, 1F);
            if (t == 680) play(ModSounds.ACCESS.get(), 1F, 1F);
            if (t == 512) play(SoundEvents.PLAYER_BIG_FALL, 0.8F, 1F);
            if (t == 800) play(ModSounds.WHOOSH.get(), 0.6F, 1F);
            if (t == 812) play(ModSounds.SHIP_CHARGE.get(), 1.4F, 0.8F);
            if (t == 840) { play(ModSounds.FIRE.get(), 0.6F, 1F); ClientEvents.addShake(1.5F); }
            if (t == 560) {
                play(ModSounds.BUTTON.get(), 1F, 1F);
                ClientEvents.addShake(1.6F);
            }
        }
        for (int i = 0; i < Timeline.PLANETS; i++) {
            if (t == tl.planetArrive(i)) play(ModSounds.LOCK.get(), 0.8F + i * 0.05F, 0.5F);
        }
        if (t == tl.impact - 30) play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1F);
    }

    private static void play(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    // ---------------------------------------------------------------- कैमरा
    private static float ease(float p) {
        p = Mth.clamp(p, 0, 1);
        return p * p * (3 - 2 * p);
    }

    /** Catmull-Rom स्प्लाइन: स्मूद रास्ता */
    private static Vec3 spline(Vec3[] pts, float u) {
        int n = pts.length - 1;
        float f = Mth.clamp(u, 0, 1) * n;
        int i = Math.min(n - 1, (int) f);
        float t = f - i;
        Vec3 p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(n, i + 2)];
        double t2 = t * t, t3 = t2 * t;
        return new Vec3(
                0.5 * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                0.5 * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
                0.5 * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3));
    }

    // दरवाज़ा शिप की दाईं (+X) तरफ़: लोकल (5.6, -1.8, 5.7) x SHIP_SCALE
    private static final Vec3[] APPROACH_PATH = {
            SHIP.add(150, 28, 230), SHIP.add(115, 12, 120), SHIP.add(66, 0, 44), SHIP.add(44, -5.4, 20), SHIP.add(34, -5.4, 17.1), SHIP.add(28, -5.4, 17.1)};

    private void camera(Phase ph, int a, boolean cut) {
        Vec3 T = fx.pos;
        Vec3 pos, look;
        double baseAng = Math.atan2(eye.z - T.z, eye.x - T.x);
        switch (ph) {
            case SPACE -> {
                float u = a / (float) Timeline.APPROACH_START;
                Vec3 sc = Vec3.ZERO.lerp(APPROACH_PATH[0], u * u * 0.9);
                sceneSet(sc, cut);
                Vec3 lk = SHIP.add(60 - a * 0.5, 6, 0);
                rotTo(eye, eye.add(lk.subtract(sc)), cut);
                return;
            }
            case APPROACH -> {
                float u = ease((a - Timeline.APPROACH_START) / (float) (Timeline.INTERIOR_START - 10 - Timeline.APPROACH_START));
                Vec3 sc = spline(APPROACH_PATH, u);
                if (a < Timeline.APPROACH_START + 8) {   // SPACE से जुड़ाव
                    sc = Vec3.ZERO.lerp(APPROACH_PATH[0], 0.9).lerp(sc, (a - Timeline.APPROACH_START) / 8.0);
                }
                sceneSet(sc, cut);
                Vec3 lk = u < 0.6F ? SHIP.add(0, 4, 12) : SHIP.add(16.8, -5.4, 17.1);   // आखिर में खुलते दरवाज़े पर
                rotTo(eye, eye.add(lk.subtract(sc)), cut);
                return;
            }
            case INTERIOR -> {
                Shot s = shots.get(0);
                for (Shot sh : shots) if (a >= sh.start) s = sh;
                int end = tl.planetsStart;
                for (Shot sh : shots) if (sh.start > s.start) { end = sh.start; break; }
                boolean shotCut = s != lastShot;
                lastShot = s;
                float p = ease((a - s.start) / (float) Math.max(1, end - s.start));
                Vec3 sc = s.from.lerp(s.to, p);
                if (a >= 490 && a < 690) sc = sc.add(Math.sin(a * 1.7) * 0.03, Math.cos(a * 2.3) * 0.03, 0);
                sceneSet(sc, cut || shotCut);
                rotTo(eye, eye.add(s.lookFrom.lerp(s.lookTo, p).subtract(sc)), cut || shotCut);
                return;
            }
            case ORBIT -> {
                sceneSet(Vec3.ZERO, cut);
                float p = ease((a - Timeline.ORBIT_START) / 60F);
                setCam(eye, 4 + p * 4, 30 + p * 4, cut);
                return;
            }
            case PLANETS -> {
                float p = Mth.clamp((a - tl.planetsStart) / (float) Math.max(1, tl.alignedAt - tl.planetsStart), 0, 1);
                float after = Mth.clamp((a - tl.alignedAt) / 60F, 0, 1);
                double ang = baseAng + Math.toRadians((a - tl.planetsStart) * 0.35);
                double r = 32 + after * 20;
                pos = T.add(Math.cos(ang) * r, 6 + after * 10, Math.sin(ang) * r);
                look = T.add(0, 35 + ease(p) * 70 + after * 15, 0);
            }
            case SHIP -> {
                Vec3 gp = fx.giantPos(0);
                Vec3 g = gp != null ? gp : T.add(40, 0, 0);
                Vec3 away = new Vec3(T.x - g.x, 0, T.z - g.z).normalize();
                float G = ClientFx.StrikeFx.GIANT;
                if (a < tl.impact - 70) {
                    // टारगेट के पीछे नीचे से, ऊपर विशाल VIP की ओर
                    float p = ease((a - tl.vajraStart) / (float) Math.max(1, tl.impact - 70 - tl.vajraStart));
                    pos = T.add(away.scale(26 - p * 6)).add(away.yRot((float) Math.PI / 2).scale(8)).add(0, 3 + p * 2, 0);
                    look = g.add(0, G * (1.5 - p * 0.3), 0);
                } else {
                    // चेहरे का क्लोज़-अप (स्पीड-लाइन्स के साथ)
                    boolean first = lastShot != Phase.SHIP;
                    lastShot = Phase.SHIP;
                    float p = ease((a - (tl.impact - 70)) / 40F);
                    Vec3 face = g.add(0, G * 1.72, 0);
                    pos = face.add(away.scale(G * 0.25 + 26 - p * 10)).add(0, -2 + p, 0);
                    look = face;
                    setCam(pos, yawTo(pos, look), pitchTo(pos, look), cut || first);
                    return;
                }
            }
            case IMPACT -> {
                double ang = baseAng + Math.toRadians((a - (tl.impact - 30)) * 0.25);
                double r = 70 + Math.max(0, a - tl.impact) * 0.15;
                pos = T.add(Math.cos(ang) * r, 34, Math.sin(ang) * r);
                look = T.add(0, a < tl.impact ? 30 : 6, 0);
            }
            default -> {
                double yawR = Math.toRadians(playerYaw);
                Vec3 fwd = new Vec3(-Math.sin(yawR), 0, Math.cos(yawR));
                float push = ease((a - (tl.impact + 70)) / 60F);
                pos = eye.add(fwd.scale(2.9 - push * 0.7)).add(0, -0.05, 0);
                look = eye;
            }
        }
        setCam(pos, yawTo(pos, look), pitchTo(pos, look), cut);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90F;
    }

    private static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        return (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
    }

    private void rotTo(Vec3 pos, Vec3 look, boolean cut) {
        setCam(pos, yawTo(pos, look), pitchTo(pos, look), cut);
    }

    private void sceneSet(Vec3 sc, boolean cut) {
        sceneCamO = cut ? sc : sceneCam;
        sceneCam = sc;
    }

    private void setCam(Vec3 pos, float yaw, float pitch, boolean cut) {
        if (cut) {
            cam.setPos(pos);
            cam.xo = pos.x; cam.yo = pos.y; cam.zo = pos.z;
            cam.xOld = pos.x; cam.yOld = pos.y; cam.zOld = pos.z;
            cam.setYRot(yaw); cam.yRotO = yaw;
            cam.setXRot(pitch); cam.xRotO = pitch;
            return;
        }
        cam.xo = cam.getX(); cam.yo = cam.getY(); cam.zo = cam.getZ();
        cam.xOld = cam.xo; cam.yOld = cam.yo; cam.zOld = cam.zo;
        cam.setPos(pos);
        float prevYaw = cam.getYRot();
        cam.yRotO = prevYaw;
        cam.setYRot(prevYaw + Mth.wrapDegrees(yaw - prevYaw));
        cam.xRotO = cam.getXRot();
        cam.setXRot(pitch);
    }

    // ---------------------------------------------------------------- अंतरिक्ष / शिप / अंदर के सीन (दुनिया को ढक देते हैं)
    public static void renderSpaceScene(PoseStack ps, Camera camera, float pt) {
        CinematicDirector d = active;
        if (d == null || d.tl.mode != Timeline.LONG) return;
        float t = d.fx.time(pt);
        if (t >= d.tl.planetsStart) return;
        Phase ph = d.phase((int) t);
        Vec3 sc = d.sceneCamO.lerp(d.sceneCam, pt);
        boolean interior = ph == Phase.INTERIOR;
        if (ph == Phase.ORBIT) {
            d.renderOrbit(ps, camera, t);
            return;
        }

        // पृष्ठभूमि: गैलेक्सी, फिर 3D पृथ्वी और चाँद, सूरज
        RenderSystem.disableDepthTest();
        GalaxySkyRenderer.renderSkybox(ps, 1.0F, false);
        Vec3 c = camera.getPosition();
        Vec3 sunDir = dir(235, -22);
        FxDraw.begin(true);
        Vec3 sunPos = c.add(sunDir.scale(100));
        FxDraw.billboard(ps, camera, sunPos, 14, FxDraw.env("sun"), 1, 1, 1, 1, 0);
        FxDraw.billboard(ps, camera, sunPos, 40, FxDraw.fx("glow"), 1F, 0.9F, 0.7F, 0.35F, 0);   // लेंस फ़्लेयर
        FxDraw.billboard(ps, camera, c.add(dir(225, -10).scale(100)), 4, FxDraw.fx("glow"), 0.6F, 0.8F, 1F, 0.4F, 0);
        FxDraw.billboard(ps, camera, c.add(dir(212, 4).scale(100)), 7, FxDraw.fx("ring"), 0.7F, 0.6F, 1F, 0.25F, 0);
        FxDraw.end();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // GL_DEPTH_BUFFER_BIT
        FxDraw.beginOpaque();
        Vec3 earthPos = interior ? c.add(dir(180, 16).scale(100)) : c.add(dir(185, 34).scale(100));
        FxDraw.sphere(ps, camera, earthPos, interior ? 40 : 52, FxDraw.fx("map_earth"), t * 0.06F, 23, sunDir, false, 1);
        FxDraw.sphere(ps, camera, c.add(dir(150, -12).scale(100)), 6, FxDraw.fx("map_moon"), t * 0.1F, 6, sunDir, false, 1);
        FxDraw.end();
        FxDraw.begin(true);
        FxDraw.billboard(ps, camera, earthPos, interior ? 46 : 60, FxDraw.fx("glow"), 0.35F, 0.6F, 1F, 0.35F, 0);   // वायुमंडल
        FxDraw.end();

        RenderSystem.clear(256, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(-sc.x, -sc.y, -sc.z);
        if (interior) {
            d.renderInterior(ps, buf, t);
        } else {
            ps.pushPose();
            ps.translate(SHIP.x, SHIP.y, SHIP.z);
            float door = Mth.clamp((t - 195) / 30F, 0, 1);
            ShipModel.render(ps, buf, SHIP_SCALE, door, 0, ((int) (t / 10)) % 2 == 0);
            ps.popPose();
        }
        ps.popPose();
        buf.endBatch();
        if (!interior) {
            // आँखों और मीनारों की चमक (कैमरा-सापेक्ष -> दुनिया के निर्देशांक)
            ShipModel.renderGlow(ps, camera, c.add(SHIP.subtract(sc)), 0, SHIP_SCALE, t, 0);
        }
    }

    /** अंतरिक्ष से पृथ्वी पर निशाना: 3D पृथ्वी, लाल टारगेट, ऊपर शिप की तोप, फिर बीम */
    private void renderOrbit(PoseStack ps, Camera camera, float t) {
        float o = t - Timeline.ORBIT_START;
        RenderSystem.disableDepthTest();
        GalaxySkyRenderer.renderSkybox(ps, 1.0F, false);
        Vec3 c = camera.getPosition();
        Vec3 sunDir = dir(300, -15);
        RenderSystem.clear(256, Minecraft.ON_OSX);
        FxDraw.beginOpaque();
        Vec3 earth = c.add(dir(6, 38).scale(100));
        FxDraw.sphere(ps, camera, earth, 52, FxDraw.fx("map_earth"), 40 + o * 0.15F, 23, sunDir, false, 1);
        FxDraw.end();
        Vec3 toCam = c.subtract(earth).normalize();
        Vec3 mark = earth.add(toCam.scale(52.5));
        float pulse = 1 + 0.15F * Mth.sin(o * 0.6F);
        FxDraw.begin(true);
        FxDraw.billboard(ps, camera, earth, 60, FxDraw.fx("glow"), 0.35F, 0.6F, 1F, 0.35F, 0);
        FxDraw.billboard(ps, camera, mark, 7 * pulse, FxDraw.fx("ring"), 1F, 0.15F, 0.15F, 0.95F, o * 3);
        FxDraw.billboard(ps, camera, mark, 3.5F * pulse, FxDraw.fx("ring"), 1F, 0.3F, 0.3F, 0.9F, -o * 5);
        FxDraw.billboard(ps, camera, mark, 1.2F, FxDraw.fx("glow"), 1F, 0.2F, 0.2F, 1F, 0);
        FxDraw.end();
        // ऊपर शिप का निचला हिस्सा और तोप
        RenderSystem.clear(256, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 shipLocal = new Vec3(0, 2, 34);
        ps.pushPose();
        ps.translate(shipLocal.x, shipLocal.y, shipLocal.z);
        ps.mulPose(Axis.YP.rotationDegrees(90));
        float glow = Mth.clamp((o - 12) / 28F, 0, 1);
        ShipModel.render(ps, buf, 3.0F, 0, glow, ((int) (t / 8)) % 2 == 0);
        ps.popPose();
        buf.endBatch();
        Vec3 gun = c.add(shipLocal).add(ShipModel.cannonWorld(Vec3.ZERO, 90, 3.0F));
        FxDraw.begin(true);
        FxDraw.billboard(ps, camera, gun, 2 + glow * 10, FxDraw.fx("glow"), 0.75F, 0.4F, 1F, 0.4F + glow * 0.6F, 0);
        if (o >= 40) {        // बीम: तोप से पृथ्वी तक
            float b = Mth.clamp((o - 40) / 4F, 0, 1);
            for (int i = 0; i <= 40; i++) {
                Vec3 pnt = gun.lerp(mark, i / 40.0);
                FxDraw.billboard(ps, camera, pnt, 2.5F * b, FxDraw.fx("glow"), 0.85F, 0.6F, 1F, 0.9F, 0);
                FxDraw.billboard(ps, camera, pnt, 1.1F * b, FxDraw.fx("glow"), 1F, 1F, 1F, 1F, 0);
            }
            FxDraw.billboard(ps, camera, mark, 14 * b, FxDraw.fx("glow"), 1F, 0.9F, 0.8F, 1F, 0);
        }
        FxDraw.end();
    }

    private static void box(PoseStack ps, MultiBufferSource buf, Block b, double x, double y, double z, double sx, double sy, double sz) {
        FxDraw.box(ps, buf, b, (float) x, (float) y, (float) z, (float) sx, (float) sy, (float) sz);
    }

    private void renderInterior(PoseStack ps, MultiBufferSource buf, float t) {
        boolean alarm = t > 500 && t < 690;
        boolean alarmOn = alarm && ((int) (t / 6)) % 2 == 0;
        // साइंस-फ़िक्शन पैनल: फ़र्श, छत, दीवारें (खिड़की वाली सामने की दीवार में छेद)
        float rr = alarmOn ? 1F : 1F, gg = alarmOn ? 0.45F : 1F, bb = alarmOn ? 0.45F : 1F;
        ResourceLocation WALL = FxDraw.fx("panel_wall"), FLOOR = FxDraw.fx("panel_floor"), CEIL = FxDraw.fx("panel_ceiling");
        FxDraw.beginOpaque();
        FxDraw.panel(ps, FLOOR, -4, 0, 4, 8, 0, 0, 0, 0, -8, 4, 4, rr, gg, bb);
        FxDraw.panel(ps, CEIL, -4, 4, -4, 8, 0, 0, 0, 0, 8, 4, 4, rr, gg, bb);
        FxDraw.panel(ps, WALL, -4, 0, 4, 0, 0, -8, 0, 4, 0, 4, 2, rr, gg, bb);
        FxDraw.panel(ps, WALL, 4, 0, -4, 0, 0, 8, 0, 4, 0, 4, 2, rr, gg, bb);
        FxDraw.panel(ps, WALL, 4, 0, 4, -8, 0, 0, 0, 4, 0, 4, 2, rr, gg, bb);
        FxDraw.panel(ps, WALL, -4, 0, -4, 8, 0, 0, 0, 1, 0, 4, 0.5F, rr, gg, bb);
        FxDraw.panel(ps, WALL, -4, 3.2, -4, 8, 0, 0, 0, 0.8, 0, 4, 0.4F, rr, gg, bb);
        FxDraw.panel(ps, WALL, -4, 1, -4, 1, 0, 0, 0, 2.2, 0, 0.5F, 1.1F, rr, gg, bb);
        FxDraw.panel(ps, WALL, 3, 1, -4, 1, 0, 0, 0, 2.2, 0, 0.5F, 1.1F, rr, gg, bb);
        FxDraw.panel(ps, FxDraw.fx("panel_hazard"), -3, 0.01, -3.25, 6, 0, 0, 0, 0, -0.3, 6, 1, 1, 1, 1);
        FxDraw.panel(ps, FxDraw.fx("panel_hazard"), -1, 0.01, 3.7, 2, 0, 0, 0, 0, 0.3, 2, 1, 1, 1, 1);
        for (int s = -1; s <= 1; s += 2) {      // कंसोल की स्क्रीन
            double cx = s * 1.4;
            FxDraw.panel(ps, FxDraw.fx("panel_screen"), cx - 0.8, 0.95, -2.93, 1.6, 0, 0, 0, 0.7, 0, 1, 1, rr, gg, bb);
        }
        FxDraw.end();
        // होलोग्राम रडार स्क्रीन (दीवारों पर और खिड़की के ऊपर)
        FxDraw.begin(true);
        RenderSystem.enableDepthTest();
        float hp = 0.85F + 0.15F * Mth.sin(t * 0.3F);
        FxDraw.wallQuad(ps, FxDraw.fx("holo_radar"), -3.85, 2.2, -0.8, 90, 1.3F, 0, 0.6F, 1F, 0.7F, hp);
        FxDraw.wallQuad(ps, FxDraw.fx("holo_sweep"), -3.84, 2.2, -0.8, 90, 1.3F, -t * 6, 0.6F, 1F, 0.7F, 0.9F);
        FxDraw.wallQuad(ps, FxDraw.fx("holo_radar"), 3.85, 2.2, -0.8, -90, 1.3F, 0, 0.6F, 1F, 0.7F, hp);
        FxDraw.wallQuad(ps, FxDraw.fx("holo_sweep"), 3.84, 2.2, -0.8, -90, 1.3F, -t * 6, 0.6F, 1F, 0.7F, 0.9F);
        FxDraw.wallQuad(ps, FxDraw.fx("holo_radar"), 3.85, 2.2, 2.2, -90, 0.9F, 0, alarmOn ? 1F : 0.5F, alarmOn ? 0.3F : 0.8F, 1F, hp);
        FxDraw.wallQuad(ps, FxDraw.fx("holo_radar"), 0, 2.15, -3.2, 0, 0.75F, t * 0.5F, 0.6F, 1F, 0.8F, 0.55F);
        FxDraw.end();
        RenderSystem.enableDepthTest();
        box(ps, buf, Blocks.VERDANT_FROGLIGHT, -3.9, 0.0, -4, 0.1, 0.05, 8);
        box(ps, buf, Blocks.VERDANT_FROGLIGHT, 3.8, 0.0, -4, 0.1, 0.05, 8);
        box(ps, buf, Blocks.POLISHED_BLACKSTONE, -1, 0, 3.9, 2, 2.8, 0.1);
        box(ps, buf, Blocks.VERDANT_FROGLIGHT, -1.1, 2.8, 3.85, 2.2, 0.1, 0.1);
        box(ps, buf, Blocks.CRYING_OBSIDIAN, -3.05, 0.95, -4.05, 6.1, 0.1, 0.1);
        box(ps, buf, Blocks.CRYING_OBSIDIAN, -3.05, 3.15, -4.05, 6.1, 0.1, 0.1);
        box(ps, buf, Blocks.CRYING_OBSIDIAN, -0.05, 1.0, -4.05, 0.1, 2.2, 0.1);
        box(ps, buf, alarmOn ? Blocks.REDSTONE_LAMP : Blocks.SEA_LANTERN, -0.6, 3.9, -0.6, 1.2, 0.1, 1.2);
        // कंसोल
        for (int s = -1; s <= 1; s += 2) {
            double cx = s * 1.4;
            box(ps, buf, Blocks.POLISHED_DEEPSLATE, cx - 1.0, 0, -3.0, 2.0, 0.85, 0.7);
            box(ps, buf, Blocks.SMOOTH_STONE, cx - 1.05, 0.85, -3.05, 2.1, 0.08, 0.8);
            box(ps, buf, Blocks.GRAY_CONCRETE, cx - 0.85, 0.9, -3.02, 1.7, 0.8, 0.06);
            for (int k = 0; k < 4; k++) {
                boolean on = ((int) (t / 5) + k + s) % 3 != 0;
                box(ps, buf, on ? Blocks.LIME_CONCRETE : Blocks.GREEN_CONCRETE, cx - 0.8 + k * 0.35, 0.94, -2.55, 0.12, 0.04, 0.12);
            }
        }
        // ज़ारा का कीपैड
        boolean typing = t > 600 && t < 690;
        for (int r = 0; r < 4; r++)
            for (int col = 0; col < 3; col++) {
                boolean lit = typing && ((int) (t / 4) + r * 3 + col) % 7 == 0;
                box(ps, buf, lit ? Blocks.LIME_CONCRETE : Blocks.QUARTZ_BLOCK, -1.75 + col * 0.13, 0.94, -2.75 + r * 0.11, 0.1, 0.04, 0.08);
            }
        // ज़ोरो का बड़ा लाल बटन
        float press = t >= 560 ? 1 : 0;
        box(ps, buf, Blocks.POLISHED_DEEPSLATE, BUTTON.x - 0.4, 0, BUTTON.z - 0.4, 0.8, 0.95, 0.8);
        box(ps, buf, Blocks.YELLOW_CONCRETE, BUTTON.x - 0.42, 0.85, BUTTON.z - 0.42, 0.84, 0.06, 0.84);
        box(ps, buf, Blocks.IRON_BLOCK, BUTTON.x - 0.25, 0.95, BUTTON.z - 0.25, 0.5, 0.08, 0.5);
        box(ps, buf, Blocks.REDSTONE_BLOCK, BUTTON.x - 0.17, 1.03 - press * 0.07, BUTTON.z - 0.17, 0.34, 0.14, 0.34);

        // एलियन
        Vec3 zp = path(ZORO_PATH, t), zap = path(ZARA_PATH, t);
        AlienRenderer.render(ps, buf, AlienRenderer.ZORO, zp.x, 0, zp.z, alienYaw(ZORO_PATH, t, zoroYaw(t)), zoroPose(t));
        AlienRenderer.render(ps, buf, AlienRenderer.ZARA, zap.x, 0, zap.z, alienYaw(ZARA_PATH, t, zaraYaw(t)), zaraPose(t));
    }

    private boolean speaking(String who, float t) {
        for (Line l : lines) if (l.speaker.equals(who) && t >= l.start && t < l.start + l.dur) return true;
        return false;
    }

    // ---------------------------------------------------------------- एलियन की दौड़ (रास्ते: {समय, x, z})
    // ZORO = Crazy Gamer (गिरता है, फिर कोड डालता है), ZARA = VIP Dhruvil (भागकर बटन दबाता है)
    private static final float[][] ZORO_PATH = {
            {0, 1.4F, -1.6F}, {506, 1.4F, -1.6F}, {512, 1.0F, -0.7F},                            // ठोकर खाकर गिरना
            {548, 1.0F, -0.7F}, {562, 0.0F, -1.3F}, {598, -1.4F, -1.9F},                          // उठकर कीपैड तक
            {730, -1.4F, -1.9F}, {742, 0, -0.8F}, {2000, 0, -0.8F}};
    private static final float[][] ZARA_PATH = {
            {0, -1.4F, -1.6F}, {515, -1.4F, -1.6F}, {530, 0.3F, 0.3F}, {550, 2.75F, 0.6F},        // भागकर बटन तक
            {574, 2.75F, 0.6F}, {590, 1.2F, -1.0F}, {678, 1.2F, -1.0F}, {692, -1.4F, -0.4F}, {2000, -1.4F, -0.4F}};

    private static Vec3 path(float[][] p, float t) {
        for (int i = 0; i < p.length - 1; i++) {
            if (t <= p[i + 1][0]) {
                float f = Mth.clamp((t - p[i][0]) / Math.max(1, p[i + 1][0] - p[i][0]), 0, 1);
                return new Vec3(Mth.lerp(f, p[i][1], p[i + 1][1]), 0, Mth.lerp(f, p[i][2], p[i + 1][2]));
            }
        }
        float[] l = p[p.length - 1];
        return new Vec3(l[1], 0, l[2]);
    }

    /** दौड़ते समय चेहरा चलने की दिशा में, रुके हों तो fallback */
    private static float alienYaw(float[][] p, float t, float fallback) {
        Vec3 a = path(p, t - 1), b = path(p, t + 1);
        double dx = b.x - a.x, dz = b.z - a.z;
        if (dx * dx + dz * dz < 0.0004) return fallback;
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
    }

    private static float speed(float[][] p, float t) {
        return (float) path(p, t + 1).distanceTo(path(p, t - 1)) * 0.5F;
    }

    private float zoroYaw(float t) {          // Crazy Gamer
        if (t >= 506 && t < 560) return 150;
        if (t >= 598 && t < 730) return 180;
        if (t >= 742) return 0;
        float turn = ease((t - 335) / 20F);
        return Mth.lerp(turn, 180, 140);
    }

    private float zaraYaw(float t) {          // VIP Dhruvil
        if (t >= 550 && t < 578) return -90;
        if (t >= 590 && t < 678) return 180;
        if (t >= 692) return 0;
        float turn = ease((t - 255) / 20F);
        return Mth.lerp(turn, 180, 215);
    }

    private static void run(AlienRenderer.Pose p, float speed, float t) {
        if (speed < 0.02F) return;
        float amp = Math.min(1F, speed * 8F);
        float sw = Mth.sin(t * 1.1F) * 55 * amp;
        p.rightLeg = sw; p.leftLeg = -sw;
        p.rightArm = -sw * 0.9F; p.leftArm = sw * 0.9F;
        p.bob = Math.abs(Mth.sin(t * 1.1F)) * 0.08F * amp;
        p.lean = 12 * amp;
    }

    private AlienRenderer.Pose zoroPose(float t) {   // Crazy Gamer
        AlienRenderer.Pose p = new AlienRenderer.Pose();
        p.bob = Mth.sin(t * 0.1F) * 0.02F;
        p.rightArm = Mth.sin(t * 0.08F) * 4;
        p.leftArm = -Mth.sin(t * 0.08F) * 4;
        if (speaking("zoro", t)) {
            p.headPitch = Mth.sin(t * 0.9F) * 7;
            p.headYaw = Mth.sin(t * 0.37F) * 10;
            p.leftArm = -25 + Mth.sin(t * 0.5F) * 18;
            p.leftArmOut = 10;
        }
        if (t > 492 && t < 507) {          // घबराहट
            p.rightArm = -165 + Mth.sin(t * 1.3F) * 15;
            p.leftArm = -165 + Mth.cos(t * 1.1F) * 15;
            p.headYaw = Mth.sin(t * 2.2F) * 25;
        }
        run(p, speed(ZORO_PATH, t), t);
        if (t >= 506 && t < 560) {         // धड़ाम से पीछे गिरना, चक्कर, फिर उठना
            float fall = ease((t - 506) / 6F);
            float up = ease((t - 548) / 12F);
            p.lean = Mth.lerp(up, -88 * fall, 0);
            p.bob = Mth.lerp(up, 0.15F * fall, 0);
            p.rightArm = Mth.lerp(up, -150 * fall, 0);
            p.leftArm = Mth.lerp(up, -150 * fall, 0);
            p.rightArmOut = 30 * fall; p.leftArmOut = 30 * fall;
            p.headYaw = Mth.sin(t * 0.6F) * 30 * (1 - up);   // चक्कर
            p.rightLeg = -20 * fall * (1 - up); p.leftLeg = -35 * fall * (1 - up);
        }
        if (t >= 600 && t < 690) {         // कीपैड पर टाइपिंग
            p.rightArm = -55 + Mth.sin(t * 1.6F) * 10;
            p.leftArm = -55 + Mth.cos(t * 1.6F) * 10;
            p.headPitch = 30;
        }
        if (t >= 742) {                    // जीत की चीख़
            p.rightArm = -170 + Mth.sin(t * 0.8F) * 12;
            p.leftArm = -170 + Mth.cos(t * 0.8F) * 12;
            p.rightArmOut = 15; p.leftArmOut = 15;
            p.headPitch = -20;
            p.bob = Math.abs(Mth.sin(t * 0.4F)) * 0.15F;
        }
        return p;
    }

    private AlienRenderer.Pose zaraPose(float t) {   // VIP Dhruvil
        AlienRenderer.Pose p = new AlienRenderer.Pose();
        p.bob = Mth.sin(t * 0.11F + 1) * 0.02F;
        p.rightArm = Mth.sin(t * 0.09F) * 4;
        p.leftArm = -Mth.sin(t * 0.09F) * 4;
        if (speaking("zara", t)) {
            p.headPitch = Mth.sin(t * 1.1F) * 8;
            p.headYaw = Mth.sin(t * 0.43F) * 12;
            p.rightArm = -30 + Mth.sin(t * 0.6F) * 20;
            p.rightArmOut = 12;
        }
        run(p, speed(ZARA_PATH, t), t);
        if (t >= 550 && t < 575) {         // बटन: हाथ ऊपर... धड़ाम!
            float up = ease((t - 550) / 8F);
            float slam = ease((t - 558) / 3F);
            p.rightArm = Mth.lerp(slam, Mth.lerp(up, 0, -175), -50);
            p.rightArmOut = 8;
            p.headPitch = 25;
        }
        if (t >= 590 && t < 678) {         // खिड़की से पृथ्वी देखना, हाथ बाँधकर
            p.rightArm = -40; p.leftArm = -40;
            p.rightArmOut = -25; p.leftArmOut = -25;
            p.headPitch = 5;
        }
        return p;
    }

    /** Minecraft yaw/pitch से दिशा */
    private static Vec3 dir(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    // ---------------------------------------------------------------- GUI
    public static void renderGui(GuiGraphics g, float pt) {
        if (active != null) active.gui(g, pt);
    }

    private void gui(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = fx.time(pt);
        int a = fx.age;

        if (tl.mode == Timeline.LONG && t > 500 && t < 690) {
            int al = (int) ((0.16F + 0.12F * Mth.sin(t * 0.26F)) * 255);
            g.fill(0, 0, w, h, (al << 24) | 0xFF1010);
        }
        if (tl.vajraStart >= 0 && t > tl.vajraStart && t < tl.impact) {
            float s = Mth.clamp((t - tl.vajraStart) / 40F, 0, 1);
            g.fill(0, 0, w, h, ((int) (s * 80) << 24) | 0x0A0518);
        }
        // शिप में घुसते समय सफ़ेद फ़ेड
        if (tl.mode == Timeline.LONG && t > Timeline.INTERIOR_START - 12 && t < Timeline.INTERIOR_START + 10) {
            float f = 1 - Math.abs(t - Timeline.INTERIOR_START) / 12F;
            g.fill(0, 0, w, h, ((int) (Mth.clamp(f, 0, 1) * 255) << 24) | 0xE8FFF0);
        }

        int bar = (int) (h * 0.12F);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        if (tl.mode == Timeline.LONG && t < 105) {
            float al = Mth.clamp(Math.min(t / 15F, (105 - t) / 20F), 0, 1);
            big(g, font, Component.translatable("cinematic.orbitalstrike.meanwhile").withStyle(ChatFormatting.BOLD), w / 2, (int) (h * 0.32F), 3.0F, 0xFFFFFF, al);
            big(g, font, Component.translatable("cinematic.orbitalstrike.loc"), w / 2, (int) (h * 0.32F) + 34, 1.3F, 0xA8FF8A, al);
        }

        if (tl.mode == Timeline.LONG && t > 595 && t < 700) keypad(g, font, w, h, t);

        for (int i = 0; i < Timeline.PLANETS; i++) {
            float since = t - tl.planetArrive(i);
            float dur = Math.max(tl.planetStep + 6, 14);
            if (since >= 0 && since < dur) {
                float pop = 1 + Math.max(0, 0.6F - since * 0.08F);
                float al = Mth.clamp((dur - since) / 6F, 0, 1);
                MutableComponent name = Component.translatable("planet.orbitalstrike." + ClientFx.PLANET_IDS[i])
                        .withStyle(ClientFx.PLANET_COLOR[i], ChatFormatting.BOLD);
                big(g, font, name, w / 2, (int) (h * 0.22F), 3.0F * pop, 0xFFFFFF, al);
                big(g, font, Component.translatable("cinematic.orbitalstrike.planet_aligned").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                        w / 2, (int) (h * 0.22F) + (int) (20 * pop), 1.6F, 0xFFFFFF, al);
            }
        }
        if (t > tl.alignedAt && t < tl.alignedAt + 55) {
            float al = Mth.clamp(Math.min((t - tl.alignedAt) / 8F, (tl.alignedAt + 55 - t) / 10F), 0, 1);
            big(g, font, Component.translatable("cinematic.orbitalstrike.aligned").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    w / 2, (int) (h * 0.22F), 3.2F, 0xFFFFFF, al);
        }
        if (tl.mode == Timeline.LONG && t > Timeline.ORBIT_START + 5 && t < Timeline.ORBIT_START + 40) {
            float al = Mth.clamp(Math.min((t - Timeline.ORBIT_START - 5) / 6F, (Timeline.ORBIT_START + 40 - t) / 6F), 0, 1);
            big(g, font, Component.translatable("cinematic.orbitalstrike.lockon").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    w / 2, (int) (h * 0.25F), 3.2F, 0xFFFFFF, al);
        }
        // बीम पृथ्वी से टकराते ही सफ़ेद फ़्लैश, फिर ग्रहों पर कट
        if (tl.mode == Timeline.LONG && t > Timeline.ORBIT_START + 44 && t < tl.planetsStart + 8) {
            float f = t < tl.planetsStart ? (t - Timeline.ORBIT_START - 44) / (tl.planetsStart - Timeline.ORBIT_START - 44F) : 1 - (t - tl.planetsStart) / 8F;
            g.fill(0, 0, w, h, ((int) (Mth.clamp(f, 0, 1) * 255) << 24) | 0xFFFFFF);
        }
        // विशाल VIP के क्लोज़-अप पर स्पीड-लाइन्स
        if (tl.vajraStart >= 0 && t > tl.impact - 70 && t < tl.impact - 30) speedLines(g, w, h, t);
        if (t > tl.impact - 30 && t < tl.impact) {
            float shake = (t - (tl.impact - 30)) / 30F;
            int ox = (int) ((mc.level.random.nextFloat() - 0.5F) * 6 * shake), oy = (int) ((mc.level.random.nextFloat() - 0.5F) * 6 * shake);
            big(g, font, Component.translatable("cinematic.orbitalstrike.strike").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                    w / 2 + ox, (int) (h * 0.4F) + oy, 3.5F + shake * 1.5F, 0xFFFFFF, 1);
        }

        Line line = null;
        for (Line l : lines) if (t >= l.start && t < l.start + l.dur) line = l;
        if (line != null) dialogue(g, font, line, t, w, h, bar);
        else lastLine = null;

        if (a < tl.impact - 40) {
            Component skip = Component.translatable("cinematic.orbitalstrike.skip");
            g.drawString(font, skip, w - font.width(skip) - 8, h - bar + (bar - 8) / 2, 0x99FFFFFF);
        }
        float fade = Math.max(Mth.clamp(1 - t / 15F, 0, 1), Mth.clamp((t - (tl.end - 15)) / 15F, 0, 1));
        if (fade > 0) g.fill(0, 0, w, h, ((int) (fade * 255) << 24));
        ClientEvents.renderFlash(g, pt);
    }

    private void speedLines(GuiGraphics g, int w, int h, float t) {
        java.util.Random r = new java.util.Random((long) (t / 2));
        int cx = w / 2, cy = h / 2;
        for (int i = 0; i < 46; i++) {
            float ang = r.nextFloat() * 360F;
            float inner = Math.max(w, h) * (0.32F + r.nextFloat() * 0.15F);
            float len = Math.max(w, h) * (0.25F + r.nextFloat() * 0.4F);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
            g.fill((int) inner, -1, (int) (inner + len), 1 + r.nextInt(2), 0xB0FFFFFF);
            g.pose().popPose();
        }
    }

    private void dialogue(GuiGraphics g, Font font, Line l, float t, int w, int h, int bar) {
        Minecraft mc = Minecraft.getInstance();
        int boxW = Math.min(w - 40, 380);
        int boxH = 52, x0 = (w - boxW) / 2, x1 = x0 + boxW, y1 = h - bar - 6, y0 = y1 - boxH;
        g.fill(x0, y0, x1, y1, 0xC0000000);
        g.fill(x0, y0, x1, y0 + 1, l.speaker.equals("zara") ? 0xFFC060FF : l.speaker.equals("zoro") ? 0xFF80FF60 : 0xFFFFFFFF);
        int px = x0 + 6, py = y0 + 6, ps = 40;
        if (l.speaker.equals("player")) {
            if (mc.player != null) PlayerFaceRenderer.draw(g, mc.player.getSkinTextureLocation(), px, py, ps);
        } else {
            ResourceLocation skin = l.speaker.equals("zara") ? AlienRenderer.ZARA : AlienRenderer.ZORO;
            g.blit(skin, px, py, ps, ps, 8, 8, 8, 8, 64, 64);
        }
        Component name = Component.translatable("speaker.orbitalstrike." + l.speaker).withStyle(l.color, ChatFormatting.BOLD);
        g.drawString(font, name, px + ps + 8, py, 0xFFFFFF);
        String full = I18n.get("dialog.orbitalstrike." + l.key);
        int chars = Math.min(full.length(), (int) ((t - l.start) * 1.6F));
        if (l != lastLine) { lastLine = l; lastChars = -1; }
        if (chars > lastChars) {
            boolean alien = l.speaker.equals("zoro") || l.speaker.equals("zara");
            if (chars < full.length() && (alien ? chars % 3 == 0 : chars % 2 == 0)) {
                if (alien) {
                    float base = l.speaker.equals("zara") ? 1.3F : 0.8F;
                    play(ModSounds.CHIRP.get(), base + mc.level.random.nextFloat() * 0.25F, 0.8F);
                } else {
                    play(ModSounds.BLIP.get(), 1.0F, 0.5F);
                }
            }
            lastChars = chars;
        }
        List<FormattedCharSequence> rows = font.split(Component.literal(full.substring(0, chars)), x1 - (px + ps + 8) - 8);
        int ty = py + 12;
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            g.drawString(font, rows.get(i), px + ps + 8, ty, 0xFFFFFF);
            ty += 10;
        }
    }

    private void keypad(GuiGraphics g, Font font, int w, int h, float t) {
        int kw = 128, kh = 160;
        int x = w - kw - 30, y = h / 2 - kh / 2;
        float slide = Mth.clamp((t - 595) / 10F, 0, 1);
        x += (int) ((1 - slide) * (kw + 40));
        g.blit(new ResourceLocation("orbitalstrike", "textures/gui/keypad.png"), x, y, 0, 0, kw, kh, kw, kh);
        int typed = t >= 655 ? 4 : t >= 640 ? 3 : t >= 625 ? 2 : t >= 610 ? 1 : 0;
        String code = "9274".substring(0, typed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(i < code.length() ? code.charAt(i) : '_').append(' ');
        if (t >= 680) {
            Component ok = Component.translatable("cinematic.orbitalstrike.access").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            g.drawCenteredString(font, ok, x + kw / 2, y + 21, 0x55FF55);
        } else {
            g.drawCenteredString(font, sb.toString().trim(), x + kw / 2, y + 21, 0x55FF88);
        }
        g.drawCenteredString(font, Component.translatable("cinematic.orbitalstrike.code").withStyle(ChatFormatting.AQUA), x + kw / 2, y - 12, 0x7FE7FF);
        String keys = "123456789*0#";
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                char k = keys.charAt(r * 3 + c);
                int kx = x + 14 + c * 34, ky = y + 48 + r * 27;
                boolean lit = typed > 0 && t < 680 && code.charAt(typed - 1) == k && (t - (595 + typed * 15)) < 8;
                if (lit) g.fill(kx + 1, ky + 1, kx + 30, ky + 23, 0xFF2EE6A0);
                g.drawCenteredString(font, String.valueOf(k), kx + 15, ky + 8, 0xFFFFFF);
            }
        }
    }

    private static void big(GuiGraphics g, Font font, Component c, int cx, int cy, float scale, int color, float alpha) {
        if (alpha <= 0.02F) return;
        int a = Mth.clamp((int) (alpha * 255), 8, 255);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.drawCenteredString(font, c, 0, -4, (a << 24) | (color & 0xFFFFFF));
        g.pose().popPose();
    }

    @SuppressWarnings("unused")
    private static void unusedAxis() { Axis.YP.rotationDegrees(0); }
}
