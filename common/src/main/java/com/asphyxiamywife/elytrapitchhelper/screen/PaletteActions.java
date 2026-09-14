package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.ConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.KeyBindings;
import com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteUsageSettings;
import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingReset;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformFileOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

final class PaletteActions {
    private static final Component SETTINGS = Component.translatable("palette.elytrapitchhelper.category.settings");
    private static final Component PROFILES = Component.translatable("palette.elytrapitchhelper.category.profiles");
    private static final Component ACTIONS = Component.translatable("palette.elytrapitchhelper.category.actions");

    private PaletteActions() {
    }

    static List<PaletteAction> available(PaletteState state) {
        List<PaletteAction> actions = new ArrayList<>();
        addSettings(actions, state);
        addProfiles(actions, state);
        addActions(actions);
        long nowMillis = System.currentTimeMillis();
        return actions.stream()
                .filter(action -> action.available(state))
                .map(action -> withUsageBoost(action, state, nowMillis))
                .toList();
    }

    static void recordUse(PaletteAction action, PaletteState state, Minecraft client) {
        String actionId = action.id().toString();
        long nowMillis = System.currentTimeMillis();
        if (state.configScreen() != null) {
            state.configScreen().recordPaletteActionUse(actionId, nowMillis);
            return;
        }

        ConfigStore store = ClientConfigStore.store();
        Config config = store.get();
        if (config.isReadOnly()) {
            return;
        }
        if (config.commandPaletteUsage == null) {
            config.commandPaletteUsage = new CommandPaletteUsageSettings();
        }
        config.commandPaletteUsage = config.commandPaletteUsage.recordUse(actionId, nowMillis);
        saveLiveConfig(client, store, config);
    }

    static String settingActionId(SettingsSearch.Entry entry) {
        return ModConstants.id("palette/settings/" + entry.spec().id()).toString();
    }

    static List<PaletteAction> search(List<PaletteAction> actions, String query) {
        return PaletteActionSearch.search(actions, query);
    }

    private static void addSettings(List<PaletteAction> actions, PaletteState state) {
        for (ConfigCategory category : ConfigCategory.values()) {
            actions.add(action("settings/open_" + category.name().toLowerCase(Locale.ROOT),
                    Component.translatable("palette.elytrapitchhelper.open_category",
                            Component.translatable(category.translationKey)),
                    SETTINGS,
                    List.of("open", "settings", "tab", category.name().toLowerCase(Locale.ROOT)),
                    90,
                    availableState -> availableState.hasProfiles() && !availableState.deleteMode(),
                    (client, availableState) -> openConfigScreen(client, availableState)
                            .openCategoryFromPalette(category)));
        }

        Profile profile = state.currentProfile();
        if (profile != null) {
            for (SettingsSearch.Entry entry : SettingsSearch.allEntries(profile)) {
                actions.add(settingAction(entry, profile, 80));
                addSettingReset(actions, state, entry);
            }
        }
    }

    private static void addSettingReset(List<PaletteAction> actions, PaletteState state,
            SettingsSearch.Entry entry) {
        SettingReset.Unit unit = SettingReset.forSetting(entry.spec().id()).orElse(null);
        boolean dynamicVoid = "void_y_mode".equals(entry.spec().id())
                && state.configScreen() != null
                && state.configScreen().paletteCanResetVoidY();
        if (unit == null && !dynamicVoid) {
            return;
        }
        Profile profile = state.currentProfile();
        if (!dynamicVoid && (profile == null
                || !SettingReset.isModified(unit, profile, Config.defaultProfileTemplate()))) {
            return;
        }
        actions.add(action("settings/reset_" + entry.spec().id(),
                Component.translatable("palette.elytrapitchhelper.reset_setting",
                        Component.translatable(entry.labelKey())),
                Component.translatable(entry.category().translationKey),
                List.of("reset", "default", "revert", entry.optionName(), entry.labelKey()),
                82,
                availableState -> writable(availableState)
                        && availableState.hasProfiles() && !availableState.deleteMode(),
                (client, availableState) -> resetSettingFromPalette(client, availableState, entry)));
    }

    private static PaletteAction withUsageBoost(PaletteAction action, PaletteState state, long nowMillis) {
        Config config = state.config();
        if (config == null || config.commandPaletteUsage == null) {
            return action;
        }
        int boost = config.commandPaletteUsage.rankingBoost(action.id().toString(), nowMillis);
        return boost <= 0 ? action : new UsageBoostedAction(action, boost);
    }

