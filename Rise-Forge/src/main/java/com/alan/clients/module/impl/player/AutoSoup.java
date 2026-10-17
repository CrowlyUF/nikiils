package com.alan.clients.module.impl.player;

import com.alan.clients.component.impl.player.Slot;
import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PreUpdateEvent;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.value.impl.NumberValue;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import com.alan.clients.util.time.StopWatch;

@ModuleInfo(aliases = {"module.player.autosoup.name"}, description = "module.player.autosoup.description", category = Category.PLAYER)
public class AutoSoup extends Module {

    private final NumberValue health = new NumberValue("Minimum Health", this, 10, 1, 20, 1);
    private final NumberValue delay = new NumberValue("Delay", this, 250, 0, 1000, 50);
    private final StopWatch stopwatch = new StopWatch();

    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mc.thePlayer == null ||
                mc.theWorld == null ||
                mc.currentScreen != null ||
                mc.thePlayer.isUsingItem() ||
                mc.thePlayer.getHealth() > health.getValue().floatValue() ||
                !stopwatch.finished(delay.getValue().longValue())) {
            return;
        }

        for (int i = 0; i < 9; i++) {
            final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);

            if (stack == null || !(stack.getItem() instanceof ItemSoup)) {
                continue;
            }

            getComponent(Slot.class).setSlot(i);
            mc.playerController.syncCurrentPlayItem();
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack);
            stopwatch.reset();
            break;
        }
    };
}
