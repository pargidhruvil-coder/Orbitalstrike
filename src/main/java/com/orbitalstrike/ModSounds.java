package com.orbitalstrike;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, OrbitalStrikeMod.MODID);

    public static final RegistryObject<SoundEvent> CHARGE = reg("charge");
    public static final RegistryObject<SoundEvent> READY = reg("ready");
    public static final RegistryObject<SoundEvent> FIRE = reg("fire");
    public static final RegistryObject<SoundEvent> LOCK = reg("lock");
    public static final RegistryObject<SoundEvent> ALIGN = reg("align");
    public static final RegistryObject<SoundEvent> BEAM = big("beam");
    public static final RegistryObject<SoundEvent> IMPACT = big("impact");
    public static final RegistryObject<SoundEvent> COMM_RING = reg("comm_ring");
    public static final RegistryObject<SoundEvent> ALARM = reg("alarm");
    public static final RegistryObject<SoundEvent> KEYPAD = reg("keypad");
    public static final RegistryObject<SoundEvent> ACCESS = reg("access");
    public static final RegistryObject<SoundEvent> VOICE = reg("voice");
    public static final RegistryObject<SoundEvent> BLIP = reg("blip");
    public static final RegistryObject<SoundEvent> VAJRA = big("vajra");
    public static final RegistryObject<SoundEvent> FIZZLE = reg("fizzle");
    public static final RegistryObject<SoundEvent> DIVINE = big("divine");
    public static final RegistryObject<SoundEvent> CHIRP = reg("chirp");
    public static final RegistryObject<SoundEvent> DOOR = reg("door");
    public static final RegistryObject<SoundEvent> BUTTON = reg("button");
    public static final RegistryObject<SoundEvent> SHIP_CHARGE = big("ship_charge");
    public static final RegistryObject<SoundEvent> SHIP_HUM = reg("ship_hum");
    public static final RegistryObject<SoundEvent> WHOOSH = reg("whoosh");

    private static RegistryObject<SoundEvent> reg(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(OrbitalStrikeMod.MODID, n)));
    }

    private static RegistryObject<SoundEvent> big(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(OrbitalStrikeMod.MODID, n), 300.0F));
    }
}
