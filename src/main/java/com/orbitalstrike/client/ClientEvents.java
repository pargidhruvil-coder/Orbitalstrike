package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalstrike.Config;
import com.orbitalstrike.OrbitalCannonItem;
import com.orbitalstrike.OrbitalStrikeMod;
import com.orbitalstrike.RocketEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * सिनेमैटिक: लॉन्च पर थर्ड-पर्सन कैमरा, स्क्रीन शेक, फ़िल्मी काली पट्टियाँ, FOV, पहुँचने पर सफ़ेद फ़्लैश।
 * और पृथ्वी के रात वाले आसमान में मिल्की-वे।
 */
@Mod.EventBusSubscriber(modid = OrbitalStrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    private static float shake = 0, bars = 0, prevBars = 0, flash = 0, prevFlash = 0;
    private static int arrivalTicks = 0;
    private static CameraType savedCamera = null;
    private static ResourceKey<Level> lastDim = null;

    public static void addShake(float amount) {
        shake = Math.min(3.0F, shake + amount);
    }

    private static float recoil = 0, prevRecoil = 0;

    /** फ़ायर: रिकॉइल + शेक */
    public static void kick() {
        recoil = 1.0F;
        prevRecoil = 1.0F;
        addShake(1.2F);
    }

    public static float recoil(float pt) {
        return Mth.lerp(pt, prevRecoil, recoil);
    }

    public static void flash(float amount) {
        flash = Math.max(flash, amount);
        prevFlash = flash;
    }

    public static void renderFlash(GuiGraphics g, float pt) {
        float f = Mth.lerp(pt, prevFlash, flash);
        if (f > 0.01F) {
            int a = (int) (Mth.clamp(f, 0, 1) * 255);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xFFFFFF);
        }
    }

    private static final ResourceLocation SCOPE = new ResourceLocation(OrbitalStrikeMod.MODID, "textures/gui/scope.png");

    /** गन चार्ज करते समय स्कोप, रेंज, लॉक और चार्ज बार */
    private static void renderScope(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !p.isUsingItem() || !(p.getUseItem().getItem() instanceof OrbitalCannonItem) || CinematicDirector.isActive()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float c = OrbitalCannonItem.chargeProgress(p);
        int size = (int) (Math.min(w, h) * (1.25F - 0.15F * c));
        int x = (w - size) / 2, y = (h - size) / 2;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(SCOPE, x, y, 0, 0, size, size, size, size);
        if (x > 0) { g.fill(0, 0, x, h, 0xFF000000); g.fill(x + size, 0, w, h, 0xFF000000); }
        if (y > 0) { g.fill(0, 0, w, y, 0xFF000000); g.fill(0, y + size, w, h, 0xFF000000); }
        net.minecraft.world.phys.HitResult hit = p.pick(Config.RANGE.get(), pt, false);
        boolean locked = hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
        int dist = locked ? (int) Math.round(hit.getLocation().distanceTo(p.getEyePosition(pt))) : 0;
        net.minecraft.network.chat.Component range = net.minecraft.network.chat.Component.translatable("hud.orbitalstrike.range", locked ? dist : "---");
        g.drawCenteredString(mc.font, range, w / 2, h / 2 + 24, 0x7FE7FF);
        if (locked) {
            boolean blink = (p.tickCount / 4) % 2 == 0 || c >= 1;
            if (blink) g.drawCenteredString(mc.font, net.minecraft.network.chat.Component.translatable("hud.orbitalstrike.lock"), w / 2, h / 2 - 32, c >= 1 ? 0xFF4040 : 0xFFD040);
        }
        int bw = 120, bx = w / 2 - bw / 2, by = h / 2 + 38;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xFF7FE7FF);
        g.fill(bx, by, bx + bw, by + 4, 0xFF101820);
        g.fill(bx, by, bx + (int) (bw * c), by + 4, c >= 1 ? 0xFFFFC040 : 0xFFB060FF);
        if (c >= 1) g.drawCenteredString(mc.font, net.minecraft.network.chat.Component.translatable("orbitalstrike.msg.ready"), w / 2, by + 9, 0xFFD040);
    }

    private static boolean cinematicOn() {
        return Config.CINEMATIC.get();
    }

    private static RocketEntity launchingRocket(LocalPlayer p) {
        if (p != null && p.getVehicle() instanceof RocketEntity r && r.getLaunchTicks() >= 0) return r;
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        prevBars = bars;
        prevFlash = flash;
        prevRecoil = recoil;
        recoil *= 0.7F;
        if (recoil < 0.01F) recoil = 0;
        if (p == null) { lastDim = null; return; }

        RocketEntity rocket = launchingRocket(p);
        boolean cine = cinematicOn();

        // कैमरा: लॉन्च के दौरान थर्ड-पर्सन
        if (rocket != null && cine && savedCamera == null) {
            savedCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (rocket == null && savedCamera != null && arrivalTicks <= 0) {
            mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }

        // शेक
        if (rocket != null) {
            int t = rocket.getLaunchTicks();
            float target = t < RocketEntity.COUNTDOWN ? (t / (float) RocketEntity.COUNTDOWN) * 0.35F : 1.4F;
            shake = Math.max(shake, target);
        }

        // नई दुनिया में पहुँचे -> फ़्लैश + पट्टियाँ
        ResourceKey<Level> dim = p.level().dimension();
        if (lastDim != null && dim != lastDim) {
            flash = 1.0F;
            arrivalTicks = 110;
            addShake(1.5F);
        }
        lastDim = dim;
        if (arrivalTicks > 0) arrivalTicks--;
        if (arrivalTicks == 1 && savedCamera != null && rocket == null) {
            mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }

        float barTarget = cine && (rocket != null || arrivalTicks > 0) ? 1F : 0F;
        bars += (barTarget - bars) * 0.12F;
        flash *= 0.92F;
        shake *= 0.9F;
        if (shake < 0.01F) shake = 0;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0 || !cinematicOn()) return;
        double time = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + event.getPartialTick();
        float s = shake;
        event.setPitch(event.getPitch() + (float) (Math.sin(time * 1.9) * 0.9 + Math.sin(time * 3.7) * 0.5) * s);
        event.setYaw(event.getYaw() + (float) (Math.cos(time * 2.3) * 0.8 + Math.sin(time * 4.1) * 0.4) * s);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 1.3) * 1.2F * s);
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        // गन चार्ज करते समय ज़ूम
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof OrbitalCannonItem) {
            float c = OrbitalCannonItem.chargeProgress(p);
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F - 0.55F * c));
        }
        RocketEntity r = launchingRocket(p);
        if (r != null && r.getLaunchTicks() >= RocketEntity.COUNTDOWN && cinematicOn()) {
            event.setNewFovModifier(event.getNewFovModifier() * 1.2F);
        }
    }

    /** ऑर्बिटल स्ट्राइक का धमाका पास में हो तो स्क्रीन हिले */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null) return;
        ResourceLocation id = s.getLocation();
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        double dist = Math.sqrt(p.distanceToSqr(s.getX(), s.getY(), s.getZ()));
        if (id.equals(SoundEvents.WARDEN_SONIC_BOOM.getLocation()) && dist < 160) {
            addShake((float) (2.6 * (1.0 - dist / 160.0)));
            if (dist < 60) flash = Math.max(flash, (float) (0.8 * (1.0 - dist / 60.0)));
        } else if (id.equals(SoundEvents.GENERIC_EXPLODE.getLocation()) && dist < 40) {
            addShake((float) (0.5 * (1.0 - dist / 40.0)));
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (CinematicDirector.isActive()) {
            event.setCanceled(true);
            return;
        }
        Minecraft mcx = Minecraft.getInstance();
        if (mcx.player != null && mcx.player.isUsingItem() && mcx.player.getUseItem().getItem() instanceof OrbitalCannonItem
                && event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            event.setCanceled(true);
            return;
        }
        if (bars < 0.5F) return;
        ResourceLocation id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                || id.equals(VanillaGuiOverlay.JUMP_BAR.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        float pt = event.getPartialTick();
        if (CinematicDirector.isActive()) return;
        renderScope(event.getGuiGraphics(), pt);
        float b = Mth.lerp(pt, prevBars, bars);
        float f = Mth.lerp(pt, prevFlash, flash);
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (b > 0.01F) {
            int bh = (int) (h * 0.11F * b);
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        if (f > 0.01F) {
            int a = (int) (Mth.clamp(f, 0, 1) * 255);
            g.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }
    }

    /** पृथ्वी की रात में मिल्की-वे */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD || !Config.OVERWORLD_GALAXY.get()) return;
        float pt = event.getPartialTick();
        float alpha = level.getStarBrightness(pt) * (1.0F - level.getRainLevel(pt)) * 1.8F;
        if (alpha <= 0.02F) return;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360.0F));
        GalaxySkyRenderer.renderSkybox(ps, Math.min(alpha, 0.9F), true);
        ps.popPose();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
    }
}
