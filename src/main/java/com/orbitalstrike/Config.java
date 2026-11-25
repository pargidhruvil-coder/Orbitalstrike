package com.orbitalstrike;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue POWER;
    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
    public static final ForgeConfigSpec.IntValue CHARGE_SECONDS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue RANGE;
    public static final ForgeConfigSpec.BooleanValue OVERWORLD_GALAXY;
    public static final ForgeConfigSpec.BooleanValue CINEMATIC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("orbital_strike");
        POWER = b.comment("Main blast power (TNT = 4). Default 14")
                .defineInRange("power", 14.0D, 1.0D, 40.0D);
        BREAK_BLOCKS = b.comment("Should the strike destroy blocks and start fires?")
                .define("breakBlocks", true);
        CHARGE_SECONDS = b.comment("Seconds between the gun shot and the impact from orbit")
                .defineInRange("chargeSeconds", 3, 1, 15);
        COOLDOWN_SECONDS = b.comment("Cooldown of the gun in seconds")
                .defineInRange("cooldownSeconds", 10, 0, 600);
        RANGE = b.comment("Max targeting distance in blocks")
                .defineInRange("range", 300, 16, 1000);
        b.pop();
        b.push("galaxy");
        OVERWORLD_GALAXY = b.comment("Show the milky way in the normal overworld night sky")
                .define("overworldGalaxy", true);
        CINEMATIC = b.comment("Cinematic camera (third person, shake, black bars) during rocket launch and landing")
                .define("cinematic", true);
        b.pop();
        SPEC = b.build();
    }
}
