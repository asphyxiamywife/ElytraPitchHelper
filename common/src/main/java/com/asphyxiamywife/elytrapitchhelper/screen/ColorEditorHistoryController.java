package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import net.minecraft.client.input.KeyEvent;

final class ColorEditorHistoryController implements SliderRow.InteractionListener {
    private final ColorEditorScreen.HistoryController history;

    ColorEditorHistoryController(ColorEditorScreen.HistoryController history) {
        this.history = history;
    }

    @Override
    public void begin(String actionKey) {
        history.begin(actionKey);
    }

    void end(boolean changed, boolean coalesce) {
        history.end(changed, coalesce);
    }

    @Override
    public void end(String actionKey, boolean changed, boolean coalesce) {
        end(changed, coalesce);
    }

    void breakCoalescing() {
        history.breakCoalescing();
    }

    void acceptExternalRevision() {
        history.acceptExternalRevision();
    }

    boolean handleShortcut(KeyEvent event, Runnable syncFromConfig) {
        return UndoRedoShortcuts.handle(event, history::undo, history::redo, syncFromConfig);
    }

    SliderRow.InteractionListener sliderListener() {
        return this;
    }

    ColorEditorScreen.HistoryController delegate() {
        return history;
    }
}
