package com.alan.clients.ui.click.standard;

import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.Client;
import com.alan.clients.event.Listener;
import com.alan.clients.event.Priorities;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.impl.render.ClickGUI;
import com.alan.clients.ui.click.standard.components.ModuleComponent;
import com.alan.clients.ui.click.standard.components.category.SidebarCategory;
import com.alan.clients.ui.click.standard.components.value.ValueComponent;
import com.alan.clients.ui.click.standard.components.value.impl.BoundsNumberValueComponent;
import com.alan.clients.ui.click.standard.components.value.impl.NumberValueComponent;
import com.alan.clients.ui.click.standard.components.value.impl.StringValueComponent;
import com.alan.clients.ui.click.standard.screen.Colors;
import com.alan.clients.ui.click.standard.screen.Screen;
import com.alan.clients.ui.click.standard.screen.impl.SearchScreen;
import com.alan.clients.ui.click.standard.screen.impl.ThemeScreen;
import com.alan.clients.util.Accessor;
import com.alan.clients.util.animation.Animation;
import com.alan.clients.util.animation.Easing;
import com.alan.clients.util.gui.GUIUtil;
import com.alan.clients.util.interfaces.ThreadAccess;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.layer.Layer;
import com.alan.clients.util.shader.base.ShaderRenderType;
import com.alan.clients.util.shader.impl.AlphaShader;
import com.alan.clients.util.vector.Vector2d;
import com.alan.clients.util.vector.Vector2f;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import com.alan.clients.util.time.StopWatch;

import java.awt.*;
import java.io.IOException;
import java.text.Collator;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.alan.clients.layer.Layers.BLOOM;

@Getter
public class RiseClickGUI extends GuiScreen implements Accessor, ThreadAccess {

    public Vector2f position = new Vector2f(-1, -1);
    public Vector2f scale = new Vector2f(320 * 1.3f, 260 * 1.3f);

    /* Sidebar */
    public SidebarCategory sidebar = new SidebarCategory();

    /* Selected Screen */
    public Screen selectedScreen = Category.SEARCH.getClickGUIScreen();
    public Screen renderedScreen = selectedScreen;
    public Screen lastScreen = selectedScreen;

    public float draggingOffsetX, draggingOffsetY;
    public boolean dragging;
    public StopWatch timeInCategory = new StopWatch();
    public StopWatch stopwatch = new StopWatch();

    /* +1 when navigating down the sidebar, -1 when navigating up - drives the slide direction */
    public int transitionDirection = 1;
    /* Duration (ms) of the screen change transition */
    public int transitionLength = 320;

    public ConcurrentLinkedQueue<ModuleComponent> moduleList = new ConcurrentLinkedQueue<>();

    public Vector2f mouse;
    public double animationTime, opacity, animationVelocity;

    public int round = 7;

    Vector2d translate;
    public ValueComponent overlayPresent;
    public Vector2f moduleDefaultScale = new Vector2f(283, 38);
    public Animation scaleAnimation = new Animation(Easing.EASE_OUT_QUINT, 400);
    public Animation opacityAnimation = new Animation(Easing.EASE_OUT_QUINT, 200);

    /* Ambient particles */
    private final CopyOnWriteArrayList<AmbientParticle> snowflakes = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AmbientParticle> raindrops = new CopyOnWriteArrayList<>();
    private final Random random = new Random();
    private final StopWatch particleTimer = new StopWatch();

    private static class AmbientParticle {
        double x, y, speed, size, drift;
        float alpha = 1f;

        AmbientParticle(double x, double y, double speed, double size, double drift) {
            this.x = x; this.y = y; this.speed = speed;
            this.size = size; this.drift = drift;
        }
    }

    private void renderSnow() {
        double areaWidth = scale.x - sidebar.sidebarWidth;
        double areaHeight = scale.y;
        double areaX = position.x + sidebar.sidebarWidth;
        double areaY = position.y;

        if (particleTimer.finished(30)) {
            snowflakes.add(new AmbientParticle(
                    areaX + random.nextDouble() * areaWidth,
                    areaY,
                    0.5 + random.nextDouble() * 1.5,
                    1.5 + random.nextDouble() * 2.5,
                    -0.3 + random.nextDouble() * 0.6
            ));
            if (snowflakes.size() > 40) snowflakes.remove(0);
            particleTimer.reset();
        }

        for (AmbientParticle p : snowflakes) {
            p.y += p.speed;
            p.x += p.drift;
            p.alpha = Math.max(0, p.alpha - 0.003f);
            if (p.y > areaY + areaHeight || p.x < areaX || p.x > areaX + areaWidth) {
                p.alpha = 0;
            }
            if (p.alpha > 0) {
                RenderUtil.circle(p.x, p.y, p.size, ColorUtil.withAlpha(Color.WHITE, (int)(200 * p.alpha)));
            }
        }
        snowflakes.removeIf(p -> p.alpha <= 0);
    }

