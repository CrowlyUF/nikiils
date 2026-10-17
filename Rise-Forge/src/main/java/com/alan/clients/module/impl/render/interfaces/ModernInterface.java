package com.alan.clients.module.impl.render.interfaces;

import com.alan.clients.Client;
import com.alan.clients.component.impl.render.ParticleComponent;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.other.KillEvent;
import com.alan.clients.event.impl.other.TickEvent;
import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.module.impl.render.Interface;
import com.alan.clients.module.impl.render.interfaces.api.ModuleComponent;
import com.alan.clients.util.font.Font;
import com.alan.clients.util.render.ColorUtil;
import com.alan.clients.util.render.RenderUtil;
import com.alan.clients.util.render.particle.Particle;
import com.alan.clients.util.vector.Vector2d;
import com.alan.clients.util.vector.Vector2f;
import com.alan.clients.value.Mode;
import com.alan.clients.value.impl.BooleanValue;
import com.alan.clients.value.impl.ModeValue;
import com.alan.clients.value.impl.StringValue;
import com.alan.clients.value.impl.SubMode;
import com.alan.clients.util.time.StopWatch;

import java.awt.*;
import java.util.Optional;
import java.util.function.Consumer;

import static com.alan.clients.font.Fonts.*;
import static com.alan.clients.layer.Layers.*;

public class ModernInterface extends Mode<Interface> {
    private final Font productSansMedium36 = MAIN.get(36, Weight.MEDIUM);
    private final Font watermarkFont = MAIN.get(18, Weight.REGULAR);
    private final Font productSansMedium18 = MAIN.get(18, Weight.MEDIUM);
    private final Font productSansRegular = MAIN.get(18, Weight.REGULAR);
    private static final String WATERMARK_TEXT = "riseclient.com";
    Font font = MAIN.get(18, Weight.REGULAR);
    private final StopWatch stopWatch = new StopWatch();

    private final ModeValue colorMode = new ModeValue("ArrayList Color Mode", this) {{
        add(new SubMode("Static"));
        add(new SubMode("Fade"));
        add(new SubMode("Breathe"));
        setDefault("Fade");
    }};

    private final ModeValue fontMode = new ModeValue("ArrayList Font", this) {{
        add(new SubMode("Apple UI"));
        add(new SubMode("Minecraft"));
        add(new SubMode("Custom"));
        setDefault("Apple UI");
    }};

    private final StringValue customFont = new StringValue("Custom Installed Font", this, "Arial", () -> !fontMode.getValue().getName().equals("Custom"));

    private final ModeValue shader = new ModeValue("Shader Effect", this) {{
        add(new SubMode("Glow"));
        add(new SubMode("Shadow"));
        setDefault("Shadow");
    }};

    private final BooleanValue dropShadow = new BooleanValue("Drop Shadow", this, true);
    private final BooleanValue sidebar = new BooleanValue("Sidebar", this, true);
//    private final BooleanValue outline = new BooleanValue("Outline", this, false); alan impl that pls
    private final BooleanValue particles = new BooleanValue("Particles on Kill", this, true);
    private final ModeValue background = new ModeValue("BackGround", this) {{
        add(new SubMode("Off"));
        add(new SubMode("Normal"));
        setDefault("Normal");
    }};
    private final StringValue customClientName = new StringValue("Custom Client Name", this, "");

    private boolean glow, shadow;
    private boolean normalBackGround;
    private String coordinates;
    private float userWidth, xyzWidth;
    private Color logoColor = new Color(0);
    private final StopWatch update = new StopWatch();

