package com.alan.clients.util.mc;

import net.minecraft.item.ItemStack;

/**
 * Rise's predicted block count for scaffolding: decremented on every client-side placement and re-synced to the real
 * stack size once the stack hasn't changed for a second. Stored on the stack by {@code MixinItemStack}.
 */
public final class ItemStackData {

    private ItemStackData() {
    }

    public static int realStackSize(final ItemStack stack) {
        return ((Holder) (Object) stack).rise$getRealStackSize();
    }

    public interface Holder {
        int rise$getRealStackSize();

        void rise$setRealStackSize(int size);

        void rise$markModified();
    }
}
