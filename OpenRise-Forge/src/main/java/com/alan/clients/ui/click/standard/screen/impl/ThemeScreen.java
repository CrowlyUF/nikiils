package com.alan.clients.ui.click.standard.screen.impl;

import com.alan.clients.Client;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.module.impl.render.ClickGUI;
import com.alan.clients.ui.click.standard.components.theme.ThemeComponent;
import com.alan.clients.ui.click.standard.components.theme.ThemeKeyColorComponent;
import com.alan.clients.ui.click.standard.screen.Screen;
import com.alan.clients.ui.theme.Themes;
import com.alan.clients.util.Accessor;
import com.alan.clients.util.gui.GUIUtil;
import com.alan.clients.util.gui.ScrollUtil;
import com.alan.clients.util.localization.Localization;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.util.vector.Vector2d;
import com.alan.clients.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
public class ThemeScreen implements Screen, Accessor {
    private final ArrayList<ThemeComponent> allThemes = new ArrayList<>();
    private ArrayList<ThemeComponent> visibleThemes = new ArrayList<>();

    private final ArrayList<ThemeKeyColorComponent> colors = new ArrayList<>();
    private final ScrollUtil scrollUtil = new ScrollUtil();

    private ThemeKeyColorComponent selectedColor = null;

    public ThemeScreen() {
        for (Themes themes : Themes.values()) {
            this.allThemes.add(new ThemeComponent(themes));
        }
        for (Themes.KeyColors colors : Themes.KeyColors.values()) {
            this.colors.add(new ThemeKeyColorComponent(colors));
        }
    }

    @Override
    public void onRender(int mouseX, int mouseY, float partialTicks) {
        scrollUtil.onRender();

//        scrollUtil.setMax( -(60 + 57 * (Math.floor(visibleThemes.size() / 3D) - 3)));
        final double rows = Math.ceil(visibleThemes.size() / 3D);
        scrollUtil.setMax(-57 * Math.max(0, (rows - 3)));

        double padding = 7;
        double scrollX = getClickGUI().getPosition().getX() + getClickGUI().getScale().getX() - 4;
        double scrollY = getClickGUI().getPosition().getY() + padding;

        scrollUtil.renderScrollBar(new Vector2d(scrollX, scrollY), getClickGUI().scale.y - padding * 2);

        final Vector2f position = getClickGUI().getPosition();
        final Vector2f scale = getClickGUI().getScale();
        final double sidebar = getClickGUI().getSidebar().sidebarWidth;
        final double positionY = position.getY() + 44 + scrollUtil.getScroll();

        final double themeWidth = (scale.getX() - sidebar - 29) / 3D;
        final double colorWidth = (scale.getX() - sidebar - 43) / 5D;

//        FontManager.getIconsThree(28).drawString("4",
//                position.getX() + sidebar + 14, position.getY() + 16 + scrollUtil.getScroll(), -1);
        Fonts.MAIN.get(16, Weight.REGULAR).drawRight(Localization.get("ui.themes.text"),
                position.getX() + scale.getX() - 20, position.getY() + 20 + scrollUtil.getScroll(), new Color(255, 255, 255, 128).getRGB());

        // Draw key colors
        for (int i = 0; i < this.colors.size(); i++) {
            ThemeKeyColorComponent color = this.colors.get(i);

            color.draw(position.getX() + sidebar + 7 + ((7 + colorWidth) * (i % 5)),
                    positionY + Math.floor(i / 5D) * 24, colorWidth, this.selectedColor != null && this.selectedColor.equals(color));

            color.getDimAnimation().run(this.selectedColor == null || this.selectedColor.equals(color) ? 1 : 0);
            color.getBloomAnimation().run(Objects.equals(this.selectedColor, color) ? 1 : 0);
        }

        // Draw themes
        double themeRows = Math.ceil(visibleThemes.size() / 3D);
        double themesEndY = position.getY() + 44 + themeRows * 57 + 60;

        for (int i = 0; i < this.visibleThemes.size(); i++) {
            ThemeComponent theme = this.visibleThemes.get(i);

            theme.getXAnimation().run(position.getX() + sidebar + 7 + ((7 + themeWidth) * (i % 3)));
            theme.getYAnimation().run(position.getY() + 44 + Math.floor(i / 3D) * 57 + 60);
        }

        for (ThemeComponent theme : this.allThemes) {
            if (theme.getOpacityAnimation().getValue() > 0) {
                theme.draw(this.scrollUtil.getScroll(), themeWidth);
            }

            theme.getOpacityAnimation().run(this.visibleThemes.contains(theme) ? 255 : 0);
        }

        /* Ambient effects toggles */
        ClickGUI clickGUI = Client.INSTANCE.getModuleManager().get(ClickGUI.class);
        if (clickGUI != null) {
            double ambientY = themesEndY + 20 + scrollUtil.getScroll();
            Fonts.MAIN.get(16, Weight.REGULAR).draw("Ambient Effects", position.getX() + sidebar + 7, ambientY, new Color(255, 255, 255, 180).getRGB());

            String[] labels = {"Snow", "Rain"};
            boolean[] states = {clickGUI.ambientSnow.getValue(), clickGUI.ambientRain.getValue()};
            double toggleX = position.getX() + sidebar + 7;
            double toggleY = ambientY + 22;

            for (int i = 0; i < labels.length; i++) {
                double toggleWidth = 80;
                double toggleHeight = 22;
                Color bgColor = states[i] ? new Color(60, 120, 255, 200) : new Color(40, 40, 50, 200);

                RenderUtil.roundedRectangle(toggleX + i * (toggleWidth + 7), toggleY, toggleWidth, toggleHeight, 4, bgColor);
                Fonts.MAIN.get(14, Weight.REGULAR).draw(labels[i], toggleX + i * (toggleWidth + 7) + toggleWidth / 2 - Fonts.MAIN.get(14, Weight.REGULAR).width(labels[i]) / 2,
                        toggleY + 5, Color.WHITE.getRGB());
            }
        }
    }

