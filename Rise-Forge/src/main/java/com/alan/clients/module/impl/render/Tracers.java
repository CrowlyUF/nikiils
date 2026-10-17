package com.alan.clients.module.impl.render;

import com.alan.clients.Client;
import com.alan.clients.component.impl.player.TargetComponent;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.render.Render3DEvent;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.EntityColors;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.value.impl.BooleanValue;
import com.alan.clients.value.impl.ColorValue;
import com.alan.clients.value.impl.ModeValue;
import com.alan.clients.value.impl.NumberValue;
import com.alan.clients.value.impl.SubMode;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.awt.*;

@ModuleInfo(aliases = {"module.render.tracers.name"}, description = "module.render.tracers.description", category = Category.RENDER)
public final class Tracers extends Module {

    private final ModeValue colorMode = EntityColors.colorMode(new ModeValue("Color Mode", this)).setDefault(EntityColors.DISTANCE);
    private final ColorValue customColor = new ColorValue("Custom Color", this, new Color(120, 90, 255),
            () -> !colorMode.getValue().getName().equals(EntityColors.CUSTOM));
    private final NumberValue distanceRange = new NumberValue("Distance Range", this, 50, 5, 256, 1,
            () -> !colorMode.getValue().getName().equals(EntityColors.DISTANCE));

    private final NumberValue lineWidth = new NumberValue("Line Width", this, 1.5, 0.5, 5, 0.1);
    private final NumberValue alpha = new NumberValue("Alpha", this, 128, 0, 255, 1);

    private final ModeValue start = new ModeValue("Target Point", this)
            .add(new SubMode("Eyes"))
            .add(new SubMode("Center"))
            .add(new SubMode("Feet")).setDefault("Eyes");

    private final BooleanValue players = new BooleanValue("Players", this, true);
    private final BooleanValue invisibles = new BooleanValue("Invisibles", this, true);
    private final BooleanValue animals = new BooleanValue("Animals", this, false);
    private final BooleanValue mobs = new BooleanValue("Mobs", this, false);
    private final BooleanValue teams = new BooleanValue("Teammates", this, false);

    @EventLink
    public final Listener<Render3DEvent> onRender3D = event -> {
        if (mc.gameSettings.hideGUI) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        mc.entityRenderer.orientCamera(mc.timer.renderPartialTicks);

        final String cm = colorMode.getValue().getName();
        final double range = distanceRange.getValue().doubleValue();
        final int a = alpha.getValue().intValue();
        final float width = lineWidth.getValue().floatValue();
        final String point = start.getValue().getName();

        for (final EntityLivingBase player : TargetComponent.getTargets(players.getValue(), invisibles.getValue(), animals.getValue(), mobs.getValue(), teams.getValue())) {
            if (player == mc.thePlayer || player.isDead || Client.INSTANCE.getBotManager().contains((Entity) player)) {
                continue;
            }

            final double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
            final double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

            switch (point) {
                case "Center":
                    y += player.height / 2f;
                    break;
                case "Feet":
                    break;
                case "Eyes":
                default:
                    y += player.getEyeHeight();
                    break;
            }

            final Color color = ColorUtil.withAlpha(
                    EntityColors.resolve(cm, customColor.getValue(), getTheme(), player, 0, player.getEntityId() * 120, range), a);

            RenderUtil.drawLine(mc.getRenderManager().renderPosX, mc.getRenderManager().renderPosY + mc.thePlayer.getEyeHeight(), mc.getRenderManager().renderPosZ, x, y, z, color, width);
        }

        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    };
}
