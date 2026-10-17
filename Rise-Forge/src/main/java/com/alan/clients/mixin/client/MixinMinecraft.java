package com.alan.clients.mixin.client;

import com.alan.clients.Client;
import com.alan.clients.event.impl.input.ClickEvent;
import com.alan.clients.event.impl.input.KeyboardInputEvent;
import com.alan.clients.event.impl.input.RightClickEvent;
import com.alan.clients.event.impl.other.AttackEvent;
import com.alan.clients.event.impl.other.GameEvent;
import com.alan.clients.event.impl.other.TickEvent;
import com.alan.clients.event.impl.other.WorldChangeEvent;
import com.alan.clients.event.impl.render.FramebufferUpdateEvent;
import com.alan.clients.module.impl.render.FreeLook;
import com.alan.clients.util.mc.EntityData;
import com.alan.clients.util.mc.Events;
import com.alan.clients.util.mc.MCHooks;
import com.alan.clients.util.time.StopWatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow public EntityPlayerSP thePlayer;
    @Shadow public WorldClient theWorld;
    @Shadow public GuiScreen currentScreen;
    @Shadow public MovingObjectPosition objectMouseOver;
    @Shadow public int displayWidth;
    @Shadow public int displayHeight;
    @Shadow private int leftClickCounter;
    @Shadow private Timer timer;

    @Shadow public abstract void runTick() throws IOException;

    @Unique private final StopWatch rise$gameEventWatch = new StopWatch();
    @Unique private FreeLook rise$freeLook;

    /* -------------------------------------------------------------- lifecycle */

    @Inject(method = "startGame", at = @At("TAIL"))
    private void rise$startClient(final CallbackInfo ci) {
        MCHooks.markStart();
        Client.INSTANCE.init();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void rise$stopClient(final CallbackInfo ci) {
        Client.INSTANCE.terminate();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("TAIL"))
    private void rise$worldChange(final WorldClient world, final String loadingMessage, final CallbackInfo ci) {
        Events.post(new WorldChangeEvent());
    }

    @Inject(method = "updateFramebufferSize", at = @At("TAIL"))
    private void rise$framebufferResize(final CallbackInfo ci) {
        Events.post(new FramebufferUpdateEvent(this.displayWidth, this.displayHeight));
    }

    /* -------------------------------------------------------------- ticking */

    /**
     * Every game tick goes through TickEvent; a cancelled event skips the tick (used for tick-base / timer tricks).
     * Rise resets the timer speed per tick so the Timer module re-applies its speed from its own listener.
     */
    @Redirect(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick()V"))
    private void rise$tick(final Minecraft minecraft) throws IOException {
        this.timer.timerSpeed = 1.0F;

        if (!Events.post(new TickEvent()).isCancelled()) {
            this.runTick();
        }
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void rise$gameEvent(final CallbackInfo ci) {
        if (rise$gameEventWatch.finished(5000)) {
            rise$gameEventWatch.reset();
            Events.post(new GameEvent());
            // the OptiFine menu can switch Fast Render back on; Rise's framebuffer effects break with it
            MCHooks.setOptiFineOption("ofFastRender", false);
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void rise$tickStart(final CallbackInfo ci) {
        if (this.thePlayer != null) {
            final EntityData data = EntityData.of(this.thePlayer);
            data.lastMotionX = this.thePlayer.motionX;
            data.lastMotionZ = this.thePlayer.motionZ;
            data.lastMovementYaw = data.movementYaw;
            data.movementYaw = data.velocityYaw = this.thePlayer.rotationYaw;
        }
    }

    /* -------------------------------------------------------------- input */

    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    private void rise$leftClick(final CallbackInfo ci) {
        if (this.leftClickCounter > 0) return;

        if (Events.post(new ClickEvent()).isCancelled()) {
            ci.cancel();
            return;
        }

        if (this.objectMouseOver != null && this.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && this.objectMouseOver.entityHit instanceof EntityLivingBase) {
            if (Events.post(new AttackEvent((EntityLivingBase) this.objectMouseOver.entityHit)).isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    private void rise$rightClick(final CallbackInfo ci) {
        if (Events.post(new RightClickEvent()).isCancelled()) {
            ci.cancel();
        }
    }

    /** The emptied-stack cleanup must clear the silently selected slot, not the visible one. */
    @Redirect(method = "rightClickMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.GETFIELD))
    private int rise$rightClickSlot(final InventoryPlayer inventory) {
        return EntityData.heldSlot(inventory);
    }

    /** Fires KeyboardInputEvent for key presses; cancelling it suppresses vanilla's handling of that key. */
    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;getEventKeyState()Z", ordinal = 2, remap = false))
    private boolean rise$keyPress() {
        final boolean pressed = Keyboard.getEventKeyState();

        if (pressed) {
            final int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();

            if (Events.post(new KeyboardInputEvent(key, Keyboard.getEventCharacter(), this.currentScreen)).isCancelled()) {
                return false;
            }
        }

        return pressed;
    }

    /** FreeLook keeps the perspective it set; toggling it meanwhile makes the camera flicker. */
    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = Opcodes.PUTFIELD))
    private void rise$lockPerspective(final GameSettings settings, final int value) {
        if (rise$freeLook == null && Client.INSTANCE.getModuleManager() != null) {
            rise$freeLook = Client.INSTANCE.getModuleManager().get(FreeLook.class);
        }

        if (rise$freeLook == null || !rise$freeLook.isEnabled()) {
            settings.thirdPersonView = value;
        }
    }
}
