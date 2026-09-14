package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteUsageSettings;
import com.asphyxiamywife.elytrapitchhelper.config.SectionCollapseSettings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

final class ConfigScreenHistoryController implements SliderRow.InteractionListener, ConfigEditSession.History {
    interface Host {
        Config config();

        ConfigHistory.Context historyContext();

        void restoreHistoryContext(ConfigHistory.Context context);

        void syncEditingProfileIndex();

        void reconcileResetUndo();

        void saveInternal(String actionKey);

        boolean savePending();

        boolean hasExternalRevision();

        void loadConfigSnapshot();

        void rebuildWidgets();

        void returnToConfigScreen();

        Minecraft minecraftClient();
    }

    private final Host host;
    private final ConfigHistory history = new ConfigHistory();
    private Config baseline;
    private ConfigHistory.Context baselineContext;
    private boolean restoring;

    ConfigScreenHistoryController(Host host) {
        this.host = host;
    }

    SliderRow.InteractionListener sliderListener() {
        return this;
    }

    boolean isRestoring() {
        return restoring;
    }

    boolean canUndo() {
        return history.canUndo();
    }

    boolean canRedo() {
        return history.canRedo();
    }

    @Override
    public void begin(String actionKey) {
        history.begin(actionKey, host.config(), host.historyContext());
    }

    @Override
    public void commit(boolean changed, boolean coalesce) {
        history.commit(host.config(), host.historyContext(), changed, coalesce, MonotonicClock.millis());
        resetBaseline();
    }

    @Override
    public void end(String actionKey, boolean changed, boolean coalesce) {
        commit(changed, coalesce);
    }

    void recordImplicit(String actionKey) {
        if (!restoring && !history.isActionActive()) {
            history.record(actionKey, baseline, baselineContext, host.config(), host.historyContext(), false,
                    MonotonicClock.millis());
            resetBaseline();
        }
    }

    void finishActiveAction() {
        if (history.isActionActive()) {
            history.commit(host.config(), host.historyContext(),
                    !baseline.hasSameState(host.config()), true, MonotonicClock.millis());
            resetBaseline();
        }
    }

    void breakCoalescing() {
        history.breakCoalescing();
    }

    void clear() {
        history.clear();
        resetBaseline();
    }

    void resetBaseline() {
        baseline = host.config().copy();
        baselineContext = host.historyContext();
    }

    void updateContextBaseline() {
        baselineContext = host.historyContext();
    }

    void copyPaletteUsageToBaseline() {
        if (baseline != null) {
            baseline.commandPaletteUsage = host.config().commandPaletteUsage;
        }
    }

    boolean handleShortcut(KeyEvent event) {
        return UndoRedoShortcuts.handle(event, () -> restore(false), () -> restore(true), () -> {});
    }

    boolean restore(boolean redo) {
        ConfigHistory.Restore restore = redo ? history.redo() : history.undo();
        if (restore == null) {
            return false;
        }
        restoring = true;
        try {
            Config snapshot = restore.config();
            CommandPaletteUsageSettings paletteUsage = host.config().commandPaletteUsage == null
                    ? new CommandPaletteUsageSettings()
                    : host.config().commandPaletteUsage;
            SectionCollapseSettings sectionCollapse = host.config().sectionCollapse == null
                    ? new SectionCollapseSettings()
                    : host.config().sectionCollapse;
            boolean usedSettingsSearch = host.config().usedSettingsSearch;
            if (!snapshot.prepareHistoryRestoreFrom(restore.actionSource(), host.config())) {
                if (redo) history.undo();
                else history.redo();
                return false;
            }
            host.config().restoreFrom(snapshot);
            host.config().commandPaletteUsage = paletteUsage;
            host.config().sectionCollapse = sectionCollapse;
            host.config().usedSettingsSearch = usedSettingsSearch;
            host.restoreHistoryContext(restore.context());
            host.syncEditingProfileIndex();
            host.reconcileResetUndo();
            resetBaseline();
            if (!restore.context().deleteMode()) {
                host.saveInternal(redo ? "redo" : "undo");
            }
        } finally {
            restoring = false;
        }
        Minecraft minecraft = host.minecraftClient();
        if (minecraft == null || minecraft.gui.screen() == host) {
            host.rebuildWidgets();
        }
        return true;
    }

    boolean acceptExternalRevision() {
        if (host.savePending() || !host.hasExternalRevision()) {
            return false;
        }
        host.loadConfigSnapshot();
        clear();
        host.syncEditingProfileIndex();
        Minecraft minecraft = host.minecraftClient();
        if (minecraft != null) {
            host.returnToConfigScreen();
        }
        return true;
    }
}