    private static PaletteAction settingAction(SettingsSearch.Entry entry, Profile profile, int priority) {
        boolean toggle = entry.paletteOpenMode() == SettingsSearch.PaletteOpenMode.TOGGLE_SETTING;
        return action("settings/" + entry.spec().id(),
                toggle ? toggleSettingTitle(entry, profile)
                        : Component.translatable("palette.elytrapitchhelper.open_setting",
                                Component.translatable(entry.labelKey())),
                Component.translatable(entry.category().translationKey),
                settingKeywords(entry, profile, toggle),
                toggle ? priority + 6 : priority,
                state -> (!toggle || writable(state))
                        && state.hasProfiles() && !state.deleteMode(),
                toggle
                        ? (client, state) -> toggleSettingFromPalette(client, state, entry)
                        : (client, state) -> openConfigScreen(client, state).openSettingFromPalette(entry));
    }

    private static Component toggleSettingTitle(SettingsSearch.Entry entry, Profile profile) {
        Component label = Component.translatable(entry.labelKey());
        String nextValueKey = entry.toggleBinding().nextValueKey(profile);
        return nextValueKey == null
                ? booleanToggleTitle(label, !entry.toggleBinding().currentValue(profile))
                : settingValueTitle(label, nextValueKey);
    }

    private static Component settingValueTitle(Component label, String valueKey) {
        return Component.translatable("palette.elytrapitchhelper.set_setting_to",
                label, Component.translatable(valueKey));
    }

    private static Component booleanToggleTitle(Component label, boolean enabledAfterToggle) {
        return Component.translatable(enabledAfterToggle
                ? "palette.elytrapitchhelper.enable_setting"
                : "palette.elytrapitchhelper.disable_setting", label);
    }

    private static List<String> settingKeywords(SettingsSearch.Entry entry, Profile profile, boolean toggle) {
        List<String> keywords = new ArrayList<>(List.of(
                "setting", "settings", optionName(entry), entry.labelKey(), entry.tooltipKey()));
        if (toggle) {
            keywords.add("toggle");
            keywords.addAll(toggleSearchKeywords(entry, profile));
        }
        keywords.addAll(entry.paletteKeywords());
        return List.copyOf(keywords);
    }

    private static List<String> toggleSearchKeywords(SettingsSearch.Entry entry, Profile profile) {
        String nextValueKey = entry.toggleBinding().nextValueKey(profile);
        return nextValueKey == null
                ? booleanToggleKeywords(entry.toggleBinding().currentValue(profile))
                : concreteValueKeywords(nextValueKey);
    }

    private static List<String> booleanToggleKeywords(boolean currentlyEnabled) {
        return List.of(currentlyEnabled ? "disable" : "enable");
    }

    private static List<String> concreteValueKeywords(String valueKey) {
        return List.of("set", valueKey, concreteValueKeyword(valueKey));
    }

    private static String concreteValueKeyword(String valueKey) {
        int lastDot = valueKey.lastIndexOf('.');
        return lastDot >= 0 ? valueKey.substring(lastDot + 1).replace('_', ' ') : valueKey;
    }

    private static void toggleSettingFromPalette(Minecraft client, PaletteState state, SettingsSearch.Entry entry) {
        if (state.configScreen() != null) {
            client.setScreen(state.configScreen());
            state.configScreen().toggleSettingFromPalette(entry);
            return;
        }

        Profile selectedProfile = state.currentProfile();
        String selectedFile = selectedProfile == null ? null : selectedProfile.fileName();
        ConfigStore store = ClientConfigStore.store();
        Config config = store.get();
        int currentIndex = config.profileIndexByFileName(selectedFile);
        if (currentIndex >= 0 && toggleSetting(config, currentIndex, entry)) {
            saveLiveConfig(client, store, config);
        }
    }

    static boolean toggleSetting(Config config, int profileIndex, SettingsSearch.Entry entry) {
        if (config == null || config.isReadOnly() || config.profileCount() <= 0) {
            return false;
        }
        int clampedIndex = config.clampProfileIndex(profileIndex);
        Profile profile = config.profile(clampedIndex);
        if (entry.toggleBinding() == null) {
            return false;
        }
        return config.replaceProfile(profileIndex, entry.toggleBinding().apply(profile));
    }

    private static void resetSettingFromPalette(Minecraft client, PaletteState state,
            SettingsSearch.Entry entry) {
        if (state.configScreen() != null) {
            client.setScreen(state.configScreen());
            state.configScreen().resetSettingFromPalette(entry);
            return;
        }
        SettingReset.Unit unit = SettingReset.forSetting(entry.spec().id()).orElse(null);
        if (unit == null) {
            return;
        }
        ConfigStore store = ClientConfigStore.store();
        Config config = store.get();
        if (config.isReadOnly() || config.profileCount() <= 0) {
            return;
        }
        int profileIndex = config.clampProfileIndex(config.activeProfileIndex);
        Profile profile = config.profile(profileIndex);
        Profile defaults = Config.defaultProfileTemplate();
        if (!SettingReset.isModified(unit, profile, defaults)) {
            return;
        }
        config.replaceProfile(profileIndex, SettingReset.reset(unit, profile, defaults));
        saveLiveConfig(client, store, config);
    }

