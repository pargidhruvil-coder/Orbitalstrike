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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * नवग्रह कैनन का सिनेमैटिक: कैमरा, कहानी, डायलॉग, कीपैड, ग्रहों के नाम, वज्र, प्रहार।
 * सिर्फ़ गोली चलाने वाले खिलाड़ी के लिए।
 */
public class CinematicDirector {
    private static CinematicDirector active;

    private record Line(int start, int dur, String speaker, String portrait, ChatFormatting color, String key) {}

    private enum Phase { SPACE, PLANETS, VAJRA, IMPACT, END }

    private final ClientFx.StrikeFx fx;
    private final Timeline tl;
    private final Vec3 eye;
    private final float playerYaw;
    private final Marker cam;
    private final CameraType savedType;
    private final List<Line> lines = new ArrayList<>();
    private Phase lastPhase = null;
    private int lastAge = -1;
    private int lastChars = -1;
    private Line lastLine = null;
    private final int ending;

    // ---------------------------------------------------------------- चालू / बंद
    public static boolean isActive() { return active != null; }

    public static void startOrSync(ClientFx.StrikeFx fx) {
        if (active != null && active.fx == fx) return;   // उम्र ClientFx में बदल चुकी है
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

    private void buildScript() {
        ChatFormatting sys = ChatFormatting.AQUA, voice = ChatFormatting.LIGHT_PURPLE, vik = ChatFormatting.GOLD,
                chi = ChatFormatting.GREEN, ind = ChatFormatting.YELLOW;
        if (tl.mode == Timeline.LONG) {
            lines.add(new Line(60, 80, "system", "system", sys, "l1"));
            lines.add(new Line(150, 85, "voice", "voice", voice, "l2"));
            lines.add(new Line(245, 75, "vikram", "vikram", vik, "l3"));
            lines.add(new Line(325, 70, "chintu", "chintu", chi, "l4"));
            lines.add(new Line(400, 75, "vikram", "vikram", vik, "l5"));
            lines.add(new Line(480, 75, "chintu", "chintu", chi, "l6"));
            lines.add(new Line(560, 40, "vikram", "vikram", vik, "l7"));
            lines.add(new Line(600, 65, "chintu", "chintu", chi, "l8"));
            lines.add(new Line(670, 45, "system", "system", sys, "l9"));
            lines.add(new Line(715, 60, "chintu", "chintu", chi, "l10"));
            int vs = tl.vajraStart;
            lines.add(new Line(vs + 10, 65, "indra", "indra", ind, "l11"));
            lines.add(new Line(vs + 80, 70, "indra", "indra", ind, "l12"));
            lines.add(new Line(vs + 155, 45, "indra", "indra", ind, "l13"));
        } else if (tl.mode == Timeline.MEDIUM) {
            int vs = tl.vajraStart;
            lines.add(new Line(vs + 5, 55, "indra", "indra", ind, "l12"));
            lines.add(new Line(vs + 65, 50, "indra", "indra", ind, "l13"));
        }
        lines.add(new Line(tl.impact + 72, Math.max(40, tl.end - tl.impact - 75), "player", null, ChatFormatting.WHITE, "l" + ending));
    }

    // ---------------------------------------------------------------- हर टिक
    private Phase phase(int a) {
        if (tl.mode == Timeline.LONG && a < tl.planetsStart) return Phase.SPACE;
        int planetsEnd = tl.vajraStart >= 0 ? tl.vajraStart : tl.impact - 30;
        if (a < planetsEnd) return Phase.PLANETS;
        if (a < tl.impact - 30) return Phase.VAJRA;
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
        if (!(mc.screen instanceof CinematicScreen) && mc.screen == null) mc.setScreen(new CinematicScreen());

        Phase ph = phase(a);
        boolean cut = ph != lastPhase || a < lastAge || a > lastAge + 2;
        lastPhase = ph;
        if (cam != null) camera(ph, a, cut);

        // समय पर बजने वाली आवाज़ें (स्किप होने पर बीच वाली छोड़ दो)
        for (int t = Math.max(lastAge + 1, a - 1); t <= a; t++) sounds(t);
        lastAge = a;

        if (a == tl.impact) {
            ClientEvents.flash(1.0F);
            ClientEvents.addShake(3.0F);
        } else if (a > tl.impact - 30 && a < tl.impact) {
            ClientEvents.addShake(0.25F);
        }
    }

    private void sounds(int t) {
        if (tl.mode == Timeline.LONG) {
            if (t == 40) play(ModSounds.COMM_RING.get(), 1F, 1F);
            if (t == 150) play(ModSounds.VOICE.get(), 1F, 0.8F);
            if (t >= 480 && t < 690 && (t - 480) % 48 == 0) play(ModSounds.ALARM.get(), 1F, 0.7F);
            if (t == 605 || t == 620 || t == 635 || t == 650) play(ModSounds.KEYPAD.get(), 1F, 1F);
            if (t == 670) play(ModSounds.ACCESS.get(), 1F, 1F);
        }
        for (int i = 0; i < Timeline.PLANETS; i++) {
            if (t == tl.planetArrive(i)) play(ModSounds.LOCK.get(), 0.8F + i * 0.05F, 0.5F);
        }
        if (t == tl.impact - 30) {
            play(ModSounds.DIVINE.get(), 1.3F, 1F);
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1F);
        }
    }

