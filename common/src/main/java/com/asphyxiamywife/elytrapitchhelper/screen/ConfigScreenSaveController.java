package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.ConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigConflictDiff;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigConflictException;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigSaveException;
import com.asphyxiamywife.elytrapitchhelper.config.NewerConfigVersionException;
import com.asphyxiamywife.elytrapitchhelper.config.ProfileFileNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

final class ConfigScreenSaveController {
    interface Host {
        Config config();

        void replaceConfig(Config config);

        Minecraft minecraftClient();

        default Screen currentSaveScreen() {
            Minecraft minecraft = minecraftClient();
            return minecraft == null ? null : minecraft.gui.screen();
        }

        default Executor saveCompletionExecutor() {
            Minecraft minecraft = minecraftClient();
            return minecraft == null ? Runnable::run : minecraft::execute;
        }

        void resetHistoryBaseline();

        void reconcileResetUndo();

        void clearHistory();

        void syncEditingProfileIndex();

        void rebuildWidgets();

        void sendOverlay(Component message);

        void leaveConfigWorkflow();
    }

    private final Host host;
    private final ConfigStore store = ClientConfigStore.store();
    private final Function<List<Path>, Config.ConflictBaseline> conflictBaselineCapture;
    private long revision;
    private boolean pending;
    private boolean conflictConfirmationOpen;
    private boolean saveFailurePresentationOpen;
    private boolean asyncFlushInFlight;
    private long handledConflictGeneration = -1L;
    private final Set<String> pendingProfileDeletes = new LinkedHashSet<>();
    private final List<Runnable> pendingCommitActions = new ArrayList<>();

    ConfigScreenSaveController(Host host) {
        this(host, null);
    }

    ConfigScreenSaveController(Host host,
            Function<List<Path>, Config.ConflictBaseline> conflictBaselineCapture) {
        this.host = host;
        this.conflictBaselineCapture = conflictBaselineCapture != null ? conflictBaselineCapture
                : paths -> Config.captureConflictBaseline(store.fileSystem(), paths);
        revision = store.revision();
        pending = store.hasUnsavedChanges();
        pendingProfileDeletes.addAll(store.pendingProfileDeletes());
        if (pending) {
            Config pendingConfig = store.pendingSaveConfig();
            if (pendingConfig != null) {
                host.replaceConfig(pendingConfig);
            }
        }
    }

    long revision() {
        return revision;
    }

    boolean pending() {
        return pending;
    }

    boolean hasExternalRevision() {
        return ownsCurrentStore() && revision != store.revision();
    }

    void scheduleSave() {
        if (!ownsCurrentStore()) {
            return;
        }
        store.update(host.config());
        revision = store.revision();
        pending = true;
        saveFailurePresentationOpen = false;
        handledConflictGeneration = -1L;
    }

    void scheduleProfileDeletes(List<String> profileFiles) {
        if (!ownsCurrentStore()) {
            return;
        }
        pendingProfileDeletes.addAll(profileFiles);
        store.update(host.config(), profileFiles);
        revision = store.revision();
        pending = true;
        saveFailurePresentationOpen = false;
        handledConflictGeneration = -1L;
    }

    private boolean ownsCurrentStore() {
        if (ClientConfigStore.store() == store) {
            return true;
        }
        pendingCommitActions.clear();
        return false;
    }

    void workflowRemoved() {
        if (!conflictConfirmationOpen && !saveFailurePresentationOpen) {
            pendingCommitActions.clear();
        }
    }

    void flushIfDue() {
        if (!ownsCurrentStore()) {
            return;
        }
        if (pending && !store.hasUnsavedChanges()) {
            completeCommittedSave();
            runPendingCommitActions();
            return;
        }
        if (pending && !saveFailurePresentationOpen && !conflictConfirmationOpen) {
            ClientConfigStore.PendingConflict backgroundConflict = store.pendingSaveConflict();
            if (backgroundConflict != null
                    && backgroundConflict.pendingGeneration() != handledConflictGeneration) {
                handledConflictGeneration = backgroundConflict.pendingGeneration();
                openConflictConfirmation(backgroundConflict.conflict());
                return;
            }
        }
    }

