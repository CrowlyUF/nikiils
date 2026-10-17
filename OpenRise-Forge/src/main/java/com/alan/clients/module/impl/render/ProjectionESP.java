package com.alan.clients.module.impl.render;

import com.alan.clients.component.impl.player.TargetComponent;
import com.alan.clients.component.impl.render.ProjectionComponent;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.util.font.Font;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.EntityColors;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.value.impl.BooleanValue;
import com.alan.clients.value.impl.ColorValue;
import com.alan.clients.value.impl.ModeValue;
import com.alan.clients.value.impl.NumberValue;
import com.alan.clients.value.impl.SubMode;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.util.List;

import static com.alan.clients.util.render.RenderUtil.*;

@ModuleInfo(aliases = {"module.render.2desp.name"}, description = "module.render.projectionesp.description", category = Category.RENDER)
public class ProjectionESP extends Module {

    private final ModeValue box = new ModeValue("Box", this)
            .add(new SubMode("Normal"))
            .add(new SubMode("Rounded"))
            .add(new SubMode("Outline"))
            .add(new SubMode("Filled"))
            .add(new SubMode("CS"))
            .add(new SubMode("Corner"))
            .add(new SubMode("None")).setDefault("Normal");

    private final ModeValue colorMode = EntityColors.colorMode(new ModeValue("Color Mode", this));
    private final ColorValue customColor = new ColorValue("Custom Color", this, new Color(120, 90, 255),
            () -> !colorMode.getValue().getName().equals(EntityColors.CUSTOM));
    private final NumberValue distanceRange = new NumberValue("Distance Range", this, 50, 5, 256, 1,
            () -> !colorMode.getValue().getName().equals(EntityColors.DISTANCE));

    private final ModeValue healthBar = new ModeValue("Health Bar Mode", this)
            .add(new SubMode("Health"))
            .add(new SubMode("Standard"))
            .add(new SubMode("Gradient"))
            .add(new SubMode("None")).setDefault("Health");
    private final BooleanValue healthText = new BooleanValue("Health Text", this, false,
            () -> healthBar.getValue().getName().equals("None"));

    private final BooleanValue armorBar = new BooleanValue("Armor Bar", this, false);
    private final BooleanValue nameTag = new BooleanValue("Name Tag", this, false);
    private final BooleanValue distanceTag = new BooleanValue("Distance Tag", this, false);
    private final BooleanValue glow = new BooleanValue("Glow", this, false);
    private final BooleanValue animated = new BooleanValue("Animated Pulse", this, true);

    private final BooleanValue showTargets = new BooleanValue("Targets", this, false);
    private final BooleanValue player = new BooleanValue("Player", this, true, () -> !showTargets.getValue());
    private final BooleanValue invisibles = new BooleanValue("Invisibles", this, false, () -> !showTargets.getValue());
    private final BooleanValue animals = new BooleanValue("Animals", this, false, () -> !showTargets.getValue());
    private final BooleanValue mobs = new BooleanValue("Mobs", this, false, () -> !showTargets.getValue());
    private final BooleanValue teams = new BooleanValue("Player Teammates", this, true, () -> !showTargets.getValue());

    private final Font tagFont = Fonts.MAIN.get(16, Weight.REGULAR);
    private final Font healthFont = Fonts.MAIN.get(14, Weight.MEDIUM);

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        List<EntityLivingBase> entities = TargetComponent.getTargets(this.player.getValue(), this.invisibles.getValue(), this.animals.getValue(), this.mobs.getValue(), this.teams.getValue(), true);

        if (mc.gameSettings.thirdPersonView != 0) {
            entities.add(mc.thePlayer);
        }

        double pulse = animated.getValue() ? (Math.sin(System.currentTimeMillis() / 800.0) * 0.15 + 0.85) : 1.0;

        final String cm = colorMode.getValue().getName();
        final double range = distanceRange.getValue().doubleValue();
        final Color custom = customColor.getValue();

