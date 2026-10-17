package com.alan.clients.util.mc;

import com.alan.clients.util.vector.Vector2f;
import com.alan.clients.util.vector.Vector3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Session;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.nio.FloatBuffer;

/**
 * Static replacements for the members Rise added directly to vanilla classes. Call sites in the client were rewritten
 * from {@code receiver.member(args)} to {@code MCHooks.member(receiver, args)}.
 */
public final class MCHooks {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /* ------------------------------------------------------------------ fields Rise added to vanilla classes */

    /** {@code GuiIngame#frame}: bumped once per rendered frame before the 2D pass. */
    public static int guiFrame;
    /** {@code GlStateManager#caching}: Rise's blend-state cache toggle. Not needed with the vanilla state manager. */
    public static boolean glCaching;
    /** {@code EntityRenderer#mouseAddedX/Y}: extra mouse delta consumed on the next camera update. */
    public static float mouseAddedX, mouseAddedY;
    /** {@code RendererLivingEntity#SHADER_RENDERING}: true while entities are drawn into a shader framebuffer. */
    public static boolean SHADER_RENDERING;
    /** {@code GuiMultiplayer#usingFinderList}. */
    public static boolean usingFinderList;

    private static long startMillis = System.currentTimeMillis();

    private static ScaledResolution scaledResolution;
    private static int srWidth = -1, srHeight = -1, srScale = -1;
    private static boolean srUnicode;

    private static final FloatBuffer SHADER_BRIGHTNESS = BufferUtils.createFloatBuffer(4);

    private MCHooks() {
    }

    public static void markStart() {
        startMillis = System.currentTimeMillis();
    }

    public static long startMillisTime() {
        return startMillis;
    }

    /**
     * Rise kept a {@code ScaledResolution} on {@code Minecraft} that was refreshed on resize. Building one is not free,
     * and the HUD asks for it many times per frame, so it's cached until the display or GUI scale changes.
     */
    public static ScaledResolution scaledResolution() {
        final int scale = mc.gameSettings.guiScale;
        final boolean unicode = mc.isUnicode();

        if (scaledResolution == null || srWidth != mc.displayWidth || srHeight != mc.displayHeight || srScale != scale || srUnicode != unicode) {
            scaledResolution = new ScaledResolution(mc);
            srWidth = mc.displayWidth;
            srHeight = mc.displayHeight;
            srScale = scale;
            srUnicode = unicode;
        }

        return scaledResolution;
    }

    /* ------------------------------------------------------------------ OptiFine (optional at runtime) */

    private static java.lang.reflect.Field fastRenderField;
    private static boolean fastRenderLookedUp;

    /** OptiFine adds its options as fields on GameSettings; absent without OptiFine. */
    public static boolean isOptiFineFastRender() {
        if (!fastRenderLookedUp) {
            fastRenderLookedUp = true;
            try {
                fastRenderField = net.minecraft.client.settings.GameSettings.class.getDeclaredField("ofFastRender");
                fastRenderField.setAccessible(true);
            } catch (final ReflectiveOperationException ignored) {
            }
        }

        try {
            return fastRenderField != null && fastRenderField.getBoolean(mc.gameSettings);
        } catch (final IllegalAccessException ignored) {
            return false;
        }
    }

    public static void setOptiFineOption(final String name, final Object value) {
        try {
            final java.lang.reflect.Field field = net.minecraft.client.settings.GameSettings.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(mc.gameSettings, value);
        } catch (final ReflectiveOperationException ignored) {
        }
    }

    /* ------------------------------------------------------------------ input */

    public static void setPressed(final KeyBinding keyBinding, final boolean pressed) {
        keyBinding.pressed = pressed;
    }

    /* ------------------------------------------------------------------ entities */

    public static Vector3d getCustomPositionVector(final Entity entity) {
        return new Vector3d(entity.posX, entity.posY, entity.posZ);
    }

    public static MovingObjectPosition rayTraceCustom(final Entity entity, final double blockReachDistance, final float yaw, final float pitch) {
        final Vec3 eyes = entity.getPositionEyes(1.0F);
        final Vec3 look = entity.getVectorForRotation(pitch, yaw);
        final Vec3 end = eyes.addVector(look.xCoord * blockReachDistance, look.yCoord * blockReachDistance, look.zCoord * blockReachDistance);
        return entity.worldObj.rayTraceBlocks(eyes, end, false, false, true);
    }