    void flushAsyncIfPending(Runnable afterCommit) {
        if (!ownsCurrentStore()) {
            return;
        }
        Objects.requireNonNull(afterCommit, "afterCommit");
        if (!pending && !store.hasUnsavedChanges()) {
            afterCommit.run();
            return;
        }
        pendingCommitActions.add(afterCommit);
        startAsyncFlush();
    }

    void leaveAfterSaving() {
        if (!ownsCurrentStore()) {
            Minecraft minecraft = host.minecraftClient();
            if (minecraft == null || minecraft.gui.screen() == host) {
                host.leaveConfigWorkflow();
            }
            return;
        }
        flushAsyncIfPending(host::leaveConfigWorkflow);
    }

    private void startAsyncFlush() {
        if (!ownsCurrentStore()) {
            return;
        }
        pending = true;
        if (asyncFlushInFlight || saveFailurePresentationOpen || conflictConfirmationOpen) {
            return;
        }

        asyncFlushInFlight = true;
        Executor completionExecutor = host.saveCompletionExecutor();
        Screen originScreen = host.currentSaveScreen();
        try {
            store.flushPending()
                    .whenCompleteAsync((result, failure) -> {
                        asyncFlushInFlight = false;
                        Screen currentScreen = host.currentSaveScreen();
                        if (ClientConfigStore.store() != store
                                || (currentScreen != originScreen
                                    && !ConfigWorkflowChild.belongsTo(currentScreen, originScreen)
                                    && !ConfigWorkflowChild.belongsTo(currentScreen, host))) {
                            pendingCommitActions.clear();
                            return;
                        }
                        if (failure != null) {
                            presentSaveFailure(asSaveException(failure));
                            return;
                        }
                        handleAsyncSaveResult(result);
                    }, completionExecutor);
        } catch (RuntimeException failure) {
            asyncFlushInFlight = false;
            presentSaveFailure(asSaveException(failure));
        }
    }

    private void completeCommittedSave() {
        if (!ownsCurrentStore()) {
            return;
        }
        ClientConfigStore.Snapshot committed = store.snapshot();
        Config committedConfig = committed.config();
        host.config().syncSavedProfileMetadataFrom(committedConfig);
        boolean stateChanged = !host.config().hasSameState(committedConfig)
                || host.config().isReadOnly() != committedConfig.isReadOnly();
        if (stateChanged) {
            host.replaceConfig(committedConfig);
        }
        revision = committed.revision();
        pending = false;
        saveFailurePresentationOpen = false;
        handledConflictGeneration = -1L;
        pendingProfileDeletes.clear();
        if (stateChanged) {
            host.reconcileResetUndo();
            host.syncEditingProfileIndex();
            host.clearHistory();
            host.rebuildWidgets();
        } else {
            host.resetHistoryBaseline();
        }
    }

    void explicitSave() {
        flushAsyncIfPending(() ->
                host.sendOverlay(Component.translatable("message.elytrapitchhelper.config.saved")));
    }

    void handleAsyncSaveResult(ClientConfigStore.AsyncSaveResult asyncResult) {
        if (ClientConfigStore.store() != store || (asyncResult.store() != null && asyncResult.store() != store)) {
            pendingCommitActions.clear();
            return;
        }
        ClientConfigStore.SaveObservation observation = store.saveObservation();
        if (asyncResult.pendingGeneration() != observation.latestGeneration()) {
            if (pending) {
                if (store.hasUnsavedChanges()) {
                    startAsyncFlush();
                } else {
                    completeCommittedSave();
                    runPendingCommitActions();
                }
            }
            return;
        }
        ClientConfigStore.SaveResult result = asyncResult.result();
        if ((result.outcome() == ClientConfigStore.SaveOutcome.FAILED
                || result.outcome() == ClientConfigStore.SaveOutcome.CONFLICTED)
                && observation.status().pendingGeneration() != asyncResult.pendingGeneration()) {
            return;
        }
        switch (result.outcome()) {
            case COMMITTED -> {
                if (store.hasUnsavedChanges()) {
                    pendingProfileDeletes.clear();
                    pendingProfileDeletes.addAll(store.pendingProfileDeletes());
                    startAsyncFlush();
                    return;
                }
                completeCommittedSave();
                runPendingCommitActions();
            }
            case CONFLICTED -> {
                if (result.conflict() != null) {
                    openConflictConfirmation(result.conflict());
                }
            }
            case FAILED -> presentSaveFailure(result.failure());
            case SUPERSEDED -> startAsyncFlush();
            case CANCELLED -> {
            }
        }
    }

