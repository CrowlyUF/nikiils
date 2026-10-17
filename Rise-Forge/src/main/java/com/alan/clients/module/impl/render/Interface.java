package com.alan.clients.module.impl.render;

import com.alan.clients.Client;
import com.alan.clients.component.impl.render.NotificationComponent;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PreUpdateEvent;
import com.alan.clients.event.impl.other.ModuleToggleEvent;
import com.alan.clients.event.impl.other.ServerJoinEvent;
import com.alan.clients.event.impl.other.WorldChangeEvent;
import com.alan.clients.event.impl.render.Render2DEvent;
import com.alan.clients.font.Fonts;
import com.alan.clients.font.Weight;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.module.impl.render.interfaces.CreidaInterface;
import com.alan.clients.module.impl.render.interfaces.ExhibitionInterface;
import com.alan.clients.module.impl.render.interfaces.ModernInterface;
import com.alan.clients.module.impl.render.interfaces.WurstInterface;
import com.alan.clients.module.impl.render.interfaces.api.ModuleComponent;
import com.alan.clients.ui.theme.Themes;
import com.alan.clients.util.font.Font;
import com.alan.clients.util.math.MathUtil;
import com.alan.clients.util.vector.Vector2d;
import com.alan.clients.value.Value;
import com.alan.clients.value.impl.BooleanValue;
import com.alan.clients.value.impl.ModeValue;
import com.alan.clients.value.impl.SubMode;
import lombok.Getter;
import lombok.Setter;
import com.alan.clients.util.time.StopWatch;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@ModuleInfo(aliases = {"module.render.interface.name"}, description = "module.render.interface.description", category = Category.RENDER, autoEnabled = true)
public final class Interface extends Module {

    private final ModeValue mode = new ModeValue("Mode", this) {{
        add(new ModernInterface("Modern", (Interface) this.getParent()));
        add(new WurstInterface("Wurst", (Interface) this.getParent()));
        add(new ExhibitionInterface("Classic", (Interface) this.getParent()));
        add(new CreidaInterface("Creida", (Interface) this.getParent()));
        setDefault("Modern");
    }};

    private final ModeValue modulesToShow = new ModeValue("Modules to Show", this) {{
        add(new SubMode("All"));
        add(new SubMode("Exclude render"));
        add(new SubMode("Only bound"));
        setDefault("Exclude render");
    }};

    public BooleanValue suffix = new BooleanValue("Suffix", this, true);
    public BooleanValue lowercase = new BooleanValue("Lowercase", this, false);
    public BooleanValue removeSpaces = new BooleanValue("Remove Spaces", this, false);

    public BooleanValue shaders = new BooleanValue("Shaders", this, true);
    public BooleanValue toggleNotifications = new BooleanValue("Toggle Notifications", this, false);
    public BooleanValue rightToLeft = new BooleanValue("Right to Left", this, false);
    public BooleanValue hideF3 = new BooleanValue("Hide on F3", this, true);
    private List<ModuleComponent> allModuleComponents = new ArrayList<>(),
            activeModuleComponents = new ArrayList<>();

    private ModeValue informationType = new ModeValue("Information Type", this) {{
        add(new SubMode("Rise"));
        add(new SubMode("Traditional"));
        setDefault("Rise");
    }};

    private final StopWatch stopwatch = new StopWatch();
    private final StopWatch updateTags = new StopWatch();

    public Font widthComparator = Fonts.MAIN.get(20, Weight.REGULAR);
    @Setter
    public float moduleSpacing = 12, edgeOffset;

    public Interface() {
        createArrayList();
    }

    public void createArrayList() {
        allModuleComponents.clear();
        Client.INSTANCE.getModuleManager().getAll().stream()
                .sorted(Comparator.comparingDouble(module -> -widthComparator.width(module.getName())))
                .forEach(module -> allModuleComponents.add(new ModuleComponent(module)));

        this.updateTranslations();
    }

