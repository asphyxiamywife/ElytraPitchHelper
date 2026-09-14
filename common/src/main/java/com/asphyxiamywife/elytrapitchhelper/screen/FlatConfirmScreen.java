package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatButton;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class FlatConfirmScreen extends ConfirmScreen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_BUTTON_WIDTH = 80;
    private static final int MAX_BUTTON_WIDTH = 150;
    private static final int LABEL_PADDING = 24;

    FlatConfirmScreen(BooleanConsumer callback, Component title, Component message,
            Component confirmLabel, Component cancelLabel) {
        super(callback, title, message, confirmLabel, cancelLabel);
    }

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        yesButton = buttonLayout.addChild(FlatButton.of(yesButtonComponent, 0, 0,
                buttonWidth(yesButtonComponent), BUTTON_HEIGHT,
                button -> callback.accept(true)).quiet());
        noButton = buttonLayout.addChild(FlatButton.of(noButtonComponent, 0, 0,
                buttonWidth(noButtonComponent), BUTTON_HEIGHT, button -> callback.accept(false)));
    }

    private int buttonWidth(Component label) {
        return MathUtil.clamp(font.width(label) + LABEL_PADDING, MIN_BUTTON_WIDTH, MAX_BUTTON_WIDTH);
    }
}
