package com.alan.clients.script.api;

import com.alan.clients.util.mc.MCHooks;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GameSettingsAPI extends API {

    public GameSettingsAPI() {
    }

    public boolean isKeyBindForwardDown() {
        return MC.gameSettings.keyBindForward.isKeyDown();
    }

    public boolean isKeyBindBackDown() {
        return MC.gameSettings.keyBindBack.isKeyDown();
    }

    public boolean isKeyBindLeftDown() {
        return MC.gameSettings.keyBindLeft.isKeyDown();
    }

    public boolean isKeyBindRightDown() {
        return MC.gameSettings.keyBindRight.isKeyDown();
    }

    public boolean isKeyBindJumpDown() {
        return MC.gameSettings.keyBindJump.isKeyDown();
    }

    public boolean isKeyBindSneakDown() {
        return MC.gameSettings.keyBindSneak.isKeyDown();
    }

    public boolean isKeyBindAttackDown() {
        return MC.gameSettings.keyBindAttack.isKeyDown();
    }

    public boolean isKeyBindUseItemDown() {
        return MC.gameSettings.keyBindUseItem.isKeyDown();
    }

    public boolean isKeyBindDropDown() {
        return MC.gameSettings.keyBindDrop.isKeyDown();
    }

    public boolean isKeyBindInventoryDown() {
        return MC.gameSettings.keyBindInventory.isKeyDown();
    }

    public boolean isKeyBindChatDown() {
        return MC.gameSettings.keyBindChat.isKeyDown();
    }

    public boolean isKeyBindPlayerListDown() {
        return MC.gameSettings.keyBindPlayerList.isKeyDown();
    }

    public boolean isKeyBindCommandDown() {
        return MC.gameSettings.keyBindCommand.isKeyDown();
    }

    public boolean isKeyBindScreenshotDown() {
        return MC.gameSettings.keyBindScreenshot.isKeyDown();
    }

    public boolean isKeyBindTogglePerspectiveDown() {
        return MC.gameSettings.keyBindTogglePerspective.isKeyDown();
    }

    public boolean isKeyBindSmoothCameraDown() {
        return MC.gameSettings.keyBindSmoothCamera.isKeyDown();
    }

    public boolean isKeyBindFullscreenDown() {
        return MC.gameSettings.keyBindFullscreen.isKeyDown();
    }

    public boolean isKeyBindSpectatorOutlinesDown() {
        return MC.gameSettings.keyBindSpectatorOutlines.isKeyDown();
    }

    public void setKeyBindForward(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindForward, pressed);
    }

    public void setKeyBindBack(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindBack, pressed);
    }

    public void setKeyBindLeft(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindLeft, pressed);
    }

    public void setKeyBindRight(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindRight, pressed);
    }

    public void setKeyBindJump(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindJump, pressed);
    }

    public void setKeyBindSneak(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindSneak, pressed);
    }

    public void setKeyBindAttack(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindAttack, pressed);
    }

    public void setKeyBindUseItem(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindUseItem, pressed);
    }

    public void setKeyBindDrop(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindDrop, pressed);
    }

    public void setKeyBindInventory(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindInventory, pressed);
    }

    public void setKeyBindChat(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindChat, pressed);
    }

    public void setKeyBindPlayerList(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindPlayerList, pressed);
    }

    public void setKeyBindCommand(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindCommand, pressed);
    }

    public void setKeyBindScreenshot(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindScreenshot, pressed);
    }

    public void setKeyBindTogglePerspective(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindTogglePerspective, pressed);
    }

    public void setKeyBindSmoothCamera(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindSmoothCamera, pressed);
    }

    public void setKeyBindFullscreen(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindFullscreen, pressed);
    }

    public void setKeyBindSpectatorOutlines(boolean pressed) {
        MCHooks.setPressed(MC.gameSettings.keyBindSpectatorOutlines, pressed);
    }
    public boolean isKeyDown(int key) {
        return Keyboard.isKeyDown(key);
    }

    public boolean isMouseDown(int button) {
        return Mouse.isButtonDown(button);
    }
}