package com.nikiils.mushinfo;

import com.nikiils.mushinfo.check.PlayerChecks;
import com.nikiils.mushinfo.gui.ChatInterceptor;
import com.nikiils.mushinfo.gui.InfoRenderer;
import com.nikiils.mushinfo.net.PacketListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = MushInfo.MODID, name = "MushInfo", version = "1.0.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class MushInfo {

    public static final String MODID = "mushinfo";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ChatInterceptor());
        MinecraftForge.EVENT_BUS.register(new InfoRenderer());
        MinecraftForge.EVENT_BUS.register(PlayerChecks.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new PacketListener());
    }
}
