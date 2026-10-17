package com.alan.clients.mixin.render;

import com.alan.clients.Client;
import com.alan.clients.module.impl.render.ItemPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * ItemPhysics: dropped items lie flat and tumble while falling (Rise's renderer, unchanged).
 */
@Mixin(RenderEntityItem.class)
public abstract class MixinRenderEntityItem extends Render<EntityItem> {

    @Unique private static final Random rise$random = new Random();
    @Unique private static double rise$rotation;
    @Unique private ItemPhysics rise$itemPhysics;

    @Shadow protected abstract int func_177078_a(ItemStack stack);

    protected MixinRenderEntityItem(final RenderManager renderManager) {
        super(renderManager);
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void rise$itemPhysics(final EntityItem entity, final double x, final double y, final double z, final float entityYaw, final float partialTicks, final CallbackInfo ci) {
        if (rise$itemPhysics == null && Client.INSTANCE.getModuleManager() != null) {
            rise$itemPhysics = Client.INSTANCE.getModuleManager().get(ItemPhysics.class);
        }

        if (rise$itemPhysics == null || !rise$itemPhysics.isEnabled()) return;

        ci.cancel();

        final Minecraft mc = Minecraft.getMinecraft();

        if (!entity.onGround) {
            rise$rotation *= 1.005F;
            entity.rotationPitch += rise$rotation;
        }

        rise$rotation = mc.inGameHasFocus ? 2 : 0;

        final ItemStack itemstack = entity.getEntityItem();
        final int seed = itemstack != null && itemstack.getItem() != null ? Item.getIdFromItem(itemstack.getItem()) + itemstack.getMetadata() : 187;
        rise$random.setSeed(seed);

        final ResourceLocation texture = this.getEntityTexture(entity);
        mc.getTextureManager().bindTexture(texture);
        mc.getTextureManager().getTexture(texture).setBlurMipmap(false, false);

        GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.pushMatrix();

        final IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(itemstack);
        final boolean is3D = model.isGui3d();
        final int copies = this.func_177078_a(itemstack);

        GlStateManager.translate((float) x, (float) y, (float) z);

        if (is3D) GlStateManager.scale(0.5F, 0.5F, 0.5F);

        GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(entity.rotationYaw, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(0, 0, is3D ? -0.08 : -0.04);

        if (is3D || mc.getRenderManager().options != null) {
            if (is3D) {
                if (!entity.onGround) {
                    entity.rotationPitch += rise$rotation;
                }
            } else if (!Double.isNaN(entity.posX) && !Double.isNaN(entity.posY) && !Double.isNaN(entity.posZ) && entity.worldObj != null) {
                if (entity.onGround) {
                    entity.rotationPitch = 0;
                } else {
                    entity.rotationPitch += rise$rotation;
                }
            }

            GlStateManager.rotate(entity.rotationPitch, 1, 0, 0.0F);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (int k = 0; k < copies; k++) {
            GlStateManager.pushMatrix();

            if (is3D) {
                if (k > 0) {
                    final float ox = (rise$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    final float oy = (rise$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    final float oz = (rise$random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    GlStateManager.translate(ox, oy, oz);
                }

                mc.getRenderItem().renderItem(itemstack, model);
                GlStateManager.popMatrix();
            } else {
                mc.getRenderItem().renderItem(itemstack, model);
                GlStateManager.popMatrix();
                GlStateManager.translate(0.0F, 0.0F, 0.05375F);
            }
        }

        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        mc.getTextureManager().bindTexture(texture);
        mc.getTextureManager().getTexture(texture).restoreLastBlurMipmap();

        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }
}