        for (EntityLivingBase entity : entities) {
            if (mc.getRenderManager() == null || !isInViewFrustrum(entity) || entity.isDead || entity.isInvisible()) {
                continue;
            }

            Vector4d pos = ProjectionComponent.get(entity);

            if (pos == null) {
                continue;
            }

            double x = pos.x;
            double y = pos.y;
            double x2 = pos.z;
            double y2 = pos.w;
            double width = x2 - x;
            double height = y2 - y;

            if (width < 1 || height < 1) continue;

            // Resolve the accent colours through the shared colour-mode system. Two screen-space Y
            // values give a vertical gradient for the Theme mode while collapsing to a solid colour
            // for the dynamic modes (Custom/Health/Rainbow/Distance).
            final int alpha = (int) (255 * pulse);
            final int rainbowDelay = entity.getEntityId() * 120;
            final Color firstColor = ColorUtil.withAlpha(EntityColors.resolve(cm, custom, getTheme(), entity, y, rainbowDelay, range), alpha);
            final Color secondColor = ColorUtil.withAlpha(EntityColors.resolve(cm, custom, getTheme(), entity, y2, rainbowDelay, range), alpha);
            final Color outlineColor = Color.BLACK;

            if (glow.getValue()) {
                final double gx = x, gy = y, gw = width, gh = height;
                getLayer(com.alan.clients.layer.Layers.BLOOM).add(() ->
                        RenderUtil.roundedRectangle(gx - 1, gy - 1, gw + 2, gh + 2, 3, ColorUtil.withAlpha(firstColor, (int) (150 * pulse))));
            }

            switch (box.getValue().getName()) {
                case "Normal":
                    rectangle(x - 0.5f, y + 1f, 1.5f, height - 1.5f, outlineColor);
                    rectangle(x - 0.5f, y - 0.5f, width + 1.5f, 1.5f, outlineColor);
                    rectangle(x2 - 0.5f, y + 1, 1.5f, height, outlineColor);
                    rectangle(x - 0.5f, y2 - 0.5f, width, 1.5f, outlineColor);

                    horizontalGradient(x, y + 0.5f, 0.5f, height - 0.5f, firstColor, secondColor);
                    horizontalGradient(x, y, width, 0.5f, firstColor, secondColor);
                    horizontalGradient(x2, y, 0.5f, height, firstColor, secondColor);
                    horizontalGradient(x, y2, width + 0.5f, 0.5f, firstColor, secondColor);
                    break;
                case "Rounded": {
                    double radius = Math.max(1.5, Math.min(width, height) * 0.16);
                    // Black backdrop outline then the gradient outline on top -> a true hollow box.
                    RenderUtil.roundedOutlineRectangle(x - 1, y - 1, width + 2, height + 2, radius + 1, 1.0, outlineColor);
                    RenderUtil.roundedOutlineGradientRectangle(x, y, width, height, radius, 1.0, firstColor, secondColor);
                    break;
                }
                case "Outline": {
                    RenderUtil.roundedOutlineRectangle(x - 0.75, y - 0.75, width + 1.5, height + 1.5, 3.5, 1.0, outlineColor);
                    RenderUtil.roundedOutlineGradientRectangle(x, y, width, height, 2.5, 1.5, firstColor, secondColor);
                    break;
                }
                case "Filled": {
                    RenderUtil.drawRoundedGradientRect(x, y, width, height, 3,
                            ColorUtil.withAlpha(firstColor, (int) (55 * pulse)),
                            ColorUtil.withAlpha(secondColor, (int) (55 * pulse)), true);
                    RenderUtil.roundedOutlineRectangle(x - 0.75, y - 0.75, width + 1.5, height + 1.5, 3.5, 1.0, outlineColor);
                    RenderUtil.roundedOutlineGradientRectangle(x, y, width, height, 2.5, 1.0, firstColor, secondColor);
                    break;
                }
                case "CS": {
                    float adjustedWidth = (float) (width / 4);
                    float adjustedHeight = (float) (height / 4);
                    float lineWidth = 1.5f;

                    rectangle(x - 0.5f, y + 1f, lineWidth, adjustedHeight, outlineColor);
                    rectangle(x - 0.5f, y2 - adjustedHeight - 0.5f, lineWidth, adjustedHeight, outlineColor);
                    rectangle(x - 0.5f, y - 0.5f, adjustedWidth + 1, lineWidth, outlineColor);
                    rectangle(x2 - adjustedWidth - 0.5f, y - 0.5f, adjustedWidth + lineWidth, lineWidth, outlineColor);
                    rectangle(x2 - 0.5f, y + 1, lineWidth, adjustedHeight, outlineColor);
                    rectangle(x2 - 0.5f, y2 - adjustedHeight - 0.5f, lineWidth, adjustedHeight, outlineColor);
                    rectangle(x - 0.5f, y2 - 0.5f, adjustedWidth + 1, lineWidth, outlineColor);
                    rectangle(x2 - adjustedWidth - 0.5f, y2 - 0.5f, adjustedWidth + lineWidth, lineWidth, outlineColor);

                    rectangle(x, y + 0.5f, 0.5f, adjustedHeight, firstColor);
                    rectangle(x, y2 - adjustedHeight, 0.5f, adjustedHeight, firstColor);
                    rectangle(x, y, adjustedWidth, 0.5f, firstColor);
                    rectangle(x2 - adjustedWidth, y, adjustedWidth, 0.5f, firstColor);
                    rectangle(x2, y, 0.5f, adjustedHeight + 0.5f, firstColor);
                    rectangle(x2, y2 - adjustedHeight, 0.5f, adjustedHeight + 0.5f, firstColor);
                    rectangle(x, y2, adjustedWidth, 0.5f, firstColor);
                    rectangle(x2 - adjustedWidth, y2, adjustedWidth, 0.5f, firstColor);
                    break;
                }
                case "Corner": {
                    double cw = Math.min(width, height) * 0.28;
                    float t = 1.5f;
                    // black backdrop (slightly larger) then accent brackets on top
                    drawCorner(x - 0.5, y - 0.5, x2 + 0.5, y2 + 0.5, cw + 0.5, t + 1f, outlineColor);
                    drawCorner(x, y, x2, y2, cw, t, firstColor);
                    break;
                }
                case "None":
                default:
                    break;
            }

            if (!healthBar.getValue().getName().equals("None")) {
                Color backgroundColor = new Color(0, 0, 0, 180);
                rectangle(x - 2.5f, y - 0.5f, 1.5f, height + 1.5f, backgroundColor);

                float healthPercentage = MathHelper.clamp_float(entity.getHealth() / entity.getMaxHealth(), 0.0f, 1.0f);
                double health = (y2 - y - 2) * (1.0f - healthPercentage);

                switch (healthBar.getValue().getName()) {
                    case "Health":
                        int healthColor = Color.HSBtoRGB(healthPercentage / 3.0f, 1.0f, 1.0f);
                        rectangle(x - 2f, y + health, 0.5f, y2 - y - health + 0.5f, ColorUtil.withAlpha(new Color(healthColor), (int) (200 * pulse)));
                        break;
                    case "Standard":
                        rectangle(x - 2f, y + health, 0.5f, y2 - y - health + 0.5f, ColorUtil.withAlpha(firstColor, (int) (200 * pulse)));
                        break;
                    case "Gradient":
                        Color interpolatedColor = new Color(ColorUtil.mixColors(this.getTheme().getFirstColor(), this.getTheme().getSecondColor(), healthPercentage).getRGB());
                        verticalGradient(x - 2f, y + health, 0.5f, y2 - y - health + 0.5f, interpolatedColor, ColorUtil.withAlpha(this.getTheme().getSecondColor(), (int) (200 * pulse)));
                        break;
                }

                if (healthText.getValue()) {
                    String hp = String.valueOf((int) entity.getHealth());
                    double hw = healthFont.width(hp);
                    healthFont.drawWithShadow(hp, x - 3.5f - hw, y + health - healthFont.height() / 2f, ColorUtil.withAlpha(Color.WHITE, alpha).getRGB());
                }
            }

            if (armorBar.getValue()) {
                float armorDamageReduction = entity.getTotalArmorValue() / 20.0f;
                if (armorDamageReduction > 0) {
                    rectangle(x - 0.5f, y2 + 1.5f, width + 1.5f, 1.5f, new Color(0, 0, 0, 180));
                    horizontalGradient(x, y2 + 2f, (width + 0.5f) * armorDamageReduction, 0.5f, ColorUtil.withAlpha(this.getTheme().getFirstColor(), (int) (200 * pulse)), ColorUtil.mixColors(this.getTheme().getFirstColor(), this.getTheme().getSecondColor(), armorDamageReduction));
                }
            }

            if (nameTag.getValue()) {
                String name = entity.getDisplayName().getUnformattedText();
                float nameWidth = tagFont.width(name);
                float nameX = (float) (x + width / 2 - nameWidth / 2);
                float nameY = (float) (y - tagFont.height() - 2);
                RenderUtil.roundedRectangle(nameX - 2, nameY - 2, nameWidth + 4, tagFont.height() + 4, 3, new Color(0, 0, 0, 120));
                tagFont.draw(name, nameX, nameY, ColorUtil.withAlpha(Color.WHITE, alpha).getRGB());
            }

            if (distanceTag.getValue()) {
                String dist = (int) mc.thePlayer.getDistanceToEntity(entity) + "m";
                float dw = tagFont.width(dist);
                float dx = (float) (x + width / 2 - dw / 2);
                float dy = (float) (y2 + 3);
                RenderUtil.roundedRectangle(dx - 2, dy - 1, dw + 4, tagFont.height() + 3, 3, new Color(0, 0, 0, 120));
                tagFont.draw(dist, dx, dy, ColorUtil.withAlpha(firstColor, alpha).getRGB());
            }
        }
    };

    /**
     * Draws four corner brackets around the given box. {@code length} is the arm length of each bracket
     * and {@code thickness} its width.
     */
    private void drawCorner(double x, double y, double x2, double y2, double length, float thickness, Color color) {
        // top-left
        rectangle(x, y, length, thickness, color);
        rectangle(x, y, thickness, length, color);
        // top-right
        rectangle(x2 - length, y, length, thickness, color);
        rectangle(x2 - thickness, y, thickness, length, color);
        // bottom-left
        rectangle(x, y2 - thickness, length, thickness, color);
        rectangle(x, y2 - length, thickness, length, color);
        // bottom-right
        rectangle(x2 - length, y2 - thickness, length, thickness, color);
        rectangle(x2 - thickness, y2 - length, thickness, length, color);
    }
}
