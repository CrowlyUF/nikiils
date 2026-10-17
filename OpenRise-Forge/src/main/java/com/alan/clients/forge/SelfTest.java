package com.alan.clients.forge;

import com.alan.clients.Client;
import com.alan.clients.event.bus.impl.EventBus;
import com.alan.clients.module.Module;
import com.alan.clients.module.impl.render.ClickGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Development smoke test, only registered with {@code -Dopenrise.selftest=true}: loads a flat singleplayer world,
 * opens the ClickGUI, enables every module for a moment, takes screenshots and quits. Results go to the log with a
 * {@code [selftest]} prefix.
 */
public final class SelfTest {

    /** Modules that would spam chat/packets or need a key held; they are only constructed, not enabled. */
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList("Crasher", "ConsoleSpammer", "Spammer", "FreeLook", "ClickGUI", "Panic"));

    private final Minecraft mc = Minecraft.getMinecraft();
    private int stage, ticks, moduleIndex;
    private List<Module> modules;
    private int errorsBefore;
    private final List<String> failed = new ArrayList<>();

    @SubscribeEvent
    public void onTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;

        switch (stage) {
            case 0:
                if (mc.currentScreen instanceof GuiMainMenu && ticks > 60) {
                    shot("01-main-menu");
                    log("loading world");
                    final WorldSettings settings = new WorldSettings(1337L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT);
                    mc.launchIntegratedServer("openrise-selftest", "openrise-selftest", settings);
                    next();
                }
                break;
            case 1:
                if (mc.thePlayer != null && mc.theWorld != null && ticks > 200) {
                    shot("02-in-game-hud");
                    Client.INSTANCE.getModuleManager().get(ClickGUI.class).setEnabled(true);
                    next();
                }
                break;
            case 2:
                if (ticks > 60) {
                    shot("03-clickgui");
                    Client.INSTANCE.getModuleManager().get(ClickGUI.class).setEnabled(false);
                    mc.displayGuiScreen(null);
                    modules = new ArrayList<>(Client.INSTANCE.getModuleManager().getAll());
                    log("toggling " + modules.size() + " modules");
                    next();
                }
                break;
            case 3:
                stepModules();
                break;
            case 4:
                if (ticks > 40) {
                    shot("04-after-modules");
                    log("done: " + modules.size() + " modules, " + failed.size() + " with listener errors " + failed
                            + ", total listener errors " + EventBus.ERRORS.get());
                    mc.shutdown();
                    next();
                }
                break;
            default:
                break;
        }
    }

    private void stepModules() {
        if (moduleIndex >= modules.size()) {
            next();
            return;
        }

        final Module module = modules.get(moduleIndex);
        final String name = module.getClass().getSimpleName();

        if (SKIP.contains(name)) {
            moduleIndex++;
            ticks = 0;
            return;
        }

        try {
            if (ticks == 1) {
                errorsBefore = EventBus.ERRORS.get();
                if (!module.isEnabled()) module.setEnabled(true);
            } else if (ticks == 12) {
                if (module.getModuleInfo().allowDisable()) module.setEnabled(false);
            } else if (ticks > 14) {
                if (EventBus.ERRORS.get() != errorsBefore) failed.add(name);
                moduleIndex++;
                ticks = 0;
            }
        } catch (final Throwable throwable) {
            log(name + " threw " + throwable);
            throwable.printStackTrace();
            failed.add(name);
            moduleIndex++;
            ticks = 0;
        }
    }

    private void next() {
        stage++;
        ticks = 0;
    }

    private void shot(final String name) {
        final IChatComponent result = ScreenShotHelper.saveScreenshot(mc.mcDataDir, "selftest-" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        log("screenshot " + name + ": " + (result == null ? "?" : result.getUnformattedText()));
    }

    private static void log(final String message) {
        System.out.println("[selftest] " + message);
    }
}