    private void renderRain() {
        double areaWidth = scale.x - sidebar.sidebarWidth;
        double areaHeight = scale.y;
        double areaX = position.x + sidebar.sidebarWidth;
        double areaY = position.y;

        if (particleTimer.finished(20)) {
            raindrops.add(new AmbientParticle(
                    areaX + random.nextDouble() * areaWidth,
                    areaY,
                    4 + random.nextDouble() * 4,
                    1 + random.nextDouble(),
                    -0.5 + random.nextDouble()
            ));
            if (raindrops.size() > 60) raindrops.remove(0);
        }

        for (AmbientParticle p : raindrops) {
            p.y += p.speed;
            p.x += p.drift;
            p.alpha = Math.max(0, p.alpha - 0.005f);
            if (p.y > areaY + areaHeight || p.x < areaX || p.x > areaX + areaWidth) {
                p.alpha = 0;
            }
            if (p.alpha > 0) {
                Color rainColor = ColorUtil.withAlpha(new Color(180, 200, 255), (int)(150 * p.alpha));
                RenderUtil.rectangle(p.x, p.y - p.speed, 1, p.speed + 1, rainColor);
            }
        }
        raindrops.removeIf(p -> p.alpha <= 0);
    }

    public void rebuildModuleCache() {
        moduleList.clear();

        java.util.List<Module> sortedModules = Client.INSTANCE.getModuleManager().getAll();
        sortedModules.sort((o1, o2) -> Collator.getInstance().compare(o1.getName(), o2.getName()));
        sortedModules.forEach(module -> moduleList.add(new ModuleComponent(module)));
    }

    @Override
    public void initGui() {
        if (moduleList == null || moduleList.isEmpty()) {
            rebuildModuleCache();
        }

        threadPool.execute(() -> {
            round = 12;
            scaleAnimation.reset();
            scaleAnimation.setValue(0);

            ScaledResolution scaledResolution = MCHooks.scaledResolution();

            lastScreen = selectedScreen;
            timeInCategory.reset();
            timeInCategory.setMillis(System.currentTimeMillis() - 150);

            Keyboard.enableRepeatEvents(true);
            stopwatch.reset();
            selectedScreen.onInit();

            if (this.position.x < 0 || this.position.y < 0 ||
                    this.position.x + this.scale.x > scaledResolution.getScaledWidth() ||
                    this.position.y + this.scale.y > scaledResolution.getScaledHeight()) {
                this.position.x = scaledResolution.getScaledWidth() / 2f - this.scale.x / 2;
                this.position.y = scaledResolution.getScaledHeight() / 2f - this.scale.y / 2;
            }

            moduleList.forEach(moduleComponent -> {
                moduleComponent.getValueList().forEach(valueComponent -> {
                    if (valueComponent instanceof NumberValueComponent) {
                        ((NumberValueComponent) valueComponent).updateSliders();
                    } else if (valueComponent instanceof BoundsNumberValueComponent) {
                        ((BoundsNumberValueComponent) valueComponent).updateSliders();
                    }
                });
            });
        });

//        Client.INSTANCE.getNetworkManager().getCommunication().write(new ClientCommunityPopulateRequest());
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        dragging = false;
    }

    // Removed the overlay alpha shader: the GUI now animates with scale/opacity on the background only.

    /**
     * If you remove this, expect to see me at your house within 24 hours. I found Alan's house
     * from a sunset picture. Don't think you're safe if you think it's a good idea to remove
     * this again.
     */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public void render() {
        scale = new Vector2f(400, 300);
        renderGUI();
    }


