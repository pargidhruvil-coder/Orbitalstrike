package com.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class GalaxyEffects extends DimensionSpecialEffects {
    private final GalaxySkyRenderer.Kind kind;

    public GalaxyEffects(GalaxySkyRenderer.Kind kind) {
        super(Float.NaN, false, SkyType.NONE, false, false);
        this.kind = kind;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return switch (kind) {
            case SPACE -> new Vec3(0.0, 0.0, 0.02);
            case MOON -> new Vec3(0.02, 0.02, 0.03);
            case MARS -> new Vec3(0.22, 0.07, 0.03);
        };
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        GalaxySkyRenderer.render(kind, level, partialTick, poseStack);
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, net.minecraft.client.renderer.LightTexture lightTexture,
                                     double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }
}
