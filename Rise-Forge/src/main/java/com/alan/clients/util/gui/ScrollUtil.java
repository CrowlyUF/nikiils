package com.alan.clients.util.gui;

import com.alan.clients.util.Accessor;
import com.alan.clients.util.animation.Animation;
import com.alan.clients.util.animation.Easing;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.util.vector.Vector2d;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.input.Mouse;
import com.alan.clients.util.time.StopWatch;

import java.awt.*;

@Getter
@Setter
public class ScrollUtil implements Accessor {

    public double target, scroll, max = 25;
    private final Animation scrollAnimation = new Animation(Easing.EASE_OUT_EXPO, 300);
    public StopWatch stopWatch2 = new StopWatch();
    public boolean scrollingIsAllowed, active, animating;
    private boolean reverse;

    public void onRender() {
        onRender(true);
    }

    public void onRender(boolean update) {
        if (stopWatch2.finished(50)) {
            final float wheel = update ? Mouse.getDWheel() * (reverse ? -1 : 1) : 0;
            double stretch = 30;
            active = wheel != 0;
            target = Math.min(Math.max(target + wheel / 2, max - (wheel == 0 ? 0 : stretch)), (wheel == 0 ? 0 : stretch));

            stopWatch2.reset();
        }

        scrollAnimation.run(target);
        scroll = scrollAnimation.getValue();

        animating = Math.abs(scroll - target) > 0.5;
    }

    public void renderScrollBar(Vector2d position, double maxHeight) {
        double percentage = (reverse ? (getMax() - getScroll()) : getScroll()) / getMax();
        double scrollBarHeight = maxHeight - ((getMax() / (getMax() - maxHeight)) * maxHeight);

        scrollingIsAllowed = scrollBarHeight < maxHeight;
        if (!scrollingIsAllowed) return;

        double scrollX = position.x;
        double scrollY = position.y + maxHeight * percentage - scrollBarHeight * percentage;
        Color color = ColorUtil.withAlpha(Color.WHITE, 60);

        RenderUtil.roundedRectangle(scrollX, scrollY, 1, scrollBarHeight, 0.5f,
                color);
    }

    public void reset() {
        this.scroll = 0;
        this.target = 0;
    }
}
