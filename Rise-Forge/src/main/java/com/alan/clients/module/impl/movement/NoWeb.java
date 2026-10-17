package com.alan.clients.module.impl.movement;

import com.alan.clients.module.Module;
import com.alan.clients.module.api.Category;
import com.alan.clients.module.api.ModuleInfo;
import com.alan.clients.module.impl.movement.noweb.IntaveNoWeb;
import com.alan.clients.module.impl.movement.noweb.VanillaNoWeb;
import com.alan.clients.module.impl.movement.noweb.VulcanNoWeb;
import com.alan.clients.value.Mode;
import com.alan.clients.value.impl.ModeValue;
import net.minecraft.util.BlockPos;

@ModuleInfo(aliases = {"module.movement.noweb.name"}, description = "module.movement.noweb.description", category = Category.MOVEMENT)
public class NoWeb extends Module {

    private final ModeValue mode = new ModeValue("Mode", this)
            .add(new VanillaNoWeb("Vanilla", this))
            .add(new VulcanNoWeb("Vulcan", this))
            .add(new IntaveNoWeb("IntaveOld", this))
            .setDefault("Vanilla");

    public boolean shouldCancelWeb() {
        return this.isEnabled() && mode.getValue() instanceof VanillaNoWeb;
    }

    public boolean handleWebCollision(BlockPos pos) {
        if (!this.isEnabled()) {
            return false;
        }

        final Mode<?> currentMode = mode.getValue();

        if (currentMode instanceof VanillaNoWeb) {
            return true;
        }

        if (currentMode instanceof VulcanNoWeb) {
            return ((VulcanNoWeb) currentMode).handleEntityCollision(pos);
        }

        if (currentMode instanceof IntaveNoWeb) {
            return ((IntaveNoWeb) currentMode).handleEntityCollision();
        }

        return false;
    }
}
