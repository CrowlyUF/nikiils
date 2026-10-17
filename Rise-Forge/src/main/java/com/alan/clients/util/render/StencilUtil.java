package com.alan.clients.util.render;

import com.alan.clients.util.Accessor;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL30;

import static org.lwjgl.opengl.GL11.*;
public class StencilUtil implements Accessor {

    /**
     * Rise swapped the framebuffer's depth buffer for an unsized GL_DEPTH_STENCIL renderbuffer sized to the window.
     * Drivers that reject the unsized format, or framebuffers padded to a power of two, ended up with no depth buffer
     * and a black world. Forge's stencil support allocates a proper DEPTH24_STENCIL8 attachment at the right size.
     */
    public static void checkSetupFBO(final Framebuffer framebuffer) {
        if (framebuffer != null && !framebuffer.isStencilEnabled()) {
            framebuffer.enableStencil();
        }
    }

    public static void initStencil() {
        initStencil(mc.getFramebuffer());
    }

    public static void initStencil(final Framebuffer framebuffer) {
        framebuffer.bindFramebuffer(false);
        checkSetupFBO(framebuffer);
        glClear(GL_STENCIL_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_STENCIL_TEST);
    }

    public static void bindWriteStencilBuffer() {
        glStencilFunc(GL_ALWAYS, 1, 1);
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
        glColorMask(false, false, false, false);
    }

    public static void bindReadStencilBuffer(final int ref) {
        glColorMask(true, true, true, true);
        glStencilFunc(GL_EQUAL, ref, 1);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }

    public static void uninitStencilBuffer() {
        glDisable(GL_STENCIL_TEST);
    }
}