package com.alan.clients.mixin.misc;

import com.alan.clients.event.impl.input.MoveInputEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MovementInputFromOptions.class)
public abstract class MixinMovementInputFromOptions extends MovementInput {

    @Shadow @Final private GameSettings gameSettings;

    /**
     * @author Rise
     * @reason MoveInputEvent can rewrite the movement keys and the sneak slowdown (InvMove, Scaffold, MoveFix).
     */
    @Overwrite
    public void updatePlayerMoveState() {
        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;

        if (this.gameSettings.keyBindForward.isKeyDown()) ++this.moveForward;
        if (this.gameSettings.keyBindBack.isKeyDown()) --this.moveForward;
        if (this.gameSettings.keyBindLeft.isKeyDown()) ++this.moveStrafe;
        if (this.gameSettings.keyBindRight.isKeyDown()) --this.moveStrafe;

        this.jump = this.gameSettings.keyBindJump.isKeyDown();
        this.sneak = this.gameSettings.keyBindSneak.isKeyDown();

        final MoveInputEvent event = Events.post(new MoveInputEvent(this.moveForward, this.moveStrafe, this.jump, this.sneak, 0.3D));
        final double sneakMultiplier = event.getSneakSlowDownMultiplier();
        this.moveForward = event.getForward();
        this.moveStrafe = event.getStrafe();
        this.jump = event.isJump();
        this.sneak = event.isSneak();

        if (this.sneak) {
            this.moveStrafe = (float) (this.moveStrafe * sneakMultiplier);
            this.moveForward = (float) (this.moveForward * sneakMultiplier);
        }
    }
}
