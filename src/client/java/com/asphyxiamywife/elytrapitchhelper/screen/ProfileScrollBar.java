package com.asphyxiamywife.elytrapitchhelper.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

final class ProfileScrollBar extends AbstractScrollArea {
    private final int profileCount;

    ProfileScrollBar(int x, int y, int width, int height, int profileCount, int scrollRow) {
        super(x, y, width, height, Component.translatable("screen.elytrapitchhelper.profile.scrollbar"),
                AbstractScrollArea.defaultSettings(ConfigScreen.ROW_HEIGHT));
        this.profileCount = profileCount;
        setScrollRow(scrollRow);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractScrollbar(context, mouseX, mouseY);
    }

    @Override
    protected int contentHeight() {
        return profileCount * ConfigScreen.ROW_HEIGHT;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private void setScrollRow(int scrollRow) {
        super.setScrollAmount(scrollRow * ConfigScreen.ROW_HEIGHT);
    }
}