    @Override
    public void onClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton > 0) return;

        for (ThemeComponent theme : this.visibleThemes) {
            if (GUIUtil.mouseOver(theme.getLastDraw().getX(), theme.getLastDraw().getY(), theme.getLastDraw().getZ(), 50, mouseX, mouseY)) {
                Client.INSTANCE.getThemeManager().setTheme(theme.getActiveTheme());
            }
        }

        for (ThemeKeyColorComponent color : this.colors) {
            if (GUIUtil.mouseOver(color.getLastDraw().getX(), color.getLastDraw().getY(), color.getLastDraw().getZ(), 17, mouseX, mouseY)) {
                if (this.selectedColor == color) {
                    this.selectedColor = null;
                } else {
                    this.selectedColor = color;
                }

                this.sortThemes();
            }
        }

        /* Ambient toggles */
        ClickGUI clickGUI = Client.INSTANCE.getModuleManager().get(ClickGUI.class);
        if (clickGUI != null) {
            Vector2f pos = getClickGUI().getPosition();
            Vector2f sc = getClickGUI().getScale();
            double sidebar = getClickGUI().getSidebar().sidebarWidth;
            double rows = Math.ceil(visibleThemes.size() / 3D);
            double themesEndY = pos.getY() + 44 + rows * 57 + 60;
            double ambientY = themesEndY + 20 + scrollUtil.getScroll();

            double toggleX = pos.getX() + sidebar + 7;
            double toggleY = ambientY + 22;
            double toggleWidth = 80, toggleHeight = 22;

            for (int i = 0; i < 2; i++) {
                if (GUIUtil.mouseOver(toggleX + i * (toggleWidth + 7), toggleY, toggleWidth, toggleHeight, mouseX, mouseY)) {
                    switch (i) {
                        case 0: clickGUI.ambientSnow.setValue(!clickGUI.ambientSnow.getValue()); break;
                        case 1: clickGUI.ambientRain.setValue(!clickGUI.ambientRain.getValue()); break;
                    }
                }
            }
        }
    }
    private void sortThemes() {
        this.visibleThemes.clear();

        if (this.selectedColor == null) {
            this.visibleThemes = new ArrayList<>(this.allThemes);
            this.visibleThemes.forEach(themeComponent -> themeComponent.getOpacityAnimation().run(255));
            return;
        }

        for (ThemeComponent theme : this.allThemes) {
            if (theme.getActiveTheme().getKeyColors().contains(this.selectedColor.getColor())) {
                this.visibleThemes.add(theme);
                theme.getOpacityAnimation().run(255);
            }
//            else {
//                theme.getOpacityAnimation().run(0);
//            }
        }
    }

    @Override
    public void onInit() {
        this.allThemes.forEach(theme -> theme.getOpacityAnimation().setValue(255));
        this.visibleThemes = new ArrayList<>(this.allThemes);
        this.selectedColor = null;
        this.scrollUtil.reset();
        this.resetAnimations();
    }

    public void resetAnimations() {
        final Vector2f position = getClickGUI().getPosition();
        final Vector2f scale = getClickGUI().getScale();
        final double sidebar = getClickGUI().getSidebar().sidebarWidth;
        final double themeWidth = (scale.getX() - sidebar - 29) / 3D;

        for (int i = 0; i < this.visibleThemes.size(); i++) {
            ThemeComponent theme = this.visibleThemes.get(i);

            theme.getXAnimation().setValue(position.getX() + sidebar + 7 + ((7 + themeWidth) * (i % 3)));
            theme.getYAnimation().setValue(position.getY() + 44 + Math.floor(i / 3D) * 57 + 60);
        }
    }
}