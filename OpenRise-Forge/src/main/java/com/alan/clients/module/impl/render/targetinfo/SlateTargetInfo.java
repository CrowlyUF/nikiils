package com.alan.clients.module.impl.render.targetinfo;

import com.alan.clients.util.mc.MCHooks;

import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.module.impl.render.TargetInfo;
import com.alan.clients.ui.theme.Themes;
import com.alan.clients.util.animation.Animation;
import com.alan.clients.util.font.Font;
import com.alan.clients.util.math.MathUtil;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.EntityColors;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.util.render.StencilUtil;
import com.alan.clients.util.vector.Vector2d;
import com.alan.clients.value.Mode;
import com.alan.clients.value.impl.BooleanValue;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderSkeleton;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;

import static com.alan.clients.layer.Layers.*;
import static com.alan.clients.util.animation.Easing.*;

/**
 * A clean, modern "card" target info: rounded background, circular-cropped player head, name,
 * distance, and an animated gradient health bar. Accent colour follows the shared Color Mode
 * configured on the parent {@link TargetInfo} module.
 */
public class SlateTargetInfo extends Mode<TargetInfo> {

    private final BooleanValue showDistance = new BooleanValue("Distance", this, true);
    private final BooleanValue accentBar = new BooleanValue("Accent Bar", this, true);

    private final Font nameFont = Fonts.MAIN.get(20, Weight.MEDIUM);
    private final Font smallFont = Fonts.MAIN.get(16, Weight.REGULAR);

    private final int EDGE = 8;
    private final int FACE = 30;

    private final Animation openingAnimation = new Animation(EASE_OUT_ELASTIC, 500);
    private final Animation healthAnimation = new Animation(EASE_OUT_QUINT, 250);

    private TargetInfo targetInfoModule;

    public SlateTargetInfo(String name, TargetInfo parent) {
        super(name, parent);
    }

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        if (this.targetInfoModule == null) {
            this.targetInfoModule = this.getModule(TargetInfo.class);
        }

        Entity target = this.targetInfoModule.target;
        if (!(target instanceof AbstractClientPlayer)) return;

        AbstractClientPlayer player = (AbstractClientPlayer) target;

        boolean out = (!this.targetInfoModule.inWorld || this.targetInfoModule.stopwatch.finished(1000));
        openingAnimation.setDuration(out ? 400 : 850);
        openingAnimation.setEasing(out ? EASE_IN_BACK : EASE_OUT_ELASTIC);
        openingAnimation.run(out ? 0 : 1);

        if (openingAnimation.getValue() <= 0) return;

        String name = player.getName();
        double health = Math.min(!this.targetInfoModule.inWorld ? 0 : MathUtil.round(player.getHealth(), 1), player.getMaxHealth());
        String healthStr = String.valueOf(health);
        String distanceStr = (int) mc.thePlayer.getDistanceToEntity(player) + "m";

        double nameWidth = nameFont.width(name);
        double infoWidth = Math.max(nameWidth, 80);

        double width = EDGE + FACE + EDGE + infoWidth + EDGE + 10;
        double height = FACE + EDGE * 2;

        double x = this.targetInfoModule.position.x;
        double y = this.targetInfoModule.position.y;

        this.targetInfoModule.positionValue.setScale(new Vector2d(width, height));

        double scale = openingAnimation.getValue();

        // Resolve accent colour from the shared colour-mode system.
        Color accent = EntityColors.resolve(targetInfoModule.colorMode.getValue().getName(),
                targetInfoModule.customColor.getValue(), getTheme(), player, y, 0, 50);

        double healthBarWidth = infoWidth + 10;
        healthAnimation.run((health / player.getMaxHealth()) * healthBarWidth);
        double healthRemaining = healthAnimation.getValue();

        // ---- background (BLUR + BLOOM shadow + REGULAR body) ----
        getLayer(BLUR).add(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.translate((x + width / 2) * (1 - scale), (y + height / 2) * (1 - scale), 0);
            GlStateManager.scale(scale, scale, 1);
            RenderUtil.roundedRectangle(x, y, width, height, 8, Color.BLACK);
            GlStateManager.popMatrix();
        });

        getLayer(BLOOM).add(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.translate((x + width / 2) * (1 - scale), (y + height / 2) * (1 - scale), 0);
            GlStateManager.scale(scale, scale, 1);
            RenderUtil.roundedRectangle(x + 0.5, y + 0.5, width - 1, height - 1, 8, getTheme().getDropShadow());
            GlStateManager.popMatrix();
        });

        getLayer(REGULAR).add(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.translate((x + width / 2) * (1 - scale), (y + height / 2) * (1 - scale), 0);
            GlStateManager.scale(scale, scale, 1);

            RenderUtil.roundedRectangle(x, y, width, height, 8, Themes.getBackgroundShade());

            // left accent strip
            if (accentBar.getValue()) {
                RenderUtil.roundedRectangle(x + 2.5, y + 4, 2, height - 8, 1, accent);
            }

            double textX = x + EDGE + FACE + EDGE;

            // name
            nameFont.drawWithShadow(name, textX, y + EDGE + 1, Color.WHITE.getRGB());

            // distance
            if (showDistance.getValue()) {
                smallFont.drawWithShadow(distanceStr, textX, y + EDGE + nameFont.height() + 1,
                        ColorUtil.withAlpha(Color.WHITE, 170).getRGB());
            }

            // health bar background
            double barY = y + height - EDGE - 5;
            RenderUtil.roundedRectangle(textX, barY, healthBarWidth, 5, 2.5,
                    ColorUtil.withAlpha(Color.BLACK, 140));
            // health bar fill
            RenderUtil.drawRoundedGradientRect(textX, barY, Math.max(2, healthRemaining), 5, 2.5,
                    ColorUtil.mixColors(getTheme().getSecondColor(), accent, 0.5), accent, false);
            // health text
            smallFont.drawWithShadow(healthStr + " HP", textX + healthBarWidth + 4, barY - 1.5,
                    Color.WHITE.getRGB());

            GlStateManager.popMatrix();
        });

        getLayer(REGULAR, 1).add(() -> {
            GlStateManager.pushMatrix();
            GlStateManager.translate((x + width / 2) * (1 - scale), (y + height / 2) * (1 - scale), 0);
            GlStateManager.scale(scale, scale, 1);
            renderHead(player, x + EDGE, y + EDGE, FACE);
            GlStateManager.popMatrix();
        });
    };

    private void renderHead(final AbstractClientPlayer player, final double x, final double y, final double size) {
        StencilUtil.initStencil();
        StencilUtil.bindWriteStencilBuffer();
        RenderUtil.roundedRectangle(x, y, size, size, 6, Themes.getBackgroundShade());
        StencilUtil.bindReadStencilBuffer(1);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        GlStateManager.enableTexture2D();

        final ResourceLocation skin = targetInfoModule.inWorld && player.getHealth() > 0
                ? player.getLocationSkin() : MCHooks.SKELETON_TEXTURE;

        mc.getTextureManager().bindTexture(skin);
        MCHooks.drawScaledCustomSizeModalRect(x, y, 4, 4, 4, 4, size, size, 32, 32);
        GlStateManager.disableBlend();
        StencilUtil.uninitStencilBuffer();

        RenderUtil.roundedOutlineRectangle(x - 0.5, y - 0.5, size + 1, size + 1, 6, 0.5, ColorUtil.withAlpha(Color.BLACK, 60));
    }
}
