package com.alan.clients.module.impl.render;

import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.module.impl.render.esp.ChamsESP;
import com.alan.clients.module.impl.render.esp.GlowESP;
import com.alan.clients.module.impl.render.esp.SkeletalESP;
import com.alan.clients.util.render.EntityColors;
import com.alan.clients.value.impl.BooleanValue;
import com.alan.clients.value.impl.ColorValue;
import com.alan.clients.value.impl.ModeValue;
import com.alan.clients.value.impl.NumberValue;

import java.awt.*;

@ModuleInfo(aliases = {"module.render.esp.name"}, description = "module.render.esp.description", category = Category.RENDER)
public final class ESP extends Module {

    public final ModeValue colorMode = EntityColors.colorMode(new ModeValue("Color Mode", this));
    public final ColorValue customColor = new ColorValue("Custom Color", this, new Color(120, 90, 255),
            () -> !colorMode.getValue().getName().equals(EntityColors.CUSTOM));
    public final NumberValue maxDistance = new NumberValue("Distance Range", this, 50, 5, 256, 1,
            () -> !colorMode.getValue().getName().equals(EntityColors.DISTANCE));

    private final BooleanValue glowESP = new BooleanValue("Glow", this, false, new GlowESP("", this));
    private final BooleanValue chamsESP = new BooleanValue("Chams", this, false, new ChamsESP("", this));
    public final BooleanValue staticColorChamsESP = new BooleanValue("Static Color", this, false, () -> !chamsESP.getValue());
    private final BooleanValue skeletalESPValue = new BooleanValue("Skeletal", this, false, new SkeletalESP("",this));
    public final BooleanValue whiteColor = new BooleanValue("White Color", this, false, () -> !skeletalESPValue.getValue());
    public final NumberValue skeletalWidth = new NumberValue("Width", this, 0.5, 0.1, 1.5, 0.1);
}