    public ModernInterface(String name, Interface parent) {
        super(name, parent);
    }

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        if (mc == null || mc.gameSettings.showDebugInfo || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        boolean minecraft = font == Fonts.MINECRAFT.get();

        this.getParent().setModuleSpacing(font.height() + (minecraft ? 2 : 0));

        this.getParent().setWidthComparator(font);
        this.getParent().setEdgeOffset(10);

        float sx = event.getScaledResolution().getScaledWidth();
        float sy = event.getScaledResolution().getScaledHeight() - font.height() - 1;
        double widthOffset = minecraft ? 3.5 : 2;

        if (glow || shadow) {
            getLayer(BLOOM).add(() -> {
                for (final ModuleComponent moduleComponent : this.getParent().getActiveModuleComponents()) {
                    if (moduleComponent.animationTime == 0) {
                        continue;
                    }

                    double x = moduleComponent.getPosition().getX();
                    double y = moduleComponent.getPosition().getY();

                    Color finalColor = moduleComponent.getColor();

                    // draw the module background
                    if (this.normalBackGround) {
                        if (!minecraft) {
                            RenderUtil.rectangle(x - widthOffset, y - 3,
                                    (moduleComponent.nameWidth + moduleComponent.tagWidth) + 3 + widthOffset,
                                    this.getParent().moduleSpacing,
                                    glow ? ColorUtil.withAlpha(finalColor, 255) : getTheme().getDropShadow());
                        } else {
                            RenderUtil.rectangle(x - widthOffset + .5f, y - 3,
                                    (moduleComponent.nameWidth + moduleComponent.tagWidth) + 3f + widthOffset,
                                    this.getParent().moduleSpacing, glow ? ColorUtil.withAlpha(finalColor, 255) : getTheme().getDropShadow());
                        }
                    } else {
                        // draw the module text
                        if (glow) {
                            drawText(moduleComponent, x + .5f, y, finalColor.getRGB());
                        } else if (shadow) {
                            font.draw(moduleComponent.getDisplayName(), x, y, Color.BLACK.getRGB());

                            if (moduleComponent.isHasTag()) {
                                font.draw(moduleComponent.getDisplayTag(), x + moduleComponent.getNameWidth() + 3, y, Color.BLACK.getRGB());
                            }
                        }
                    }

                    // draw the sidebar
                    if (sidebar.getValue()) {
                        RenderUtil.roundedRectangle(x + moduleComponent.getNameWidth() + moduleComponent.getTagWidth() + 2, y - 1.5f, 2, 9, 1, finalColor);
                    }
//                    if (outline.getValue()) {
//                        RenderUtil.roundedRectangle(x + moduleComponent.getNameWidth() + moduleComponent.getTagWidth() + 2, y - 3, 2, this.getParent().moduleSpacing, 1, finalColor);
//                    }
                }

                // Draw the logo
                ColorUtil.drawInterpolatedText(this.productSansMedium36, Client.NAME, 6, 6, true);

                double nameWidth = productSansMedium36.width(Client.NAME);
                double editionX = nameWidth + 12;

                productSansMedium18.drawWithShadow(Client.EDITION, editionX, 10, ColorUtil.withAlpha(Color.WHITE, 170).getRGB());

                if (!customClientName.getValue().isEmpty()) {
                    double customX = Math.max(editionX + productSansMedium18.width(Client.EDITION) + 12, nameWidth + 12);
                    this.productSansMedium18.draw(customClientName.getValue(), customX, 10, getTheme().getSecondColor().getRGB());
                }

                // information of user in the bottom right corner of the screen
                watermarkFont.drawWithShadow(WATERMARK_TEXT, sx - userWidth - 3, sy, 0xFFCCCCCC);

                // coordinates of user in the bottom left corner of the screen
                productSansRegular.drawWithShadow("XYZ:", 5, sy, 0xFFCCCCCC);
                productSansMedium18.drawWithShadow(coordinates, 5 + xyzWidth, sy, 0xFFCCCCCC);
            });
        }

        // modules in the top right corner of the screen
        for (final ModuleComponent moduleComponent : this.getParent().getActiveModuleComponents()) {
            if (moduleComponent.animationTime == 0) {
                continue;
            }

            double x = moduleComponent.getPosition().getX();
            double y = moduleComponent.getPosition().getY();

            Color finalColor = moduleComponent.getColor();

            if (this.normalBackGround) {
                Consumer<Color> backgroundRunnable = color -> {
                    if (!minecraft) {
                        RenderUtil.rectangle(x - widthOffset, y - 3f,
                                (moduleComponent.nameWidth + moduleComponent.tagWidth) + 3 + widthOffset,
                                this.getParent().moduleSpacing,
                                color);
                    } else {
                        RenderUtil.rectangle(x - widthOffset + .5f, y - 3,
                                (moduleComponent.nameWidth + moduleComponent.tagWidth) + 3f + widthOffset,
                                this.getParent().moduleSpacing, color);
                    }
                };

                getLayer(BLUR).add(() -> backgroundRunnable.accept(Color.BLACK));
                getLayer(REGULAR, 1).add(() -> backgroundRunnable.accept(getTheme().getBackgroundShade()));
            }

            getLayer(REGULAR, 1).add(() -> drawText(moduleComponent, x, y - .5f, finalColor.getRGB()));

            if (this.sidebar.getValue()) {
                RenderUtil.roundedRectangle(x + moduleComponent.getNameWidth() + moduleComponent.getTagWidth() + 2, y - 1.5f, 2, 9, 1, finalColor);
            }
//            if (this.outline.getValue()) {
//                RenderUtil.rectangle(x + moduleComponent.getNameWidth() + moduleComponent.getTagWidth() + 2, y - 3f, 2, this.getParent().moduleSpacing, finalColor);
//            }
        }

        if (coordinates == null) return;

        if (!stopWatch.finished(2000)) {
            getLayer(BLOOM).add(ParticleComponent::render);
        }

        // information of user in the bottom right corner of the screen
        watermarkFont.drawWithShadow("riseclient.com", sx - userWidth - 3, sy, 0xFFCCCCCC);

        // coordinates of user in the bottom left corner of the screen
        productSansRegular.drawWithShadow("XYZ:", 5, sy, 0xFFCCCCCC);
        productSansMedium18.drawWithShadow(coordinates, 5 + xyzWidth, sy, 0xFFCCCCCC);

        // title in the top left corner of the screen
        ColorUtil.drawInterpolatedText(this.productSansMedium36, Client.NAME, 6, 6, true);

        double nameWidth = productSansMedium36.width(Client.NAME);
        double editionX = nameWidth + 12;

        productSansMedium18.drawWithShadow(Client.EDITION, editionX, 10, ColorUtil.withAlpha(Color.WHITE, 170).getRGB());

        if (!customClientName.getValue().isEmpty()) {
            double customX = Math.max(editionX + productSansMedium18.width(Client.EDITION) + 12, nameWidth + 12);
            this.productSansMedium18.draw(customClientName.getValue(), customX, 10, getTheme().getSecondColor().getRGB());
        }

        if (update.finished(150 * 50)) {
            stopWatch.reset();
            update.reset();
        }
    };