    void loadSnapshot() {
        if (!ownsCurrentStore()) {
            return;
        }
        host.replaceConfig(store.get().copy());
        revision = store.revision();
        pending = false;
        saveFailurePresentationOpen = false;
        handledConflictGeneration = -1L;
        pendingProfileDeletes.clear();
        host.reconcileResetUndo();
        host.resetHistoryBaseline();
    }

    void refreshSnapshot() {
        if (!ownsCurrentStore()) {
            return;
        }
        if (!pending && hasExternalRevision()) {
            loadSnapshot();
            host.clearHistory();
            host.syncEditingProfileIndex();
        }
    }

    private void openConflictConfirmation(ConfigConflictException conflict) {
        if (!ownsCurrentStore()) {
            return;
        }
        if (conflictConfirmationOpen) {
            return;
        }
        Config.ConflictBaseline conflictBaseline;
        try {
            conflictBaseline = conflictBaselineCapture.apply(conflict.conflictPaths());
        } catch (ConfigSaveException failure) {
            presentSaveFailure(failure);
            return;
        }
        Minecraft minecraft = host.minecraftClient();
        if (minecraft == null) {
            return;
        }
        conflictConfirmationOpen = true;
        List<ConfigConflictDiff.FileDiff> diffs = ConfigConflictDiff.capture(
                store.fileSystem(), host.config(), conflict.conflictPaths(),
                Set.copyOf(pendingProfileDeletes));
        minecraft.gui.setScreen(new ConfigConflictScreen(diffs, keepInGameChanges -> {
            conflictConfirmationOpen = false;
            if (!ownsCurrentStore()) {
                return;
            }
            minecraft.gui.setScreen((ConfigScreen) host);
            if (keepInGameChanges) {
                acceptConflictAndRetry(conflictBaseline);
            } else {
                reloadAfterConflict(conflict);
            }
        }));
    }

    void acceptConflictAndRetry(Config.ConflictBaseline conflictBaseline) {
        if (!ownsCurrentStore()) {
            return;
        }
        host.config().acceptConflictBaseline(
                store.fileSystem(), conflictBaseline);
        store.update(host.config(), List.copyOf(pendingProfileDeletes));
        startAsyncFlush();
    }

