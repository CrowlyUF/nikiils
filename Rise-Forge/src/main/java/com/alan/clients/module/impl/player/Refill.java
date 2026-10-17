package com.alan.clients.module.impl.player;

import com.alan.clients.event.Listener;
import com.alan.clients.event.annotations.EventLink;
import com.alan.clients.event.impl.motion.PreUpdateEvent;
import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.util.packet.PacketUtil;
import com.alan.clients.value.impl.BooleanValue;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;

@ModuleInfo(aliases = {"module.player.refill.name"}, description = "module.player.refill.description", category = Category.PLAYER)
public class Refill extends Module {

    private final BooleanValue autoClose = new BooleanValue("Auto Close", this, true);
    private long lastClicked = 0;
    private static final long CLICK_DELAY = 50;
    @EventLink
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (!(mc.currentScreen instanceof GuiInventory)) {
            return;
        }

        int emptySlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot == -1) {
            return;
        }

        for (int i = 9; i < 36; i++) {
            final ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);

            if (stack == null || !(stack.getItem() instanceof ItemPotion)) {
                continue;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClicked > CLICK_DELAY) {
                mc.playerController.windowClick(
                        mc.thePlayer.inventoryContainer.windowId,
                        i,
                        0,
                        4,
                        mc.thePlayer
                );
                lastClicked = currentTime;
                return;
            }
        }

        if (autoClose.getValue()) {
            boolean allSlotsFull = true;
            for (int i = 0; i < 9; i++) {
                if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
                    allSlotsFull = false;
                    break;
                }
            }

            if (allSlotsFull) {
                PacketUtil.send(new C0DPacketCloseWindow(mc.thePlayer.inventoryContainer.windowId));
            }
        }
    };

}
