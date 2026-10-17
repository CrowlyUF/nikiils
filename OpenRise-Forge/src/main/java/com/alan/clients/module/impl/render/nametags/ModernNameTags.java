package com.alan.clients.module.impl.render.nametags;

import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.component.impl.player.TargetComponent;
import com.alan.clients.component.impl.render.ProjectionComponent;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.module.impl.render.NameTags;
import com.alan.clients.util.font.Font;
import com.alan.clients.util.font.impl.minecraft.MinecraftFont;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.EntityColors;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.value.Mode;
import com.alan.clients.value.impl.BooleanValue;
import com.alan.clients.value.impl.ColorValue;
import com.alan.clients.value.impl.ModeValue;
import net.minecraft.entity.EntityLivingBase;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.util.List;

import static com.alan.clients.layer.Layers.*;

public class ModernNameTags extends Mode<NameTags> {
    public ModernNameTags(String name, NameTags parent) {
        super(name, parent);
    }

    private final BooleanValue health = new BooleanValue("Show Health", this, true);
    private final BooleanValue healthBar = new BooleanValue("Health Bar", this, true);
    private final BooleanValue distance = new BooleanValue("Distance", this, false);
    private final BooleanValue heldItem = new BooleanValue("Held Item", this, false);

    private final ModeValue colorMode = EntityColors.colorMode(new ModeValue("Color Mode", this));
    private final ColorValue customColor = new ColorValue("Custom Color", this, new Color(120, 90, 255),
            () -> !colorMode.getValue().getName().equals(EntityColors.CUSTOM));

    private final Font nunitoLight14 = Fonts.MAIN.get(14, Weight.LIGHT);

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        List<EntityLivingBase> entities = TargetComponent.getTargets(getParent().player.getValue(), getParent().invisibles.getValue(), getParent().animals.getValue(), getParent().mobs.getValue(), getParent().teams.getValue(), true);

        if (mc.gameSettings.thirdPersonView != 0) {
            entities.add(mc.thePlayer);
        }

        for (EntityLivingBase entity : entities) {
            if (!RenderUtil.isInViewFrustrum(entity)) {
                continue;
            }

            MCHooks.hideNameTag(entity);

            Vector4d position = ProjectionComponent.get(entity);

            if (position == null) {
                continue;
            }

            final Font nameFont = Fonts.MAIN.get(17, Weight.LIGHT);
            String name = entity.getName();
            if (distance.getValue() && entity != mc.thePlayer) {
                name += " " + (int) mc.thePlayer.getDistanceToEntity(entity) + "m";
            }
            final String text = name;
            // Distance changes the string every frame, so don't pollute the parent width cache with it.
            final double nameWidth = distance.getValue() ? nameFont.width(text) : getParent().getWidth(text, nameFont);

            final double posX = (position.x + (position.z - position.x) / 2);
            final double posY = position.y - 2;
            final double margin = 2;

            final int multiplier = 2;
            final double nH = nameFont.height() - 2 + (this.health.getValue() ? nunitoLight14.height() : 0) + margin * multiplier;
            final double nY = posY - nH + 1;

            // Shared colour-mode accent for the name text + health bar.
            final Color accent = EntityColors.resolve(colorMode.getValue().getName(), customColor.getValue(),
                    getTheme(), entity, nY, entity.getEntityId() * 120, 50);
            final float healthPct = Math.max(0f, Math.min(1f, entity.getHealth() / entity.getMaxHealth()));

            getLayer(BLOOM).add(() -> {
                RenderUtil.roundedRectangle((posX - margin - nameWidth / 2) + 0.5F, nY + 0.5F, (nameWidth + margin * multiplier) - 1, nH - 1, getTheme().getRound() , Color.BLACK);
            });

            getLayer(REGULAR).add(() -> {
                RenderUtil.roundedRectangle(posX - margin - nameWidth / 2, nY, nameWidth + margin * multiplier, nH, getTheme().getRound()-1, getTheme().getBackgroundShade());
                nameFont.drawCentered(text, posX - 0.5f, nY - 0.5f + margin * 2, accent.getRGB());

                if (this.health.getValue()) {
                    nunitoLight14.drawCentered(String.valueOf((int) entity.getHealth()), posX, posY + 5 - margin - MinecraftFont.FONT_HEIGHT, Color.WHITE.hashCode());
                }

                if (this.healthBar.getValue()) {
                    double barW = nameWidth + margin * multiplier - 2;
                    double barX = posX - margin - nameWidth / 2 + 1;
                    double barY = nY + nH - 1.5;
                    RenderUtil.roundedRectangle(barX, barY, barW, 1.5, 0.75, ColorUtil.withAlpha(Color.BLACK, 150));
                    Color healthColor = new Color(Color.HSBtoRGB(healthPct / 3.0f, 0.85f, 1.0f));
                    RenderUtil.roundedRectangle(barX, barY, Math.max(0.75, barW * healthPct), 1.5, 0.75, healthColor);
                }

                if (this.heldItem.getValue() && entity.getHeldItem() != null) {
                    RenderUtil.renderItemIcon(posX + nameWidth / 2 + margin + 2, nY + nH / 2 - 8, 0, entity.getHeldItem());
                }
            });

            getLayer(BLUR).add(() -> {
                RenderUtil.roundedRectangle(posX - margin - nameWidth / 2, nY, nameWidth + margin * multiplier, nH, getTheme().getRound(), Color.BLACK);
            });
        }
    };
}
