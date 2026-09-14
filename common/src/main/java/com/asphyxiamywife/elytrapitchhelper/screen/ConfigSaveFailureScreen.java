package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ConfigSaveFailureScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private final Runnable retry;
    private final Runnable stay;
    private final Runnable leave;
    private final Component detail;

    ConfigSaveFailureScreen(Runnable retry, Runnable stay, Runnable leave, Component detail) {
        super(Component.translatable("screen.elytrapitchhelper.save_failure.title"));
        this.retry = retry;
        this.stay = stay;
        this.leave = leave;
        this.detail = detail;
    }

    @Override
    protected void init() {
        int contentWidth = Math.max(180, width - 40);
        Component message = Component.translatable("screen.elytrapitchhelper.save_failure.message");
        if (detail != null) {
            message = Component.empty().append(message).append("\n\n").append(detail);
        }
        addRenderableWidget(new FlatTextArea(
                20, 44, contentWidth, Math.max(40, height - 116), message, font));

        ButtonRow row = buttonRow(width);
        int buttonWidth = row.buttonWidth();
        int x = row.x();
        int y = height - 34;
        addRenderableWidget(FlatButton.of(
                Component.translatable("screen.elytrapitchhelper.save_failure.retry"),
                x, y, buttonWidth, BUTTON_HEIGHT, ignored -> retry.run()));
        addRenderableWidget(FlatButton.of(
                Component.translatable("screen.elytrapitchhelper.save_failure.stay"),
                x + buttonWidth + BUTTON_GAP, y, buttonWidth, BUTTON_HEIGHT, ignored -> stay.run()));
        addRenderableWidget(FlatButton.of(
                Component.translatable("screen.elytrapitchhelper.save_failure.discard"),
                x + (buttonWidth + BUTTON_GAP) * 2, y, buttonWidth, BUTTON_HEIGHT,
                ignored -> leave.run()).quiet());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(font, title, width / 2, 18, 0xFFFF5555);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        stay.run();
    }

    static ButtonRow buttonRow(int width) {
        int buttonWidth = Math.min(MathUtil.clamp((width - 40 - BUTTON_GAP * 2) / 3, 72, 120),
                Math.max(24, (width - BUTTON_GAP * 2) / 3));
        int totalWidth = buttonWidth * 3 + BUTTON_GAP * 2;
        return new ButtonRow(Math.max(0, (width - totalWidth) / 2), buttonWidth, BUTTON_GAP);
    }

    record ButtonRow(int x, int buttonWidth, int gap) {
        int right() {
            return x + buttonWidth * 3 + gap * 2;
        }
    }
}
