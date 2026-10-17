package com.alan.clients.module.impl.render;

import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PostStrafeEvent;
import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.util.math.MathUtil;
import com.alan.clients.util.render.EntityColors;
import com.alan.clients.util.render.HudBackground;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.util.vector.Vector2d;
import com.alan.clients.util.vector.Vector2f;
import com.alan.clients.util.vector.Vector3d;
import com.alan.clients.value.impl.BooleanValue;
import com.alan.clients.value.impl.ColorValue;
import com.alan.clients.value.impl.DragValue;
import com.alan.clients.value.impl.ModeValue;
import com.alan.clients.value.impl.SubMode;

import java.awt.*;

import static com.alan.clients.layer.Layers.*;

@ModuleInfo(aliases = {"module.render.bpscounter.name"}, description = "module.render.bpscounter.description", category = Category.RENDER)
public final class BPSCounter extends Module {

    private final BooleanValue showTitle = new BooleanValue("Title", this, false);
    private final ModeValue style = new ModeValue("Style", this)
            .add(new SubMode("Rounded"))
            .add(new SubMode("Outline"))
            .add(new SubMode("Gradient"))
            .add(new SubMode("Minimal")).setDefault("Rounded");
    private final ModeValue colorMode = EntityColors.colorMode(new ModeValue("Color Mode", this));
    private final ColorValue customColor = new ColorValue("Custom Color", this, new Color(120, 90, 255),
            () -> !colorMode.getValue().getName().equals(EntityColors.CUSTOM));
    private final BooleanValue accentBar = new BooleanValue("Accent Bar", this, false);
    private final DragValue position = new DragValue("Position", this, new Vector2d(200, 200));

    private final Vector2f scale = new Vector2f(RenderUtil.GENERIC_SCALE, RenderUtil.GENERIC_SCALE);
    private Vector3d positionVector = new Vector3d(0, 0, 0);
    private String speed = "";

    @EventLink
    public final Listener<PostStrafeEvent> onPostStrafe = event -> {
        speed = String.valueOf(MathUtil.round(new Vector3d(mc.thePlayer.posX, 0, mc.thePlayer.posZ).distance(positionVector) * 20 * mc.timer.timerSpeed, 2));
        positionVector = new Vector3d(mc.thePlayer.posX, 0, mc.thePlayer.posZ);
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        Vector2d position = this.position.position;

        final String titleString = showTitle.getValue() ? "BPS " : "";
        final String bpsString = speed;

        final float titleWidth = Fonts.MAIN.get(20, Weight.BOLD).width(titleString);
        scale.x = titleWidth + Fonts.MAIN.get(20, Weight.REGULAR).width(bpsString);

        final Color accent = EntityColors.resolve(colorMode.getValue().getName(), customColor.getValue(), getTheme(), position.y, 0);

        getLayer(REGULAR, 1).add(() -> {
            double pad = HudBackground.draw(style.getValue().getName(), position.x, position.y, scale.x + 6, scale.y - 1,
                    getTheme().getRound(), getTheme().getBackgroundShade(), accent, accentBar.getValue());

            this.position.setScale(new Vector2d(scale.x + 6, scale.y - 1));

            final double textX = position.x + 3.0F + pad;
            final double textY = position.y + scale.y / 2.0F - Fonts.MAIN.get(20, Weight.REGULAR).height() / 4.0F;

            Fonts.MAIN.get(20, Weight.BOLD).drawWithShadow(titleString, textX, textY, accent.getRGB());
            Fonts.MAIN.get(20, Weight.REGULAR).drawWithShadow(bpsString, textX + titleWidth, textY, Color.WHITE.getRGB());
        });

        getLayer(BLUR).add(() -> RenderUtil.roundedRectangle(position.x, position.y, scale.x + 6, scale.y - 1, getTheme().getRound(), Color.BLACK));
        getLayer(BLOOM).add(() -> RenderUtil.roundedRectangle(position.x + 0.5F, position.y + 0.5F, scale.x + 6 - 1, scale.y - 2, getTheme().getRound() + 1, getTheme().getDropShadow()));
    };
}