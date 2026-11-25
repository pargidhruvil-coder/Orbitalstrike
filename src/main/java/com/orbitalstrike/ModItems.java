package com.orbitalstrike;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OrbitalStrikeMod.MODID);

    public static final RegistryObject<Item> ORBITAL_CANNON = ITEMS.register("orbital_cannon",
            () -> new OrbitalCannonItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
}