    public void renderGUI() {
        if (mouse == null) {
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();

        final int mouseX = (int) mouse.x;
        final int mouseY = (int) mouse.y;
        final float partialTicks = mc.timer.renderPartialTicks;

        /* Handles dragging */
        if (dragging) {
            if (this.selectedScreen instanceof ThemeScreen) {
                ((ThemeScreen) selectedScreen).resetAnimations();
            }
            position.x = mouseX + draggingOffsetX;
            position.y = mouseY + draggingOffsetY;
        }

        boolean isOpen = mc.currentScreen == Client.INSTANCE.getClickGUI();

        opacityAnimation.setEasing(isOpen ? Easing.EASE_OUT_CUBIC : Easing.LINEAR);
        opacityAnimation.setDuration(isOpen ? 200 : 100);
        opacityAnimation.run(isOpen ? 1 : 0);
        opacity = opacityAnimation.getValue();

        scaleAnimation.setEasing(isOpen ? Easing.EASE_OUT_QUINT : Easing.EASE_IN_CUBIC);
        scaleAnimation.run(isOpen ? 1 : 0);
        animationTime = scaleAnimation.getValue();

        if (isOpen && animationTime < 0.01) animationTime = 0.01;

        if (animationTime == 0) {
            Client.INSTANCE.getModuleManager().get(ClickGUI.class).setEnabled(false);
            return;
        }

        // Opening and closing animation - zoom from center
        double animScale = Math.max(animationTime, 0);
        double invAnim = 1.0 - animScale;
        double centerX = position.x + scale.x / 2.0;
        double centerY = position.y + scale.y / 2.0;
        translate = new Vector2d(centerX * invAnim, centerY * invAnim);

        Runnable updatePositionAndScale = () -> {
            GlStateManager.pushMatrix();
            if (animScale < 0.999) {
                GlStateManager.translate(translate.x, translate.y, 0);
                GlStateManager.scale(animScale, animScale, 0);
            }
        };

        updatePositionAndScale.run();
        getLayer(BLOOM, 2).add(updatePositionAndScale);

        /* Drop Shadow */
        if (animScale > 0.99) {
            RenderUtil.dropShadow(18, position.x, position.y, scale.x, scale.y, 30, round * 1.3);
        }

        /* Background */
        RenderUtil.roundedRectangle(position.x, position.y, scale.x, scale.y, round,
            ColorUtil.withAlpha(Colors.BACKGROUND.get(), (int) Math.round(opacity * 255)));

        /* Stop objects from going outside the ClickGUI */
        double scaledPosX = position.x * animScale + translate.x;
        double scaledPosY = position.y * animScale + translate.y;
        double scaledWidth = scale.x * animScale;
        double scaledHeight = scale.y * animScale;
        int pad = 1;

        Runnable startScissor = () -> {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtil.scissor(scaledPosX + pad, scaledPosY + pad, scaledWidth - pad * 2, scaledHeight - pad * 2);
        };

        startScissor.run();
        getLayer(BLOOM, 2).add(startScissor);

        Runnable translateAnimation = () -> {
            GL11.glPushMatrix();
            GL11.glTranslated(0, 0, 0);
        };

        getLayer(BLOOM, 2).add(translateAnimation);

        renderedScreen = selectedScreen;
        renderedScreen.onRender(mouseX, mouseY, partialTicks);

        sidebar.preRenderClickGUI();

        /* Background circles - reduce from 9 to 5 for performance */
        for (int i = 0; i <= 5; i++) {
            double radius = i * 50;
            RenderUtil.circle(position.x + sidebar.sidebarWidth - radius / 2, position.y + scale.y / 2 - radius / 2,
                    radius, ColorUtil.withAlpha(getTheme().getFirstColor(), 1));
        }

        /* Ambient effects */
        ClickGUI clickGUIModule = Client.INSTANCE.getModuleManager().get(ClickGUI.class);
        if (clickGUIModule != null && clickGUIModule.isEnabled() && clickGUIModule.ambientSnow.getValue()) {
            renderSnow();
        }
        if (clickGUIModule != null && clickGUIModule.isEnabled() && clickGUIModule.ambientRain.getValue()) {
            renderRain();
        }

        /* Sidebar */
        sidebar.renderSidebar(mouseX, mouseY);

        Runnable endScissor = () -> {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.popMatrix();
        };

        endScissor.run();
        getLayer(BLOOM, 2).add(endScissor);

        Runnable pop = GL11::glPopMatrix;
        getLayer(BLOOM, 2).add(pop);

        stopwatch.reset();
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.mouse = new Vector2f(mouseX, mouseY);
    }

    public void bloom() {
        translate = new Vector2d((position.x + scale.x / 2f) * (1 - animationTime), (position.y + scale.y / 2f) * (1 - animationTime));

        GlStateManager.pushMatrix();

        if (animationTime != 1) {
            GlStateManager.translate(translate.x, translate.y, 0);
            GlStateManager.scale(animationTime, animationTime, 0);
        }

        /* Stop objects from going outside the ClickGUI */
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderUtil.scissor(position.x * animationTime + translate.x, position.y * animationTime + translate.y, scale.x * animationTime, (scale.y - 4) * animationTime);

        renderedScreen.onBloom();
        sidebar.bloom();

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.popMatrix();
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) throws IOException {
        /* Registers click if you click within the window */

        if (GUIUtil.mouseOver(position.x, position.y, scale.x, 15, mouseX, mouseY) && overlayPresent == null) {
            draggingOffsetX = position.x - mouseX;
            draggingOffsetY = position.y - mouseY;
            dragging = true;
        }

        // Only register click if within the ClickGUI
        else if (GUIUtil.mouseOver(position.getX(), position.getY(), scale.getX(), scale.getY(), mouseX, mouseY)) {
            if (overlayPresent == null) sidebar.clickSidebar(mouseX, mouseY, mouseButton);
            selectedScreen.onClick(mouseX, mouseY, mouseButton);
        }

        overlayPresent = null;
    }

    @Override
    protected void mouseReleased(final int mouseX, final int mouseY, final int state) {
        /* Registers the mouse being released */
        dragging = false;

        selectedScreen.onMouseRelease();
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        if ("abcdefghijklmnopqrstuvwxyz1234567890 ".contains(String.valueOf(typedChar).toLowerCase()) && selectedScreen.automaticSearchSwitching() && !getClickGUI().activeTextBox()) {
            this.switchScreen(Category.SEARCH);
        }

        super.keyTyped(typedChar, keyCode);
        selectedScreen.onKey(typedChar, keyCode);
    }

    public void switchScreen(final Category category) {
        if (!category.getClickGUIScreen().equals(this.selectedScreen)) {
            lastScreen = this.getClickGUI().selectedScreen;
            selectedScreen = category.getClickGUIScreen();

            this.transitionDirection = directionBetween(lastScreen, selectedScreen);
            this.timeInCategory.reset();
            selectedScreen.onInit();

            final SearchScreen search = ((SearchScreen) Category.SEARCH.getClickGUIScreen());
            search.relevantModules = search.getRelevantModules(search.searchBar.getText());
        }
    }

    public void switchScreen(final Screen screen) {
        if (!this.selectedScreen.getClass().getSimpleName().equals(screen.getClass().getSimpleName())) {
            lastScreen = this.getClickGUI().selectedScreen;
            selectedScreen = screen;

            this.transitionDirection = directionBetween(lastScreen, selectedScreen);
            this.timeInCategory.reset();
            selectedScreen.onInit();

            final SearchScreen search = ((SearchScreen) Category.SEARCH.getClickGUIScreen());
            search.relevantModules = search.getRelevantModules(search.searchBar.getText());
        }
    }

    /** Index of a screen within the sidebar (category) ordering, or -1 if it isn't a category screen. */
    private int screenIndex(final Screen screen) {
        final Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].getClickGUIScreen() == screen) return i;
        }
        return -1;
    }

    /** +1 when navigating to a screen lower in the sidebar, -1 when navigating up. Defaults to +1. */
    private int directionBetween(final Screen from, final Screen to) {
        final int a = screenIndex(from), b = screenIndex(to);
        if (a < 0 || b < 0 || a == b) return 1;
        return b > a ? 1 : -1;
    }

    public boolean activeTextBox() {
        for (final ModuleComponent moduleComponent : moduleList) {
            for (final ValueComponent value : moduleComponent.getValueList()) {
                if (value instanceof StringValueComponent && value.position != null && ((StringValueComponent) value).textBox.selected && !((StringValueComponent) value).textBox.drawn.finished(50)) {
                    return true;
                } else if (value instanceof NumberValueComponent && ((NumberValueComponent) value).valueDisplay.isSelected() && !((NumberValueComponent) value).valueDisplay.drawn.finished(50)) {
                    return true;
                } else if (value instanceof BoundsNumberValueComponent && ((BoundsNumberValueComponent) value).valueDisplay.isSelected() && !((BoundsNumberValueComponent) value).valueDisplay.drawn.finished(50)) {
                    return true;
                }
            }
        }

        return false;
    }
}

