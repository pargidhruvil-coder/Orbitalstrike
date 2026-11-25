package com.orbitalstrike;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(OrbitalStrikeMod.MODID)
public class OrbitalStrikeMod {
    public static final String MODID = "orbitalstrike";

    public OrbitalStrikeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::addToCreativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // StrikeManager खुद @Mod.EventBusSubscriber से रजिस्टर होता है
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.ORBITAL_CANNON.get());
        }
    }
}
