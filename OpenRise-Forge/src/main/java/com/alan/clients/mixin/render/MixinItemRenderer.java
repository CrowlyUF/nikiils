package com.alan.clients.mixin.render;

import com.alan.clients.event.impl.render.RenderItemEvent;
import com.alan.clients.util.mc.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

    @Shadow @Final private Minecraft mc;
    @Shadow private ItemStack itemToRender;
    @Shadow private float equippedProgress;
    @Shadow private float prevEquippedProgress;

    @Shadow protected abstract void rotateArroundXAndY(float angle, float angleY);

    @Shadow protected abstract void setLightMapFromPlayer(AbstractClientPlayer clientPlayer);

    @Shadow protected abstract void rotateWithPlayerRotations(EntityPlayerSP entityplayerspIn, float partialTicks);

    @Shadow protected abstract void renderItemMap(AbstractClientPlayer clientPlayer, float pitch, float equipmentProgress, float swingProgress);

    @Shadow protected abstract void transformFirstPersonItem(float equipProgress, float swingProgress);

    @Shadow protected abstract void performDrinking(AbstractClientPlayer clientPlayer, float partialTicks);

    @Shadow protected abstract void doBlockTransformations();

    @Shadow protected abstract void doBowTransformations(float partialTicks, AbstractClientPlayer clientPlayer);

    @Shadow protected abstract void doItemUsedTransformations(float swingProgress);

    @Shadow protected abstract void renderPlayerArm(AbstractClientPlayer clientPlayer, float equipProgress, float swingProgress);

    @Shadow public abstract void renderItem(EntityLivingBase entityIn, ItemStack heldStack, ItemCameraTransforms.TransformType transform);

    /**
     * @author Rise
     * @reason The held item's use action, swing and equip progress go through RenderItemEvent so the Animations module
     * can draw 1.7-style block hits and custom swings (a cancelled event skips the vanilla transform).
     */
    @Overwrite
    public void renderItemInFirstPerson(final float partialTicks) {
        float equipProgress = 1.0F - (this.prevEquippedProgress + (this.equippedProgress - this.prevEquippedProgress) * partialTicks);
        final EntityPlayerSP player = this.mc.thePlayer;
        float swingProgress = player.getSwingProgress(partialTicks);
        final float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
        final float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        this.rotateArroundXAndY(pitch, yaw);
        this.setLightMapFromPlayer(player);
        this.rotateWithPlayerRotations(player, partialTicks);
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();

        if (this.itemToRender != null) {
            final RenderItemEvent event = Events.post(new RenderItemEvent(this.itemToRender.getItemUseAction(), player.getItemInUseCount() > 0,
                    equipProgress, partialTicks, swingProgress, this.itemToRender));
            final EnumAction action = event.getEnumAction();
            equipProgress = event.getAnimationProgression();
            swingProgress = event.getSwingProgress();

            if (this.itemToRender.getItem() instanceof ItemMap) {
                this.renderItemMap(player, pitch, equipProgress, swingProgress);
            } else if (event.isUseItem()) {
                if (!event.isCancelled()) {
                    switch (action) {
                        case NONE:
                            this.transformFirstPersonItem(equipProgress, 0.0F);
                            break;
                        case EAT:
                        case DRINK:
                            this.performDrinking(player, partialTicks);
                            this.transformFirstPersonItem(equipProgress, 0.0F);
                            break;
                        case BLOCK:
                            this.transformFirstPersonItem(equipProgress, 0.0F);
                            this.doBlockTransformations();
                            break;
                        case BOW:
                            this.transformFirstPersonItem(equipProgress, 0.0F);
                            this.doBowTransformations(partialTicks, player);
                    }
                }
            } else if (!event.isCancelled()) {
                this.doItemUsedTransformations(swingProgress);
                this.transformFirstPersonItem(equipProgress, swingProgress);
            }

            this.renderItem(player, this.itemToRender, ItemCameraTransforms.TransformType.FIRST_PERSON);
        } else if (!player.isInvisible()) {
            this.renderPlayerArm(player, equipProgress, swingProgress);
        }

        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
    }
}
