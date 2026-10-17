package com.alan.clients.component.impl.render;

import com.alan.clients.component.Component;
import com.alan.clients.event.Listener;
import com.alan.clients.event.Priorities;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PreMotionEvent;
import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.util.EvictingList;
import com.alan.clients.util.animation.Animation;
import com.alan.clients.util.animation.Easing;
import com.alan.clients.util.font.Font;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.util.tuples.Triple;
import com.alan.clients.util.vector.Vector2d;
import net.minecraft.client.renderer.GlStateManager;
import com.alan.clients.util.time.StopWatch;

import java.awt.*;

import static com.alan.clients.layer.Layers.*;

public class NotificationComponent extends Component {

    private static final EvictingList<Triple<String, String, Integer>> queue = new EvictingList<>(5);
    private static final StopWatch time = new StopWatch();
    private static Triple<String, String, Integer> current;
    private static final Animation scaleAnimation = new Animation(Easing.EASE_OUT_EXPO, 500);
    private static final Animation slideAnimation = new Animation(Easing.EASE_OUT_EXPO, 400);
    private static final Vector2d SCALE = new Vector2d(140, 30);
    private static final Vector2d ICON_SCALE = new Vector2d(20, 20);
    private static final Vector2d POSITION = new Vector2d(5, 27);
    private static final double SPACER = (SCALE.y - ICON_SCALE.y) / 2f;

    private static final Font bold = Fonts.MAIN.get(15, Weight.BOLD);
    private static final Font light = Fonts.MAIN.get(15, Weight.LIGHT);

    @EventLink(value = Priorities.VERY_HIGH)
    public final Listener<Render2DEvent> onRender2DEvent = event -> {
        if (current == null) return;

        boolean out = time.finished(current.getThird());

        scaleAnimation.run(out ? 1.1 : 1);
        scaleAnimation.setDuration(out ? 400 : 500);
        scaleAnimation.setEasing(Easing.EASE_OUT_EXPO);
        double scale = scaleAnimation.getValue();
        double opacity = 1 - 10 * Math.abs(1 - scaleAnimation.getValue());

        slideAnimation.run(out ? SCALE.x + 20 : 0);
        slideAnimation.setDuration(out ? 300 : 500);
        double slideOffset = slideAnimation.getValue();

        if (scaleAnimation.isFinished() && out) return;

        getLayer(REGULAR, 1).add(() -> {
            GlStateManager.pushMatrix();
            double centerX = POSITION.x + SCALE.x / 2;
            GlStateManager.translate(centerX * (1 - scale) - slideOffset, (POSITION.y + SCALE.y / 2) * (1 - scale), 0);
            GlStateManager.scale(scale, scale, 0);

            RenderUtil.roundedRectangle(POSITION.x, POSITION.y, SCALE.x, SCALE.y, 10, ColorUtil.withAlpha(getTheme().getBackgroundShade(), (int) (getTheme().getBackgroundShade().getAlpha() * opacity)));

            Color iconColor = getNotificationColor(current.getFirst());
            RenderUtil.roundedRectangle(POSITION.x + SPACER, POSITION.y + SPACER, ICON_SCALE.x, ICON_SCALE.y, 6, ColorUtil.withAlpha(iconColor, (int) (255 * opacity)));

            String icon = getNotificationIcon(current.getFirst());
            Fonts.MAIN.get(18, Weight.REGULAR).draw(icon, POSITION.x + SPACER + 5, POSITION.y + SPACER + 2, ColorUtil.withAlpha(Color.WHITE, (int) (255 * opacity)).getRGB());

            bold.drawWithShadow(current.getFirst(), POSITION.x + SPACER + ICON_SCALE.x + SPACER, POSITION.y + SPACER + 3, ColorUtil.withAlpha(getTheme().getFirstColor(), (int) (255 * opacity)).getRGB());

            light.drawWithShadow(current.getSecond(), POSITION.x + SPACER + ICON_SCALE.x + SPACER, POSITION.y + SPACER + 0.5 + SPACER * 0.7 + bold.height(), ColorUtil.withAlpha(Color.WHITE, (int) (255 * opacity)).getRGB());

            GlStateManager.popMatrix();
        });

        getLayer(BLOOM).add(() -> {
            GlStateManager.pushMatrix();
            double centerX = POSITION.x + SCALE.x / 2;
            GlStateManager.translate(centerX * (1 - scale) - slideOffset, (POSITION.y + SCALE.y / 2) * (1 - scale), 0);
            GlStateManager.scale(scale, scale, 0);

            RenderUtil.roundedRectangle(POSITION.x + 0.5F, POSITION.y + 0.5F, SCALE.x - 1, SCALE.y - 1, 11, ColorUtil.withAlpha(getTheme().getDropShadow(), (int) (getTheme().getDropShadow().getAlpha() * opacity)));

            GlStateManager.popMatrix();
        });

        getLayer(BLUR).add(() -> {
            if (Math.abs(scaleAnimation.getValue() - 1) > 0.045) return;

            GlStateManager.pushMatrix();
            double centerX = POSITION.x + SCALE.x / 2;
            GlStateManager.translate(centerX * (1 - scale) - slideOffset, (POSITION.y + SCALE.y / 2) * (1 - scale), 0);
            GlStateManager.scale(scale, scale, 0);

            RenderUtil.roundedRectangle(POSITION.x, POSITION.y, SCALE.x, SCALE.y, 10, ColorUtil.withAlpha(Color.BLACK, (int) (255 * opacity)));

            GlStateManager.popMatrix();
        });
    };

    @EventLink(value = Priorities.VERY_HIGH)
    public final Listener<PreMotionEvent> onPreMotion = event -> {
        if (mc.thePlayer.ticksExisted % 5 != 0) return;

        if (!queue.isEmpty() && (current == null || time.finished(current.getThird() + 200))) {
            if (current != null) queue.remove(current);

            if (!queue.isEmpty()) {
                current = queue.get(0);
                time.reset();
                slideAnimation.setValue(-20);
            }
            SCALE.x = Math.max(140, light.width(current.getSecond()) + SPACER * 3
                    + ICON_SCALE.x + 2);
        }
    };

    private static Color getNotificationColor(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("toggle")) return new Color(80, 180, 255);
        if (lower.contains("error") || lower.contains("fail")) return new Color(255, 80, 80);
        if (lower.contains("success") || lower.contains("enabled")) return new Color(80, 220, 120);
        if (lower.contains("warning")) return new Color(255, 200, 60);
        return new Color(180, 180, 180);
    }

    private static String getNotificationIcon(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("toggle")) return "✓";
        if (lower.contains("error") || lower.contains("fail")) return "✕";
        if (lower.contains("success") || lower.contains("enabled")) return "✓";
        if (lower.contains("warning")) return "⚠";
        return "ⓘ";
    }

    public static void post(String title, String description) {
        post(title, description, 3000);
    }

    public static void post(String title, String description, Integer time) {
        queue.add(new Triple<>(title, description, time));
    }

}