    @EventLink
    public final Listener<KillEvent> onKill = event -> {
        if (!stopWatch.finished(2000) && this.particles.getValue()) {
            for (int i = 0; i <= 10; i++) {
                ParticleComponent.add(new Particle(new Vector2f(0, 0),
                        new Vector2f((float) Math.random(), (float) Math.random())));
            }
        }

        stopWatch.reset();
    };

    @EventLink
    public final Listener<TickEvent> onTick = event -> {
        if (mc.thePlayer == null || !mc.getNetHandler().doneLoadingTerrain)
            return;

        try {
            if (fontMode.getValue().getName().equals("Custom") && font != CUSTOM.get(18)) {
                font = CUSTOM.get(18);
            }
        } catch (Exception exception) {

        }

        glow = this.shader.getValue().getName().equals("Glow");
        shadow = this.shader.getValue().getName().equals("Shadow");
        userWidth = watermarkFont.width(WATERMARK_TEXT) + 2;
        coordinates = (int) Math.floor(mc.thePlayer.posX) + ", " + (int) Math.floor(mc.thePlayer.posY) + ", " + (int) Math.floor(mc.thePlayer.posZ);
        xyzWidth = this.productSansMedium18.width("XYZ:") + 2;
        logoColor = this.getTheme().getFirstColor();

        normalBackGround = background.getValue().getName().equals("Normal");

        switch (fontMode.getValue().getName()) {
            case "Apple UI": {
                Font apple = MAIN.get(18, Weight.REGULAR);
                if (!font.equals(apple)) font = apple;
                break;
            }
            case "Minecraft": {
                Font minecraft = MINECRAFT.get();
                if (!font.equals(minecraft)) font = minecraft;
                break;
            }
            case "Custom": {
                String name = customFont.getValue();
                if (Math.random() > 0.95) {
                    Optional<String> location = Fonts.getFontPaths().stream().filter(f -> removeNonAlphabetCharacters(f).toLowerCase()
                            .contains(removeNonAlphabetCharacters(name).toLowerCase())).findFirst();
                    if (location.isPresent() && !CUSTOM.getName().equals(location.get())) {
                        CUSTOM.setName(location.get());
                        CUSTOM.getSizes().clear();
                    }
                }
                break;
            }
        }

        boolean lower = this.getParent().lowercase.getValue();
        boolean removeSpaces = getParent().getRemoveSpaces().getValue();
        boolean suffixEnabled = this.getParent().suffix.getValue();

        for (final ModuleComponent moduleComponent : this.getParent().getActiveModuleComponents()) {
            if (moduleComponent.animationTime == 0) continue;

            moduleComponent.setHasTag(!moduleComponent.getTag().isEmpty() && suffixEnabled);

            String displayName = (lower ? moduleComponent.getTranslatedName().toLowerCase() : moduleComponent.getTranslatedName())
                    .replace(removeSpaces ? " " : "", "");
            String displayTag = (lower ? moduleComponent.getTag().toLowerCase() : moduleComponent.getTag())
                    .replace(removeSpaces ? " " : "", "");

            Color color = this.getTheme().getFirstColor();
            switch (this.colorMode.getValue().getName()) {
                case "Breathe": {
                    double factor = this.getTheme().getBlendFactor(new Vector2d(0, 0));
                    color = ColorUtil.mixColors(color, this.getTheme().getSecondColor(), factor);
                    break;
                }
                case "Fade": {
                    color = this.getTheme().getAccentColor(new Vector2d(0, moduleComponent.getPosition().getY()));
                    break;
                }
            }

            moduleComponent.setColor(color);
            moduleComponent.setNameWidth(font.width(displayName));
            moduleComponent.setTagWidth(moduleComponent.isHasTag() ? (font.width(displayTag) + 3) : 0);
            moduleComponent.setDisplayName(displayName);
            moduleComponent.setDisplayTag(displayTag);
        }
    };

    public static String removeNonAlphabetCharacters(String input) {
        return input.replaceAll("[^a-zA-Z]", "");
    }

    private void drawText(ModuleComponent component, double x, double y, int hex) {
        if (dropShadow.getValue()) {
            font.drawWithShadow(component.getDisplayName(), x, y, hex);

            if (component.isHasTag()) {
                font.drawWithShadow(component.getDisplayTag(), x + component.getNameWidth() + 3, y, 0xFFCCCCCC);
            }
        } else {
            font.draw(component.getDisplayName(), x, y, hex);

            if (component.isHasTag()) {
                font.draw(component.getDisplayTag(), x + component.getNameWidth() + 3, y, 0xFFCCCCCC);
            }
        }
    }
}