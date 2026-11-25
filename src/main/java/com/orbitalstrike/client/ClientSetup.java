package com.orbitalstrike.client;

import com.orbitalstrike.ModEntities;
import com.orbitalstrike.OrbitalStrikeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OrbitalStrikeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation(OrbitalStrikeMod.MODID, "space"), new GalaxyEffects(GalaxySkyRenderer.Kind.SPACE));
        event.register(new ResourceLocation(OrbitalStrikeMod.MODID, "moon"), new GalaxyEffects(GalaxySkyRenderer.Kind.MOON));
        event.register(new ResourceLocation(OrbitalStrikeMod.MODID, "mars"), new GalaxyEffects(GalaxySkyRenderer.Kind.MARS));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ROCKET.get(), RocketRenderer::new);
    }
}
