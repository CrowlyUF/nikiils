package com.alan.clients.forge;

import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.event.impl.render.Render3DEvent;
import com.alan.clients.event.impl.render.RenderGUIEvent;
import com.alan.clients.event.impl.render.RenderHungerEvent;
import com.alan.clients.module.impl.render.FreeLook;
import com.alan.clients.module.impl.render.ScoreBoard;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.util.mc.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bridges Forge's render events to Rise's events. Rise drew its HUD from a patched GuiIngame; here the Forge HUD stays
 * and only the parts Rise replaces with its own modules are hidden.
 */
public final class ForgeEventHandler {

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onOverlayPre(final RenderGameOverlayEvent.Pre event) {
        switch (event.type) {
            case HOTBAR:       // HotBar module (always on)
            case CHAT:         // Chat module (always on)
            case EXPERIENCE:   // Rise never drew the XP bar
            case BOSSHEALTH:
            case HELMET:       // pumpkin overlay
            case PORTAL:
                event.setCanceled(true);
                break;
            case ALL:
                GuiIngameForge.renderObjective = !Modules.isEnabled(ScoreBoard.class);
                break;
            default:
                break;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOverlayPost(final RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.FOOD) {
            Events.post(new RenderHungerEvent(event.resolution));
        } else if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            MCHooks.guiFrame++;

            GlStateManager.pushMatrix();
            Events.post(new Render2DEvent(MCHooks.scaledResolution(), event.partialTicks));
            GlStateManager.popMatrix();

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
        }
    }

    @SubscribeEvent
    public void onScreenDrawn(final GuiScreenEvent.DrawScreenEvent.Post event) {
        Events.post(new RenderGUIEvent());
    }

    @SubscribeEvent
    public void onWorldLast(final RenderWorldLastEvent event) {
        Events.post(new Render3DEvent(event.partialTicks));
    }

    /** FreeLook orbits the camera without turning the player. */
    @SubscribeEvent
    public void onCameraSetup(final EntityViewRenderEvent.CameraSetup event) {
        final FreeLook freeLook = Modules.get(FreeLook.class);

        if (freeLook != null && freeLook.isEnabled() && event.entity == mc.thePlayer) {
            event.pitch = freeLook.lastPitch * (freeLook.invertPitch.getValue() ? -1 : 1);
            event.yaw = freeLook.lastYaw + 180.0F;
        }
    }
}
