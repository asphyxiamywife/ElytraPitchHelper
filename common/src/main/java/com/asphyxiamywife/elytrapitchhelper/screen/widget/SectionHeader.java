package com.asphyxiamywife.elytrapitchhelper.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class SectionHeader extends AbstractWidget {
    private static final int LABEL_COLOR = 0xFFDCDCDC;
    private static final int SUMMARY_COLOR = 0xFFB0B0B0;
    private static final int RULE_COLOR = 0x50FFFFFF;
    private static final int HOVER_RULE_COLOR = 0x90FFFFFF;
    private static final int ARROW_WIDTH = 9;
    private static final int REVEALED_ARROW_COLOR = 0xFF808080;

    private final Font font;
    private final Component label;
    private final Component boldLabel;
    private final Runnable onToggle;
    private boolean collapsed;
    private boolean searchRevealed;
    private Component summary = CommonComponents.EMPTY;

    public SectionHeader(int x, int y, int width, int height, Component label, Font font,
            Runnable onToggle) {
        super(x, y, width, height, label);
        this.font = font;
        this.label = label;
        this.boldLabel = label.copy().withStyle(net.minecraft.ChatFormatting.BOLD);
        this.onToggle = onToggle;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        updateMessage();
    }

    public void setSearchRevealed(boolean searchRevealed) {
        this.searchRevealed = searchRevealed;
        updateMessage();
    }

    public void setSummary(Component summary) {
        this.summary = summary == null ? CommonComponents.EMPTY : summary;
        updateMessage();
    }

    private boolean showsChildren() {
        return !collapsed || searchRevealed;
    }

    private void updateMessage() {
        setMessage(showsChildren() || summary.getString().isEmpty()
                ? label
                : Component.empty().append(label).append(" — ").append(summary));
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY,
            float delta) {
        SettingRowPainter.paintBackground(context, getX(), getY(), getWidth(), getHeight(),
                isHovered(), isFocused(), getAlpha());
        int baseline = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        int arrowX = getX() + SettingRowPainter.ROW_PADDING;
        context.drawString(font, showsChildren() ? "▼" : "▶", arrowX, baseline,
                searchRevealed && collapsed ? REVEALED_ARROW_COLOR : LABEL_COLOR, true);

        int textX = arrowX + ARROW_WIDTH;
        context.drawString(font, boldLabel, textX, baseline, LABEL_COLOR, true);
        int labelEnd = textX + font.width(boldLabel);

        if (!showsChildren() && !summary.getString().isEmpty()) {
            context.drawString(font, summary, labelEnd + 6, baseline, SUMMARY_COLOR, true);
            labelEnd += 6 + font.width(summary);
        }

        int ruleY = getY() + getHeight() - 2;
        int right = getX() + getWidth();
        int ruleStart = labelEnd + 4;
        if (right > ruleStart) {
            context.fill(ruleStart, ruleY, right, ruleY + 1, isHovered() ? HOVER_RULE_COLOR : RULE_COLOR);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onToggle.run();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (SettingRow.isActivationKey(event.key())) {
            onToggle.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