    private static void play(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    // ---------------------------------------------------------------- कैमरा
    private void camera(Phase ph, int a, boolean cut) {
        Vec3 T = fx.pos;
        Vec3 pos;
        Vec3 look;
        double baseAng = Math.atan2(eye.z - T.z, eye.x - T.x);
        switch (ph) {
            case SPACE -> {
                setCam(eye, 200 + a * 0.035F, 6 + Mth.sin(a * 0.012F) * 4, cut);
                return;
            }
            case PLANETS -> {
                float p = Mth.clamp((a - tl.planetsStart) / (float) Math.max(1, tl.alignedAt - tl.planetsStart), 0, 1);
                float after = Mth.clamp((a - tl.alignedAt) / 60F, 0, 1);
                double ang = baseAng + Math.toRadians((a - tl.planetsStart) * 0.35);
                double r = 30 + after * 18;
                pos = T.add(Math.cos(ang) * r, 6 + after * 8, Math.sin(ang) * r);
                look = T.add(0, 35 + p * 70 + after * 10, 0);
            }
            case VAJRA -> {
                // खिलाड़ी के कंधे के पीछे से, ऊपर गिरते वज्र की ओर
                Vec3 dir = new Vec3(T.x - eye.x, 0, T.z - eye.z).normalize();
                pos = eye.add(dir.scale(-3.2)).add(dir.yRot((float) Math.PI / 2).scale(1.1)).add(0, 0.6, 0);
                float vh = fx.vajraHeight(0);
                look = T.add(0, Float.isNaN(vh) ? 60 : vh * 0.85, 0);
            }
            case IMPACT -> {
                double ang = baseAng + Math.toRadians((a - (tl.impact - 30)) * 0.25);
                pos = T.add(Math.cos(ang) * 62, 32, Math.sin(ang) * 62);
                look = T.add(0, a < tl.impact ? 25 : 4, 0);
            }
            default -> {
                // खिलाड़ी के चेहरे के सामने
                double yawR = Math.toRadians(playerYaw);
                Vec3 fwd = new Vec3(-Math.sin(yawR), 0, Math.cos(yawR));
                float push = Mth.clamp((a - (tl.impact + 70)) / 60F, 0, 1);
                pos = eye.add(fwd.scale(2.9 - push * 0.6)).add(0, -0.05, 0);
                look = eye;
            }
        }
        double dx = look.x - pos.x, dy = look.y - pos.y, dz = look.z - pos.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        setCam(pos, yaw, pitch, cut);
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

    // ---------------------------------------------------------------- अंतरिक्ष वाला सीन (दुनिया को ढक देता है)
    public static void renderSpaceScene(PoseStack ps, Camera camera, float pt) {
        CinematicDirector d = active;
        if (d == null || d.tl.mode != Timeline.LONG) return;
        float t = d.fx.time(pt);
        if (t >= d.tl.planetsStart) return;

        RenderSystem.disableDepthTest();
        GalaxySkyRenderer.renderSkybox(ps, 1.0F, false);
        FxDraw.begin(false);
        Vec3 c = camera.getPosition();
        FxDraw.billboard(ps, camera, c.add(dir(185, 32).scale(100)), 62, FxDraw.env("earth"), 1, 1, 1, 1, t * 0.01F);
        FxDraw.billboard(ps, camera, c.add(dir(150, -12).scale(100)), 7, FxDraw.env("moon"), 1, 1, 1, 1, 0);
        FxDraw.end();
        FxDraw.begin(true);
        FxDraw.billboard(ps, camera, c.add(dir(250, -25).scale(100)), 14, FxDraw.env("sun"), 1, 1, 1, 1, 0);
        FxDraw.end();
        // अलार्म के समय उपग्रह की लाल चमक
        RenderSystem.clear(256, Minecraft.ON_OSX);   // GL_DEPTH_BUFFER_BIT
        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        ps.pushPose();
        Vec3 sp = dir(205, 4).scale(11);
        ps.translate(sp.x, sp.y, sp.z);
        ps.mulPose(Axis.YP.rotationDegrees(t * 0.3F + 20));
        ps.mulPose(Axis.ZP.rotationDegrees(12));
        boolean alarm = t > 480 && t < 700;
        FxDraw.satellite(ps, buf, alarm ? ((int) (t / 6) % 2) : ((int) (t / 20) % 2) * 0.6F);
        ps.popPose();
        buf.endBatch();
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

        // अलार्म की लाल चमक
        if (tl.mode == Timeline.LONG && t > 480 && t < 700) {
            int al = (int) ((0.18F + 0.14F * Mth.sin(t * 0.26F)) * 255);
            g.fill(0, 0, w, h, (al << 24) | 0xFF1010);
        }
        // तूफ़ान में अँधेरा
        if (tl.vajraStart >= 0 && t > tl.vajraStart && t < tl.impact) {
            float s = Mth.clamp((t - tl.vajraStart) / 40F, 0, 1);
            g.fill(0, 0, w, h, ((int) (s * 90) << 24) | 0x05081A);
        }

        // फ़िल्मी पट्टियाँ
        int bar = (int) (h * 0.12F);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        // टाइटल कार्ड
        if (tl.mode == Timeline.LONG && t < 110) {
            float al = Mth.clamp(Math.min(t / 15F, (110 - t) / 20F), 0, 1);
            big(g, font, Component.translatable("cinematic.orbitalstrike.meanwhile").withStyle(ChatFormatting.BOLD), w / 2, (int) (h * 0.32F), 3.0F, 0xFFFFFF, al);
            big(g, font, Component.translatable("cinematic.orbitalstrike.loc"), w / 2, (int) (h * 0.32F) + 34, 1.3F, 0x7FE7FF, al);
        }

        // कीपैड
        if (tl.mode == Timeline.LONG && t > 588 && t < 705) keypad(g, font, w, h, t);

        // ग्रहों के नाम
        for (int i = 0; i < Timeline.PLANETS; i++) {
            float since = t - tl.planetArrive(i);
            float dur = Math.max(tl.planetStep + 6, 14);
            if (since >= 0 && since < dur) {
                float pop = 1 + Math.max(0, 0.6F - since * 0.08F);
                float al = Mth.clamp((dur - since) / 6F, 0, 1);
                MutableComponent name = Component.translatable("planet.orbitalstrike." + ClientFx.PLANET_IDS[i])
                        .withStyle(ClientFx.PLANET_COLOR[i], ChatFormatting.BOLD);
                big(g, font, name, w / 2, (int) (h * 0.22F), 3.0F * pop, 0xFFFFFF, al);
            }
        }
        if (t > tl.alignedAt && t < tl.alignedAt + 55) {
            float al = Mth.clamp(Math.min((t - tl.alignedAt) / 8F, (tl.alignedAt + 55 - t) / 10F), 0, 1);
            big(g, font, Component.translatable("cinematic.orbitalstrike.aligned").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    w / 2, (int) (h * 0.22F), 3.2F, 0xFFFFFF, al);
        }
        // प्रहार की चीख़
        if (t > tl.impact - 30 && t < tl.impact) {
            float shake = (t - (tl.impact - 30)) / 30F;
            int ox = (int) ((mc.level.random.nextFloat() - 0.5F) * 6 * shake), oy = (int) ((mc.level.random.nextFloat() - 0.5F) * 6 * shake);
            String key = tl.vajraStart >= 0 ? "cinematic.orbitalstrike.vajra" : "cinematic.orbitalstrike.strike";
            big(g, font, Component.translatable(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), w / 2 + ox, (int) (h * 0.4F) + oy,
                    3.5F + shake * 1.5F, 0xFFFFFF, 1);
        }

        // डायलॉग
        Line line = null;
        for (Line l : lines) if (t >= l.start && t < l.start + l.dur) line = l;
        if (line != null) dialogue(g, font, line, t, w, h, bar);
        else lastLine = null;

        // स्किप
        if (a < tl.impact - 40) {
            Component skip = Component.translatable("cinematic.orbitalstrike.skip");
            g.drawString(font, skip, w - font.width(skip) - 8, h - bar + (bar - 8) / 2, 0x99FFFFFF);
        }
        // शुरुआत/अंत में काला फ़ेड, प्रहार पर सफ़ेद फ़्लैश ClientEvents से
        float fade = Math.max(Mth.clamp(1 - t / 15F, 0, 1), Mth.clamp((t - (tl.end - 15)) / 15F, 0, 1));
        if (fade > 0) g.fill(0, 0, w, h, ((int) (fade * 255) << 24));
        ClientEvents.renderFlash(g, pt);
    }

    private void dialogue(GuiGraphics g, Font font, Line l, float t, int w, int h, int bar) {
        Minecraft mc = Minecraft.getInstance();
        int boxH = 52, x0 = 24, x1 = w - 24, y1 = h - bar - 6, y0 = y1 - boxH;
        g.fill(x0, y0, x1, y1, 0xC0000000);
        g.fill(x0, y0, x1, y0 + 1, 0xFF7FE7FF);
        int px = x0 + 6, py = y0 + 6, ps = 40;
        if (l.portrait == null) {
            if (mc.player != null) PlayerFaceRenderer.draw(g, mc.player.getSkinTextureLocation(), px, py, ps);
        } else {
            ResourceLocation tex = new ResourceLocation("orbitalstrike", "textures/gui/portrait_" + l.portrait + ".png");
            g.blit(tex, px, py, ps, ps, 0, 0, 32, 32, 32, 32);
        }
        Component name = Component.translatable("speaker.orbitalstrike." + l.speaker).withStyle(l.color, ChatFormatting.BOLD);
        g.drawString(font, name, px + ps + 8, py, 0xFFFFFF);
        String full = I18n.get("dialog.orbitalstrike." + l.key);
        int chars = Math.min(full.length(), (int) ((t - l.start) * 1.6F));
        if (l != lastLine) { lastLine = l; lastChars = -1; }
        if (chars > lastChars) {
            if (chars % 2 == 0 && chars < full.length()) {
                float pitch = switch (l.speaker) { case "chintu" -> 1.6F; case "indra", "voice" -> 0.6F; case "system" -> 1.3F; default -> 1.0F; };
                play(ModSounds.BLIP.get(), pitch, 0.5F);
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
        float slide = Mth.clamp((t - 588) / 10F, 0, 1);
        x += (int) ((1 - slide) * (kw + 40));
        g.blit(new ResourceLocation("orbitalstrike", "textures/gui/keypad.png"), x, y, 0, 0, kw, kh, kw, kh);
        int typed = t >= 650 ? 4 : t >= 635 ? 3 : t >= 620 ? 2 : t >= 605 ? 1 : 0;
        String code = "9274".substring(0, typed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(i < code.length() ? code.charAt(i) : '_').append(' ');
        if (t >= 670) {
            Component ok = Component.translatable("cinematic.orbitalstrike.access").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            g.drawCenteredString(font, ok, x + kw / 2, y + 21, 0x55FF55);
        } else {
            g.drawCenteredString(font, sb.toString().trim(), x + kw / 2, y + 21, 0x55FF88);
        }
        Component title = Component.translatable("cinematic.orbitalstrike.code").withStyle(ChatFormatting.AQUA);
        g.drawCenteredString(font, title, x + kw / 2, y - 12, 0x7FE7FF);
        String keys = "123456789*0#";
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 3; c++) {
                char k = keys.charAt(r * 3 + c);
                int kx = x + 14 + c * 34, ky = y + 48 + r * 27;
                boolean lit = typed > 0 && t < 670 && code.charAt(typed - 1) == k && (t - (590 + typed * 15)) < 8;
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
}