    private static void addProfiles(List<PaletteAction> actions, PaletteState state) {
        actions.add(action("profiles/open",
                Component.translatable("palette.elytrapitchhelper.open_profiles"),
                PROFILES,
                List.of("profiles", "list", "open", "manage"),
                88,
                availableState -> availableState.hasProfiles(),
                (client, availableState) -> showConfigScreen(client, availableState, true)
                        .openProfilesFromPalette()));

        if (state.hasProfiles()) {
            for (int i = 0; i < state.config().profileCount(); i++) {
                String profileFile = state.config().profile(i).fileName();
                String profileName = state.config().profileName(i);
                actions.add(action("profiles/switch_" + safeId(profileFile),
                        Component.translatable("palette.elytrapitchhelper.switch_profile",
                                Component.literal(profileName)),
                        PROFILES,
                        List.of("profile", "switch", "active", profileName, profileFile),
                        70,
                        availableState -> {
                            int profileIndex = availableState.config().profileIndexByFileName(profileFile);
                            return availableState.hasProfiles()
                                    && writable(availableState)
                                    && !availableState.deleteMode()
                                    && profileIndex >= 0
                                    && !availableState.config().isActiveProfile(profileIndex);
                        },
                        (client, availableState) -> switchProfile(client, availableState, profileFile)));
            }
        }

        actions.add(action("profiles/duplicate_current",
                Component.translatable("palette.elytrapitchhelper.duplicate_current_profile"),
                PROFILES,
                List.of("profile", "duplicate", "copy", "current"),
                68,
                availableState -> writable(availableState)
                        && availableState.hasProfiles() && !availableState.deleteMode(),
                PaletteActions::duplicateCurrentProfile));
    }

    private static void addActions(List<PaletteAction> actions) {
        actions.add(action("actions/toggle_eph",
                Component.translatable("palette.elytrapitchhelper.toggle"),
                ACTIONS,
                List.of("toggle", "enabled", "disable", "enable", "eph", "elytra pitch helper", "guides"),
                100,
                state -> writable(state) && !state.deleteMode(),
                PaletteActions::toggleEph));

        actions.add(action("actions/expand_all_sections",
                Component.translatable("palette.elytrapitchhelper.expand_all_sections"),
                ACTIONS,
                List.of("expand", "all", "sections", "unfold", "open"),
                64,
                state -> state.hasProfiles() && !state.deleteMode(),
                (client, state) -> openConfigScreen(client, state).expandAllSectionsFromPalette()));
        actions.add(action("actions/reset_current_profile",
                Component.translatable("palette.elytrapitchhelper.reset_current_profile"),
                ACTIONS,
                List.of("reset", "defaults", "profile", "current"),
                62,
                state -> writable(state) && state.hasProfiles() && !state.deleteMode(),
                PaletteActions::resetCurrentProfile));
        actions.add(action("actions/undo_reset_current_profile",
                Component.translatable("palette.elytrapitchhelper.undo_reset_current_profile"),
                ACTIONS,
                List.of("undo", "reset", "restore", "profile", "current"),
                63,
                state -> state.configScreen() != null && state.configScreen().paletteCanUndoReset(),
                (client, state) -> {
                    client.setScreen(state.configScreen());
                    state.configScreen().undoResetFromPalette();
                }));

        actions.add(action("actions/undo",
                Component.translatable("palette.elytrapitchhelper.undo"),
                ACTIONS,
                List.of("undo", "history"),
                60,
                state -> state.configScreen() != null && state.configScreen().paletteCanUndo(),
                (client, state) -> {
                    client.setScreen(state.configScreen());
                    state.configScreen().undoFromPalette();
                }));

        actions.add(action("actions/redo",
                Component.translatable("palette.elytrapitchhelper.redo"),
                ACTIONS,
                List.of("redo", "history"),
                59,
                state -> state.configScreen() != null && state.configScreen().paletteCanRedo(),
                (client, state) -> {
                    client.setScreen(state.configScreen());
                    state.configScreen().redoFromPalette();
                }));

        actions.add(action("actions/open_json",
                Component.translatable("palette.elytrapitchhelper.open_json"),
                ACTIONS,
                List.of("open", "json", "file", "profile"),
                58,
                state -> state.hasProfiles() && !state.deleteMode(),
                PaletteActions::openCurrentProfileJson));
    }

    private static ConfigScreen openConfigScreen(Minecraft client, PaletteState state) {
        return showConfigScreen(client, state, false);
    }

