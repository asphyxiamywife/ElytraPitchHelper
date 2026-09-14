package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.ConfigConflictDiff;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ConfigConflictScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int PANEL_COLOR = 0xB0000000;

    private final List<DisplayLine> lines;
    private final Consumer<Boolean> decision;
    private boolean decided;
    private int scroll;

    ConfigConflictScreen(List<ConfigConflictDiff.FileDiff> diffs, Consumer<Boolean> decision) {
        super(Component.translatable("screen.elytrapitchhelper.save_conflict.title"));
        this.lines = displayLines(diffs);
        this.decision = decision;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(120, Math.max(80, (width - 46 - BUTTON_GAP) / 2));
        int x = (width - buttonWidth * 2 - BUTTON_GAP) / 2;
        int y = height - 30;
        addRenderableWidget(FlatButton.of(
                Component.translatable("screen.elytrapitchhelper.save_conflict.keep_mine"),
                x, y, buttonWidth, BUTTON_HEIGHT, ignored -> decide(true)));
        addRenderableWidget(FlatButton.of(
                Component.translatable("screen.elytrapitchhelper.save_conflict.reload_disk"),
                x + buttonWidth + BUTTON_GAP, y, buttonWidth, BUTTON_HEIGHT,
                ignored -> decide(false)).quiet());
        scroll = Math.min(scroll, maxScroll());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.centeredText(font, title, width / 2, 12, 0xFFFFAA00);
        context.centeredText(font,
                Component.translatable("screen.elytrapitchhelper.save_conflict.diff_hint"),
                width / 2, 25, 0xFFB8B8B8);

        int left = 16;
        int top = 39;
        int right = width - 16;
        int bottom = height - 40;
        context.fill(left, top, right, bottom, PANEL_COLOR);
        context.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        int first = scroll / LINE_HEIGHT;
        int y = top + 5 - scroll % LINE_HEIGHT;
        for (int index = first; index < lines.size() && y < bottom; index++) {
            DisplayLine line = lines.get(index);
            context.text(font, line.text(), left + 6, y, line.color(), false);
            y += LINE_HEIGHT;
        }
        context.disableScissor();
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int viewportHeight = bottom - top - 2;
            ScrollbarMetrics metrics = ScrollbarMetrics.of(top + 1, viewportHeight, viewportHeight,
                    lines.size() * LINE_HEIGHT, scroll, maxScroll, 12, 0);
            ScrollbarPainter.paint(context, right - 7, 6, top + 1, viewportHeight,
                    metrics.thumbY(), metrics.thumbHeight(), mouseX >= right - 8, false);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= 39 && mouseY < height - 40 && scrollY != 0.0) {
            scroll = scrollAfterWheel(scroll, maxScroll(), scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    static int scrollAfterWheel(int scroll, int maxScroll, double scrollY) {
        return Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY) * 30));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int amount = switch (event.key()) {
            case InputConstants.KEY_UP -> -LINE_HEIGHT;
            case InputConstants.KEY_DOWN -> LINE_HEIGHT;
            case InputConstants.KEY_PAGEUP -> -Math.max(LINE_HEIGHT, height - 94);
            case InputConstants.KEY_PAGEDOWN -> Math.max(LINE_HEIGHT, height - 94);
            case InputConstants.KEY_HOME -> -maxScroll();
            case InputConstants.KEY_END -> maxScroll();
            default -> 0;
        };
        if (amount != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + amount));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        decide(false);
    }

    private int maxScroll() {
        int viewportHeight = Math.max(0, height - 84);
        return Math.max(0, lines.size() * LINE_HEIGHT - viewportHeight);
    }

    private void decide(boolean keepMine) {
        if (decided) {
            return;
        }
        decided = true;
        decision.accept(keepMine);
    }

    private static List<DisplayLine> displayLines(List<ConfigConflictDiff.FileDiff> diffs) {
        List<DisplayLine> result = new ArrayList<>();
        for (ConfigConflictDiff.FileDiff diff : diffs) {
            if (!result.isEmpty()) {
                result.add(new DisplayLine("", 0xFFFFFFFF));
            }
            result.add(new DisplayLine("@@ " + diff.fileName() + " @@", 0xFFFFFF55));
            if (diff.error() != null) {
                result.add(new DisplayLine("! " + diff.error(), 0xFFFF5555));
                continue;
            }
            if (diff.lines().isEmpty()) {
                result.add(new DisplayLine("  (no textual changes)", 0xFF888888));
                continue;
            }
            for (ConfigConflictDiff.Line line : diff.lines()) {
                result.add(switch (line.kind()) {
                    case DISK -> new DisplayLine("- " + line.text(), 0xFFFF7777);
                    case MINE -> new DisplayLine("+ " + line.text(), 0xFF77FF77);
                    case CONTEXT -> new DisplayLine("  " + line.text(), 0xFFBBBBBB);
                    case OMITTED -> new DisplayLine("  ... " + line.text() + " unchanged lines ...",
                            0xFF777777);
                });
            }
        }
        return List.copyOf(result);
    }

    private record DisplayLine(String text, int color) {
    }
}
