package com.alan.clients.util.mc;

import com.alan.clients.util.time.StopWatch;
import net.minecraft.entity.Entity;

/**
 * State Rise kept as extra fields on vanilla entity classes. A mixin attaches one instance to every entity;
 * see {@code MixinEntity} and the other entity mixins for where each value is maintained.
 */
public final class EntityData {

    public int offGroundTicks, onGroundTicks;
    public int ticksSinceVelocity, ticksSincePlayerVelocity, ticksSinceTeleport, ticksSincePlace, ticksSinceAttack;
    public int ticksSinceStep, ticksSinceJump, ticksSprint, ticksVisible;
    public int lastHurtTime;

    public boolean safeWalk, moved, omniSprint;

    public float movementYaw, velocityYaw, lastMovementYaw;
    public float renderPitchHead, prevRenderPitchHead;

    public double lastGroundX, lastGroundY, lastGroundZ, lastLastGroundX, lastLastGroundY, lastLastGroundZ;
    public double lastLastTickPosX, lastLastTickPosY, lastLastTickPosZ;
    public double lastMotionX, lastMotionZ;
    public double lastVelocityDeltaX, lastVelocityDeltaY, lastVelocityDeltaZ;
    public double threadDistance;

    // Only meaningful on the local player's inventory (Rise kept these on InventoryPlayer)
    public int alternativeCurrentItem;
    public boolean alternativeSlot;

    private StopWatch hideSneakHeight;

    public StopWatch hideSneakHeight() {
        if (hideSneakHeight == null) hideSneakHeight = new StopWatch();
        return hideSneakHeight;
    }

    /** Hotbar index of the item the local player is treated as holding (Rise's silent slot). */
    public static int heldSlot(final net.minecraft.entity.player.InventoryPlayer inventory) {
        if (inventory.player == null || inventory.player != net.minecraft.client.Minecraft.getMinecraft().thePlayer) {
            return inventory.currentItem;
        }

        final EntityData data = of(inventory.player);
        return data.alternativeSlot ? data.alternativeCurrentItem : inventory.currentItem;
    }

    public static EntityData of(final Entity entity) {
        return ((Holder) entity).rise$getData();
    }

    public interface Holder {
        EntityData rise$getData();
    }
}
