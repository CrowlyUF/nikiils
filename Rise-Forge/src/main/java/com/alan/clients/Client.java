package com.alan.clients;

import com.alan.clients.bindable.BindableManager;
import com.alan.clients.bots.BotManager;
import com.alan.clients.command.CommandManager;
import com.alan.clients.component.ComponentManager;
import com.alan.clients.event.Event;
import com.alan.clients.event.bus.impl.EventBus;
import com.alan.clients.layer.LayerManager;
import com.alan.clients.module.api.manager.ModuleManager;
import com.alan.clients.script.ScriptManager;
import com.alan.clients.ui.click.standard.RiseClickGUI;
import com.alan.clients.ui.theme.ThemeManager;
import com.alan.clients.util.constants.ConstantsManager;
import com.alan.clients.util.file.FileManager;
import com.alan.clients.util.file.alt.AltManager;
import com.alan.clients.util.file.config.ConfigManager;
import com.alan.clients.util.file.data.DataManager;
import com.alan.clients.util.file.insult.InsultFile;
import com.alan.clients.util.file.insult.InsultManager;
import com.alan.clients.util.localization.Locale;
import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.util.render.StencilUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main class where the client is loaded up.
 * Anything related to the client will start from here and managers etc. instances will be stored in this class.
 */
@Getter
public enum Client {

    /**
     * Simple enum instance for our client as enum instances
     * are immutable and are very easy to create and use.
     */
    INSTANCE;

    public static String NAME = "Rise";
    public static final String VERSION = "6";
    public static final String VERSION_FULL = "6.2.4";
    public static final String EDITION = "Forge 1.8.9";
    public static final String COPYRIGHT = "© Rise 2024. All Rights Reserved";
    public static final String CREDITS = "Made with <3 by Alan, Rotel and The_Bi11iona1re";

    public static boolean DEVELOPMENT_SWITCH = Boolean.getBoolean("rise.dev");
    public static boolean BETA_SWITCH = true;
    public static boolean FIRST_LAUNCH = true;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Setter
    private Locale locale = Locale.EN_US; // The language of the client

    private EventBus<Event> eventBus;
    @Setter
    private ModuleManager moduleManager;
    @Setter
    private ComponentManager componentManager;
    @Setter
    private CommandManager commandManager;
    @Getter
    private BotManager botManager;
    private ThemeManager themeManager;
    @Setter
    private ScriptManager scriptManager;
    private DataManager dataManager;

    private FileManager fileManager;

    private ConfigManager configManager;
    private AltManager altManager;
    private InsultManager insultManager;
    private BindableManager bindableManager;

    private LayerManager layerManager;

    @Setter
    private RiseClickGUI clickGUI;

    @Getter
    private Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * The main method when the Minecraft#startGame method is about
     * finish executing our client gets called and that's where we
     * can start loading our own classes and modules.
     */
    public void init() {
        final Minecraft mc = Minecraft.getMinecraft();

        // Rise forced GUI scale 2 here; that shrinks everything on phones and high-res screens, so the player's own
        // setting is kept.

        // OptiFine settings Rise forced for compatibility. Fast Render skips the main framebuffer that Rise's blur and
        // bloom read from, which blacks out the world. These are no-ops without OptiFine.
        MCHooks.setOptiFineOption("ofFastRender", false);
        MCHooks.setOptiFineOption("ofSmartAnimations", true);
        MCHooks.setOptiFineOption("ofSmoothFps", false);

        // allocate the stencil buffer up front (target HUDs use it) instead of mid-frame
        StencilUtil.checkSetupFBO(mc.getFramebuffer());

        // The event bus must exist before anything registers listeners
        this.eventBus = new EventBus<>();

        this.moduleManager = new ModuleManager();
        this.componentManager = new ComponentManager();
        this.commandManager = new CommandManager();
        this.fileManager = new FileManager();
        this.configManager = new ConfigManager();
        this.altManager = new AltManager();
        this.insultManager = new InsultManager();
        this.dataManager = new DataManager();
        this.botManager = new BotManager();
        this.themeManager = new ThemeManager();
        try {
            this.scriptManager = new ScriptManager();
        } catch (final Throwable throwable) {
            System.err.println("[Rise] Script engine unavailable: " + throwable);
        }
        this.bindableManager = new BindableManager();
        this.layerManager = new LayerManager();

        this.fileManager.init();

        this.dataManager.init();
        this.moduleManager.init();
        this.botManager.init();
        this.componentManager.init();
        this.commandManager.init();
        this.altManager.init();
        this.insultManager.init();
        try {
            this.scriptManager.init();
        } catch (final Throwable throwable) {
            // scripts are optional; a JVM without Nashorn shouldn't take the client down
            System.err.println("[Rise] Script engine unavailable: " + throwable);
        }

        this.clickGUI = new RiseClickGUI();
        this.clickGUI.initGui();

        this.insultManager.update();
        this.insultManager.forEach(InsultFile::read);

        this.configManager.init();
        this.bindableManager.init();

        Display.setTitle(NAME + " " + VERSION_FULL);
    }

    /**
     * The terminate method is called when the Minecraft client is shutting
     * down, so we can cleanup our stuff and ready ourselves for the client quitting.
     */
    public void terminate() {
        if (this.getAltManager() != null) {
            this.getAltManager().update();
        }

        if (this.getConfigManager() != null && this.getConfigManager().getLatestConfig() != null) {
            this.getConfigManager().getLatestConfig().write();
        }
    }

    public void reload() {
        terminate();
        init();
        Client.INSTANCE.getConfigManager().setupLatestConfig();
    }
}
