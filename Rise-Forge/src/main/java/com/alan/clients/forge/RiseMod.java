package com.alan.clients.forge;

import com.alan.clients.Client;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Forge entry point. The client itself starts at the end of {@code Minecraft#startGame} (see MixinMinecraft), exactly
 * where Rise started; this class only hooks the Forge render events.
 */
@Mod(modid = "rise", name = "Rise", version = Client.VERSION_FULL, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class RiseMod {

    public RiseMod() {
        // Nashorn ships in the JRE's ext folder, which LaunchWrapper's class loader doesn't delegate to;
        // the script engine (com.alan.clients.script) needs it
        Launch.classLoader.addClassLoaderExclusion("jdk.nashorn.");
    }

    @Mod.EventHandler
    public void init(final FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());

        if (Boolean.getBoolean("rise.selftest")) {
            net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(new SelfTest());
        }
    }
}