    public static Vector2f getPreviousRotation(final EntityPlayerSP player) {
        return new Vector2f(player.lastReportedYaw, player.lastReportedPitch);
    }

    /**
     * Rise's {@code Entity#hide()} / {@code hideNameTag()} toggled flags nothing in OpenRise ever read, so they're
     * no-ops here as well.
     */
    public static void hide(final Entity entity) {
    }

    public static void hideNameTag(final Entity entity) {
    }

    /* ------------------------------------------------------------------ math on vanilla types */

    public static boolean equalsVector(final BlockPos pos, final Vector3d vec) {
        return Math.floor(vec.getX()) == pos.getX() && Math.floor(vec.getY()) == pos.getY() && Math.floor(vec.getZ()) == pos.getZ();
    }

    public static double distance(final Vec3i from, final Vec3i to) {
        final double x = from.getX() - to.getX();
        final double y = from.getY() - to.getY();
        final double z = from.getZ() - to.getZ();
        return Math.sqrt(x * x + y * y + z * z);
    }

    public static double distance(final Vec3i from, final double x, final double y, final double z) {
        final double dx = from.getX() - x;
        final double dy = from.getY() - y;
        final double dz = from.getZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static void setBlockPos(final MovingObjectPosition objectPosition, final BlockPos pos) {
        objectPosition.blockPos = pos;
    }

    /* ------------------------------------------------------------------ client */

    public static void setSession(final Minecraft minecraft, final Session session) {
        minecraft.session = session;
    }

    public static boolean shouldResize(final Framebuffer framebuffer, final int width, final int height) {
        return width != framebuffer.framebufferWidth || height != framebuffer.framebufferHeight;
    }

    public static void bindTextureById(final TextureManager textureManager, final int id) {
        GlStateManager.bindTexture(id);
    }

    public static void renderBasicTileEntity(final TileEntityRendererDispatcher dispatcher, final TileEntity tileEntity, final float partialTicks) {
        if (tileEntity.getDistanceSq(dispatcher.entityX, dispatcher.entityY, dispatcher.entityZ) < tileEntity.getMaxRenderDistanceSquared()) {
            GlStateManager.disableAlpha();
            GlStateManager.disableBlend();

            final BlockPos pos = tileEntity.getPos();

            if (!dispatcher.worldObj.isBlockLoaded(pos, false)) {
                return;
            }

            dispatcher.renderTileEntityAt(tileEntity, pos.getX() - TileEntityRendererDispatcher.staticPlayerX,
                    pos.getY() - TileEntityRendererDispatcher.staticPlayerY, pos.getZ() - TileEntityRendererDispatcher.staticPlayerZ, partialTicks, -1);
        }
    }

    /* ------------------------------------------------------------------ networking */

    public static void addToReceiveQueue(final NetHandlerPlayClient handler, final Packet<?> packet) {
        NetworkHooks.receive(handler.getNetworkManager(), packet, true);
    }

    public static void addToReceiveQueueUnregistered(final NetHandlerPlayClient handler, final Packet<?> packet) {
        NetworkHooks.receive(handler.getNetworkManager(), packet, false);
    }

    public static void addToSendQueueUnregistered(final NetHandlerPlayClient handler, final Packet<?> packet) {
        NetworkHooks.sendNoEvent(handler.getNetworkManager(), packet);
    }

    public static void sendUnregisteredPacket(final NetworkManager networkManager, final Packet<?> packet) {
        NetworkHooks.sendNoEvent(networkManager, packet);
    }

    /* ------------------------------------------------------------------ rendering */

    public static final ResourceLocation SKELETON_TEXTURE = new ResourceLocation("textures/entity/skeleton/skeleton.png");

    /** Rise's float-precision version of {@code Gui#drawModalRectWithCustomSizedTexture}. */
    public static void drawModalRectWithCustomSizedTexture(final float x, final float y, final float u, final float v, final float width, final float height, final float textureWidth, final float textureHeight) {
        final float f = 1.0F / textureWidth;
        final float f1 = 1.0F / textureHeight;
        final Tessellator tessellator = Tessellator.getInstance();
        final WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(x, y + height, 0.0D).tex(u * f, (v + height) * f1).endVertex();
        worldrenderer.pos(x + width, y + height, 0.0D).tex((u + width) * f, (v + height) * f1).endVertex();
        worldrenderer.pos(x + width, y, 0.0D).tex((u + width) * f, v * f1).endVertex();
        worldrenderer.pos(x, y, 0.0D).tex(u * f, v * f1).endVertex();
        tessellator.draw();
    }

    /** Rise's double-precision version of {@code Gui#drawScaledCustomSizeModalRect}. */
    public static void drawScaledCustomSizeModalRect(final double x, final double y, final float u, final float v, final float uWidth, final float vHeight, final double width, final double height, final float tileWidth, final float tileHeight) {
        final float f = 1.0F / tileWidth;
        final float f1 = 1.0F / tileHeight;
        final Tessellator tessellator = Tessellator.getInstance();
        final WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(x, y + height, 0.0D).tex(u * f, (v + vHeight) * f1).endVertex();
        worldrenderer.pos(x + width, y + height, 0.0D).tex((u + uWidth) * f, (v + vHeight) * f1).endVertex();
        worldrenderer.pos(x + width, y, 0.0D).tex((u + uWidth) * f, v * f1).endVertex();
        worldrenderer.pos(x, y, 0.0D).tex(u * f, v * f1).endVertex();
        tessellator.draw();
    }

    /**
     * Rise's {@code RenderItem#renderItemAndEffectIntoGUI(stack, color, x, y)}: sub-pixel positioned GUI item, tinted
     * with {@code color} unless it is -1.
     */
    public static void renderItemAndEffectIntoGUI(final RenderItem renderItem, final ItemStack stack, final int color, final double x, final double y) {
        if (stack == null || stack.getItem() == null) return;

        final int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x - ix, y - iy, 0);

        if (color == -1) {
            renderItem.renderItemAndEffectIntoGUI(stack, ix, iy);
            GlStateManager.popMatrix();
            return;
        }

        renderItem.zLevel += 50.0F;
        IBakedModel model = renderItem.getItemModelMesher().getItemModel(stack);
        final TextureManager textureManager = mc.getTextureManager();
        textureManager.bindTexture(TextureMap.locationBlocksTexture);
        textureManager.getTexture(TextureMap.locationBlocksTexture).setBlurMipmap(false, false);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.0F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        renderItem.setupGuiTransform(ix, iy, model.isGui3d());
        model = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GUI);

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        renderItem.renderModel(model, color);
        GlStateManager.popMatrix();

