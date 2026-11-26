package com.orbitalstrike;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue POWER;
    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
    public static final ForgeConfigSpec.IntValue CHARGE_SECONDS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue RANGE;
    public static final ForgeConfigSpec.IntValue CRATER_RADIUS;
    public static final ForgeConfigSpec.DoubleValue IMPACT_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue PROTECT_OWNER;
    public static final ForgeConfigSpec.BooleanValue OVERWORLD_GALAXY;
    public static final ForgeConfigSpec.BooleanValue CINEMATIC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("orbital_strike");
        POWER = b.comment("Main blast power (TNT = 4). Default 14")
                .defineInRange("power", 14.0D, 1.0D, 40.0D);
        BREAK_BLOCKS = b.comment("Should the strike destroy blocks and start fires?")
                .define("breakBlocks", true);
        CHARGE_SECONDS = b.comment("Seconds of right-click charging needed before the gun can fire")
                .defineInRange("chargeSeconds", 2, 1, 10);
        CRATER_RADIUS = b.comment("Crater radius in blocks (bowl shape). The shockwave flattens trees up to 2x this. Big values can lag.")
                .defineInRange("craterRadius", 48, 4, 96);
        IMPACT_DAMAGE = b.comment("Damage at the centre of the impact (falls off with distance)")
                .defineInRange("impactDamage", 400.0D, 0.0D, 5000.0D);
        PROTECT_OWNER = b.comment("The shooter is immune to their own strike and protected during the cinematic")
                .define("protectOwner", true);
        COOLDOWN_SECONDS = b.comment("Cooldown of the gun in seconds")
                .defineInRange("cooldownSeconds", 20, 0, 600);
        RANGE = b.comment("Max targeting distance in blocks")
                .defineInRange("range", 250, 16, 600);
        b.pop();
        b.push("galaxy");
        OVERWORLD_GALAXY = b.comment("Show the milky way in the normal overworld night sky")
                .define("overworldGalaxy", true);
        CINEMATIC = b.comment("Cinematics: rocket launch/landing camera and the Navagraha Cannon story cinematic")
                .define("cinematic", true);
        b.pop();
        SPEC = b.build();
    }
}