    private void reloadAfterConflict(ConfigConflictException conflict) {
        if (!ownsCurrentStore()) {
            return;
        }
        Config config = host.config();
        var loaded = Config.tryReloadFromDisk(store.fileSystem());
        if (loaded.isEmpty()) {
            host.sendOverlay(Component.translatable("message.elytrapitchhelper.config.reload_failed"));
            return;
        }
        Config diskConfig = loaded.get();
        if (diskConfig.isReadOnly()) {
            if (!store.reloadFromDisk()) {
                host.sendOverlay(Component.translatable("message.elytrapitchhelper.config.reload_failed"));
                return;
            }
            loadSnapshot();
            host.clearHistory();
            host.syncEditingProfileIndex();
            host.rebuildWidgets();
            runPendingCommitActions();
            return;
        }
        if (conflict.conflictPaths().contains(store.configPath())) {
            config.acceptMainConfigStateFromDisk(diskConfig);
        }
        Set<String> conflictingFiles = conflict.conflictPaths().stream()
                .filter(path -> !path.equals(store.configPath()))
                .map(path -> path.getFileName().toString())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!conflictingFiles.isEmpty()) {
            pendingProfileDeletes.removeIf(pending -> conflictingFiles.stream()
                    .anyMatch(conflicting -> sameProfileFile(conflicting, pending)));
            config.acceptProfileStatesFromDisk(diskConfig, conflictingFiles);
        }
        host.reconcileResetUndo();
        store.update(config, List.of(), conflictingFiles);
        revision = store.revision();
        pending = true;
        host.clearHistory();
        host.syncEditingProfileIndex();
        host.rebuildWidgets();
        startAsyncFlush();
    }

    private static boolean sameProfileFile(String first, String second) {
        String firstKey = ProfileFileNames.comparisonKey(first);
        return firstKey != null && firstKey.equals(ProfileFileNames.comparisonKey(second));
    }

    private void runPendingCommitActions() {
        List<Runnable> committedActions = List.copyOf(pendingCommitActions);
        pendingCommitActions.clear();
        for (Runnable action : committedActions) {
            if (!ownsCurrentStore()) {
                return;
            }
            action.run();
        }
    }

    private void presentSaveFailure(ConfigSaveException failure) {
        if (!ownsCurrentStore()) {
            return;
        }
        if (saveFailurePresentationOpen) {
            return;
        }
        saveFailurePresentationOpen = true;
        ClientConfigStore.SaveStatus presentedStatus = store.saveStatus();
        Minecraft minecraft = host.minecraftClient();
        if (minecraft == null) {
            host.sendOverlay(Component.translatable("message.elytrapitchhelper.config.save_failed"));
            return;
        }
        minecraft.gui.setScreen(new ConfigSaveFailureScreen(
                () -> retryFromFailureScreen(minecraft),
                () -> stayAfterFailure(minecraft),
                () -> leaveAfterFailure(minecraft, presentedStatus, failure),
                failureDetail(failure)));
    }

    private static ConfigSaveException asSaveException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConfigSaveException saveFailure) {
                return saveFailure;
            }
        }
        return null;
    }

    private static Component failureDetail(ConfigSaveException failure) {
        if (failure == null) {
            return null;
        }
        if (failure instanceof NewerConfigVersionException) {
            return Component.translatable("screen.elytrapitchhelper.save_failure.newer_version");
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? null : Component.literal(message);
    }

    private void retryFromFailureScreen(Minecraft minecraft) {
        if (!ownsCurrentStore()) {
            return;
        }
        saveFailurePresentationOpen = false;
        minecraft.gui.setScreen((ConfigScreen) host);
        startAsyncFlush();
    }

    private void stayAfterFailure(Minecraft minecraft) {
        if (!ownsCurrentStore()) {
            return;
        }
        saveFailurePresentationOpen = false;
        pendingCommitActions.clear();
        minecraft.gui.setScreen((ConfigScreen) host);
    }

    boolean tryLeaveAfterFailure(ClientConfigStore.SaveStatus presentedStatus) {
        if (ClientConfigStore.store() != store) {
            return false;
        }
        if (!store.hasUnsavedChanges()) {
            saveFailurePresentationOpen = false;
            pendingCommitActions.clear();
            host.leaveConfigWorkflow();
            return true;
        }
        if (store.discardPendingSave(presentedStatus.pendingGeneration()).outcome()
                == ClientConfigStore.SaveOutcome.CANCELLED) {
            saveFailurePresentationOpen = false;
            pendingCommitActions.clear();
            loadSnapshot();
            host.leaveConfigWorkflow();
            return true;
        }
        return false;
    }

    private void leaveAfterFailure(Minecraft minecraft, ClientConfigStore.SaveStatus presentedStatus,
            ConfigSaveException failure) {
        if (!ownsCurrentStore() || tryLeaveAfterFailure(presentedStatus)) {
            return;
        }
        saveFailurePresentationOpen = false;
        minecraft.gui.setScreen((ConfigScreen) host);
        presentSaveFailure(failure);
    }

}