    private static ConfigScreen showConfigScreen(Minecraft client, PaletteState state, boolean profiles) {
        ConfigScreen screen = state.configScreen();
        if (screen == null) {
            screen = profiles ? ConfigScreen.profiles(state.parent(), true)
                    : new ConfigScreen(state.parent(), true);
        }
        client.setScreen(screen);
        return screen;
    }

    private static void toggleEph(Minecraft client, PaletteState state) {
        if (state.configScreen() != null) {
            client.setScreen(state.configScreen());
            state.configScreen().toggleEnabledFromPalette();
        } else {
            KeyBindings.toggleGuides(client);
        }
    }

    private static void switchProfile(Minecraft client, PaletteState state, String profileFile) {
        if (state.configScreen() != null) {
            client.setScreen(state.configScreen());
            state.configScreen().switchProfileFromPalette(profileFile);
            return;
        }

        ConfigStore store = ClientConfigStore.store();
        Config config = store.get();
        int index = config.profileIndexByFileName(profileFile);
        if (index < 0) {
            return;
        }
        config.selectProfile(index);
        saveLiveConfig(client, store, config);
    }

    private static void duplicateCurrentProfile(Minecraft client, PaletteState state) {
        if (state.configScreen() != null) {
            client.setScreen(state.configScreen());
            state.configScreen().duplicateCurrentProfileFromPalette();
            return;
        }

        ConfigStore store = ClientConfigStore.store();
        Config config = store.get();
        if (config.profileCount() <= 0) {
            return;
        }
        config.duplicateProfile(config.clampProfileIndex(config.activeProfileIndex));
        saveLiveConfig(client, store, config);
    }

    private static void resetCurrentProfile(Minecraft client, PaletteState state) {
        ConfigScreen screen = openConfigScreen(client, state);
        screen.requestResetCurrentProfileFromPalette();
    }

    private static void openCurrentProfileJson(Minecraft client, PaletteState state) {
        if (state.configScreen() != null) {
            client.setScreen(state.configScreen());
            state.configScreen().openCurrentProfileJsonFromPalette();
            return;
        }

        Config config = ClientConfigStore.get();
        int index = config.clampProfileIndex(config.activeProfileIndex);
        Path path = config.getProfilePath(ClientConfigStore.store().fileSystem(), index);
        PlatformFileOpener.openAsync(path).thenAcceptAsync(opened -> {
            if (!opened) {
                sendOverlay(client, Component.translatable("message.elytrapitchhelper.profile.open_failed"));
            }
        }, client::execute);
    }

    private static void saveLiveConfig(Minecraft client, ConfigStore store, Config config) {
        if (ClientConfigStore.store() != store) {
            return;
        }
        if (config.isReadOnly()) {
            sendOverlay(client, Component.translatable("message.elytrapitchhelper.config.save_failed"));
            return;
        }
        store.saveAsync(config).thenAcceptAsync(result -> {
            if (!result.isCurrentAtDelivery()) {
                return;
            }
            if (result.result().outcome() != ClientConfigStore.SaveOutcome.COMMITTED) {
                sendOverlay(client, Component.translatable("message.elytrapitchhelper.config.save_failed"));
            }
        }, client::execute);
    }

    private static void sendOverlay(Minecraft client, Component message) {
        if (client.player != null) {
            client.player.displayClientMessage(message, true);
        }
    }

    private static boolean writable(PaletteState state) {
        return state.config() != null && !state.config().isReadOnly();
    }

    private static PaletteAction action(String id, Component title, Component category, List<String> keywords,
            int priority, Predicate<PaletteState> available, BiConsumer<Minecraft, PaletteState> execute) {
        return new SimpleAction(ModConstants.id("palette/" + id), title, category, keywords,
                priority, available, execute);
    }

    private static String optionName(SettingsSearch.Entry entry) {
        return entry.optionName();
    }

    private static String safeId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record UsageBoostedAction(PaletteAction delegate, int boost) implements PaletteAction {
        @Override
        public Identifier id() {
            return delegate.id();
        }

        @Override
        public Component title() {
            return delegate.title();
        }

        @Override
        public Component category() {
            return delegate.category();
        }

        @Override
        public List<String> keywords() {
            return delegate.keywords();
        }

        @Override
        public int priority() {
            return delegate.priority() + boost;
        }

        @Override
        public boolean available(PaletteState state) {
            return delegate.available(state);
        }

        @Override
        public void execute(Minecraft client, PaletteState state) {
            delegate.execute(client, state);
        }
    }

    private record SimpleAction(Identifier id, Component title, Component category, List<String> keywords,
            int priority, Predicate<PaletteState> availability,
            BiConsumer<Minecraft, PaletteState> executor) implements PaletteAction {
        @Override
        public boolean available(PaletteState state) {
            return availability.test(state);
        }

        @Override
        public void execute(Minecraft client, PaletteState state) {
            executor.accept(client, state);
        }
    }
}
