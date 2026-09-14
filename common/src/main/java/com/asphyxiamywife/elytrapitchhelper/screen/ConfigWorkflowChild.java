package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.screens.Screen;

interface ConfigWorkflowChild {
    Screen configWorkflowParent();

    static boolean belongsTo(Screen screen, Object owner) {
        while (screen != null) {
            if (screen == owner) {
                return true;
            }
            if (!(screen instanceof ConfigWorkflowChild child)) {
                return false;
            }
            screen = child.configWorkflowParent();
        }
        return false;
    }
}
