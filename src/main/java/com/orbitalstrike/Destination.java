package com.orbitalstrike;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum Destination {
    SPACE("space", ChatFormatting.LIGHT_PURPLE),
    MOON("moon", ChatFormatting.GRAY),
    MARS("mars", ChatFormatting.GOLD),
    EARTH("earth", ChatFormatting.AQUA),
    EARTH_RANDOM("earth_random", ChatFormatting.GREEN);

    public final String id;
    public final ChatFormatting color;

    Destination(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public Component displayName() {
        return Component.translatable("orbitalstrike.dest." + id).withStyle(color, ChatFormatting.BOLD);
    }

    public Destination next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Destination byIndex(int i) {
        Destination[] v = values();
        return (i >= 0 && i < v.length) ? v[i] : SPACE;
    }
}