    public void sortArrayList() {
//        ArrayList<ModuleComponent> components = new ArrayList<>();
//        Client.INSTANCE.getModuleManager().getAll().forEach(module -> components.add(new ModuleComponent(module)));
//
        activeModuleComponents = allModuleComponents.stream()
                .filter(moduleComponent -> moduleComponent.shouldDisplay(this))
                .sorted(Comparator.comparingDouble(module -> -module.getNameWidth() - module.getTagWidth()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (false)
            activeModuleComponents.forEach(moduleComponent -> moduleComponent.position = moduleComponent.targetPosition);
    }

    @EventLink
    public final Listener<WorldChangeEvent> onWorldChange = event -> createArrayList();

    @EventLink
    public final Listener<ServerJoinEvent> onServerJoin = event -> createArrayList();

    @EventLink
    public final Listener<ModuleToggleEvent> onModuleToggle = event -> {
        if (this.toggleNotifications.getValue()) {
            NotificationComponent.post("Toggled", "Toggled " + event.getModule().getName() + " " + (event.getModule().isEnabled() ? "on" : "off"), 900);
        }
    };

    StopWatch lastUpdate = new StopWatch();

    public void updateTranslations() {
        for (final ModuleComponent moduleComponent : allModuleComponents) {
            moduleComponent.setTranslatedName(moduleComponent.getModule().getName());
        }
    }

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        threadPool.execute(() -> {
            Themes.setBackgroundShade(new Color(0, 0, 0, shaders.getValue() ? 110 : 150));

            if (lastUpdate.finished(15000)) {
                updateTranslations();

                lastUpdate.reset();
            }

            updateTags.reset();

            for (final ModuleComponent moduleComponent : activeModuleComponents) {
                if (moduleComponent.animationTime == 0) {
                    continue;
                }

                for (final Value<?> value : moduleComponent.getModule().getValues()) {
                    if (value instanceof ModeValue) {
                        final ModeValue modeValue = (ModeValue) value;

                        moduleComponent.setTag(modeValue.getValue().getName());
                        break;
                    }

                    moduleComponent.setTag("");
                }
            }

            this.sortArrayList();
        });
    };

    @EventLink
    public final Listener<Render2DEvent> onRender2D = event -> {
        float elapsed = stopwatch.getElapsedTime();
        float animSpeed = this.mode.getValue().getName().equals("Classic") ? 10.0F : 100.0F;

        int enabledIndex = 0;
        for (final ModuleComponent moduleComponent : allModuleComponents) {
            boolean shouldBeOn = moduleComponent.getModule().isEnabled();

            // Arm the staggered appearance delay only on the rising edge (the frame the module
            // was just enabled). Doing this every frame while animationTime is still 0 would
            // perpetually reset the timer, so modules after the first would never count down.
            if (shouldBeOn && !moduleComponent.wasEnabled) {
                moduleComponent.staggerTimer = enabledIndex * 30f;
            }
            moduleComponent.wasEnabled = shouldBeOn;

            if (shouldBeOn || moduleComponent.animationTime > 0) {
                if (shouldBeOn && moduleComponent.staggerTimer > 0) {
                    moduleComponent.staggerTimer = Math.max(0, moduleComponent.staggerTimer - elapsed);
                    enabledIndex++;
                    continue;
                }

                if (shouldBeOn) {
                    moduleComponent.animationTime = Math.min(moduleComponent.animationTime + elapsed / animSpeed, 10);
                } else {
                    moduleComponent.animationTime = Math.max(moduleComponent.animationTime - elapsed / animSpeed, 0);
                }
            }

            if (shouldBeOn) enabledIndex++;
        }

        if (activeModuleComponents.isEmpty()) {
            stopwatch.reset();
            return;
        }

        final float screenWidth = event.getScaledResolution().getScaledWidth();
        float posY = 0;

        for (final ModuleComponent moduleComponent : activeModuleComponents) {
            if (moduleComponent.animationTime == 0) continue;

            double targetX = screenWidth - moduleComponent.getNameWidth() - moduleComponent.getTagWidth() - edgeOffset;
            double targetY = posY + edgeOffset;

            if (!moduleComponent.getModule().isEnabled() && moduleComponent.animationTime < 10) {
                targetX = screenWidth + moduleComponent.getNameWidth() + moduleComponent.getTagWidth();
                targetY = posY;
            } else {
                posY += moduleSpacing;
            }

            moduleComponent.targetPosition.x = targetX;
            moduleComponent.targetPosition.y = targetY;

            double dx = Math.abs(moduleComponent.position.x - targetX);
            double dy = Math.abs(moduleComponent.position.y - targetY);

            if (dx > 0.5 || dy > 0.5 || (moduleComponent.animationTime != 0 && moduleComponent.animationTime != 10)) {
                float lerpFactor = 1.5E-2F * elapsed;
                moduleComponent.position.x = MathUtil.lerp(moduleComponent.position.x, targetX, lerpFactor);
                moduleComponent.position.y = MathUtil.lerp(moduleComponent.position.y, targetY, lerpFactor);
            } else {
                moduleComponent.position.x = targetX;
                moduleComponent.position.y = targetY;
            }
        }

        stopwatch.reset();
    };
}