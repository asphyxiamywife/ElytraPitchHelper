package com.asphyxiamywife.elytrapitchhelper.fabric;

import com.asphyxiamywife.elytrapitchhelper.hud.PitchGuideHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FabricHudElement implements HudElement {
    private final PitchGuideHud hud;

    public FabricHudElement(PitchGuideHud hud) {
        this.hud = hud;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics) {
        hud.render(graphics, graphics.deltaTracker());
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        hud.render(graphics, deltaTracker);
    }
}
