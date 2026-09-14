package com.asphyxiamywife.elytrapitchhelper.screen;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class ConfigEditSession {
    interface History {
        void begin(String actionKey);

        void commit(boolean changed, boolean coalesce);
    }

    private final History history;
    private final Runnable clearProfileReset;
    private final Consumer<String> save;
    private final Consumer<String> recordSettingEdit;

    ConfigEditSession(History history, Runnable clearProfileReset, Consumer<String> save,
            Consumer<String> recordSettingEdit) {
        this.history = history;
        this.clearProfileReset = clearProfileReset;
        this.save = save;
        this.recordSettingEdit = recordSettingEdit;
    }

    void profileChanged(String optionName) {
        if (optionName != null) {
            recordSettingEdit.accept(optionName);
        }
        clearProfileReset.run();
        save.accept("profile-edit");
    }

    void changeProfile(String optionName, String actionKey, Runnable change) {
        changeProfileIfChanged(optionName, actionKey, () -> {
            change.run();
            return true;
        });
    }

    void changeProfileIfChanged(String optionName, String actionKey, BooleanSupplier change) {
        history.begin(actionKey);
        boolean changed = change.getAsBoolean();
        if (changed) {
            profileChanged(optionName);
        }
        history.commit(changed, false);
    }

    void changeConfig(String actionKey, Runnable change) {
        history.begin(actionKey);
        change.run();
        save.accept("config-edit");
        history.commit(true, false);
    }
}
