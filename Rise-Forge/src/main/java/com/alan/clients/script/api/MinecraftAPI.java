package com.alan.clients.script.api;

import net.minecraft.client.Minecraft;

public class MinecraftAPI extends API {

    public int getDisplayWidth() {
        return MC.displayWidth;
    }

    public int getDisplayHeight() {
        return MC.displayHeight;
    }

    public float getTimerSpeed() {
        return MC.timer.timerSpeed;
    }

    public void setTimerSpeed(final float timerSpeed) {
        MC.timer.timerSpeed = timerSpeed;
    }

    public float getPartialTicks() {
        return MC.timer.elapsedPartialTicks;
    }

    public float getRenderPartialTicks() {
        return MC.timer.renderPartialTicks;
    }

    public int getFPS() {
        return Minecraft.getDebugFPS();
    }
}