        GlStateManager.disableAlpha();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        textureManager.bindTexture(TextureMap.locationBlocksTexture);
        textureManager.getTexture(TextureMap.locationBlocksTexture).restoreLastBlurMipmap();
        renderItem.zLevel -= 50.0F;
        GlStateManager.popMatrix();
    }

    /**
     * Copy of Rise's {@code RendererLivingEntity#setShaderBrightness}: tints the next entity draw with a flat color
     * through the texture combiner, the same way vanilla applies the red hurt overlay.
     */
    public static boolean setShaderBrightness(final Color color) {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, OpenGlHelper.GL_INTERPOLATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, OpenGlHelper.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE2_RGB, OpenGlHelper.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND2_RGB, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);

        SHADER_BRIGHTNESS.position(0);
        SHADER_BRIGHTNESS.put(color.getRed() / 255.0F);
        SHADER_BRIGHTNESS.put(color.getGreen() / 255.0F);
        SHADER_BRIGHTNESS.put(color.getBlue() / 255.0F);
        SHADER_BRIGHTNESS.put(1.0F);
        SHADER_BRIGHTNESS.flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, SHADER_BRIGHTNESS);

        GlStateManager.setActiveTexture(OpenGlHelper.GL_TEXTURE2);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(RendererLivingEntity.textureBrightness.getGlTextureId());
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.lightmapTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        return true;
    }

    /** Vanilla {@code RendererLivingEntity#unsetBrightness}, made static. */
    public static void unsetShaderBrightness() {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_ALPHA, OpenGlHelper.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GlStateManager.setActiveTexture(OpenGlHelper.GL_TEXTURE2);
        GlStateManager.disableTexture2D();
        GlStateManager.bindTexture(0);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, OpenGlHelper.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE1_RGB, OpenGlHelper.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, OpenGlHelper.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
