package com.orbitalstrike.client;

import com.orbitalstrike.OrbitalCannonItem;
import com.orbitalstrike.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shift + राइट-क्लिक: सिनेमैटिक की लंबाई चुनने का मेनू */
public class CannonModeScreen extends Screen {
    private int mode;

    public CannonModeScreen(int mode) {
        super(Component.translatable("gui.orbitalstrike.title"));
        this.mode = mode;
    }

    public static void open(int mode) {
        Minecraft.getInstance().setScreen(new CannonModeScreen(mode));
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 52;
        for (int m = 0; m < 3; m++) {
            final int sel = m;
            Component label = Component.translatable("gui.orbitalstrike.mode_" + m)
                    .withStyle(m == mode ? ChatFormatting.GOLD : ChatFormatting.WHITE, m == mode ? ChatFormatting.BOLD : ChatFormatting.RESET);
            addRenderableWidget(Button.builder(label, b -> select(sel)).bounds(cx - 120, y + m * 36, 240, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.orbitalstrike.done"), b -> onClose())
                .bounds(cx - 50, y + 3 * 36 + 8, 100, 20).build());
    }

    private void select(int m) {
        mode = m;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.SetModePacket(m));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (ItemStack s : new ItemStack[]{mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
                if (s.getItem() instanceof OrbitalCannonItem) OrbitalCannonItem.setMode(s, m);
            }
        }
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - 52;
        g.fill(cx - 140, y - 46, cx + 140, y + 3 * 36 + 36, 0xD0080A18);
        g.fill(cx - 140, y - 46, cx + 140, y - 45, 0xFFFFC040);
        g.pose().pushPose();
        g.pose().translate(cx, y - 36, 0);
        g.pose().scale(1.6F, 1.6F, 1);
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 0, 0, 0xFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("gui.orbitalstrike.subtitle"), cx, y - 16, 0xAAAAAA);
        super.render(g, mouseX, mouseY, pt);
        for (int m = 0; m < 3; m++) {
            g.drawCenteredString(font, Component.translatable("gui.orbitalstrike.mode_" + m + "_desc"), cx, y + m * 36 + 23,
                    m == mode ? 0xFFD27F : 0x8A8A8A);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
