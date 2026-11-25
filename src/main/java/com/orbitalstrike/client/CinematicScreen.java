package com.orbitalstrike.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** सिनेमैटिक के दौरान खुली रहती है: इनपुट रोकती है, GUI बनाती है, Space/Esc = स्किप */
public class CinematicScreen extends Screen {
    public CinematicScreen() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CinematicDirector.renderGui(g, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            CinematicDirector.requestSkip();
            return true;
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
