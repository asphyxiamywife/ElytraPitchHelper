package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.google.gson.Gson;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PaletteActionsTest {
    @Test
    void searchFindsSettingsByRegisteredKeywords() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "amp");

        assertTrue(hasAction(matches, "palette/settings/amplitude_color"));
        assertTrue(hasAction(matches, "palette/settings/open_amplitude"));
    }

    @Test
    void searchFindsCommandPaletteAppearanceSetting() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "palette appearance");

        assertEquals("palette/settings/command_palette_appearance", actionId(matches.get(0)));
        assertTrue(hasAction(matches, "palette/settings/command_palette_appearance"));
    }

    @Test
    void resetActionsAppearOnlyForModifiedUnits() {
        Profile defaults = Config.defaultProfileTemplate().withName("Default").withFileName("default.json");
        List<PaletteAction> fresh = PaletteActions.available(state(config(defaults)));
        Profile modified = defaults.withLine(defaults.line().withLengthPixels(defaults.line().lengthPixels() + 1));
        List<PaletteAction> changed = PaletteActions.available(state(config(modified)));

        assertFalse(fresh.stream().anyMatch(action -> actionId(action).startsWith("palette/settings/reset_")));
        assertTrue(hasAction(changed, "palette/settings/reset_line_length"));
        assertEquals(1, changed.stream()
                .filter(action -> actionId(action).equals("palette/settings/reset_line_length"))
                .count());
    }

    @Test
    void prideCompanionsProduceOneColorResetAction() {
        Profile defaults = Config.defaultProfileTemplate().withName("Default").withFileName("default.json");
        Profile modified = defaults.withLine(defaults.line().withPrideEnabled(true));

        List<PaletteAction> actions = PaletteActions.available(state(config(modified)));

        assertTrue(hasAction(actions, "palette/settings/reset_line_color"));
        assertFalse(actions.stream().anyMatch(action -> actionId(action).contains("pride_enabled")));
    }

    @Test
    void searchRanksDedicatedJsonActionAboveProfileFileMatches() {
        Config config = config(
                profile("Default", "default.json"),
                profile("Speedrun", "speedrun.json"));
        config.activeProfileIndex = 0;
        config.activeProfileFile = "default.json";
        List<PaletteAction> actions = PaletteActions.available(state(config));

        List<PaletteAction> matches = PaletteActions.search(actions, "json");

        assertFalse(matches.isEmpty());
        assertEquals("palette/actions/open_json", actionId(matches.get(0)));
    }

    @Test
    void searchSupportsAcronymShortcuts() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "oj");

        assertFalse(matches.isEmpty());
        assertEquals("palette/actions/open_json", actionId(matches.get(0)));
    }

    @Test
    void searchSupportsAcronymsForSettings() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "cpa");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/command_palette_appearance", actionId(matches.get(0)));
    }

    @Test
    void searchRanksTighterPhrasesAboveBroadCategoryHits() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "amplitude color");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/amplitude_color", actionId(matches.get(0)));
    }

    @Test
    void emptySearchUsesRecordedActionUsage() {
        Config config = config(profile("Default", "default.json"));
        config.commandPaletteUsage = config.commandPaletteUsage.recordUse(
                "elytrapitchhelper:palette/settings/command_palette_appearance", System.currentTimeMillis());

        List<PaletteAction> matches = PaletteActions.search(PaletteActions.available(state(config)), "");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/command_palette_appearance", actionId(matches.get(0)));
    }

    @Test
    void emptySearchUsesRecordedLocalSettingEdits() {
        Config config = config(profile("Default", "default.json"));
        config.commandPaletteUsage = config.commandPaletteUsage.recordEdit(
                "elytrapitchhelper:palette/settings/void_y_override", System.currentTimeMillis());

        List<PaletteAction> matches = PaletteActions.search(PaletteActions.available(state(config)), "");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/void_y_override", actionId(matches.get(0)));
    }

    @Test
    void twoStateSettingsBecomeToggleActions() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "enable firework");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/show_only_with_firework", actionId(matches.get(0)));
        assertTitleMentions(matches.get(0), "enable");
        assertEquals(1, actions.stream()
                .filter(action -> actionId(action).equals("palette/settings/show_only_with_firework"))
                .count());
    }

    @Test
    void toggleActionsExposeWhenTheyWillDisableASetting() {
        Profile profile = profile("Default", "default.json");
        profile = profile.withVisibility(profile.visibility().withShowOnlyWithFirework(true));
        List<PaletteAction> actions = PaletteActions.available(state(config(profile)));

        List<PaletteAction> matches = PaletteActions.search(actions, "disable firework");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/show_only_with_firework", actionId(matches.get(0)));
        assertTitleMentions(matches.get(0), "disable");
    }

    @Test
    void flightDetectionSearchesAndLabelsNextAnyElytraGlideValue() {
        Profile profile = profile("Default", "default.json");
        profile = profile.withVisibility(profile.visibility().withAnyElytraGlide(false));
        List<PaletteAction> actions = PaletteActions.available(state(config(profile)));

        List<PaletteAction> matches = PaletteActions.search(actions, "any elytra glide");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/flight_detection", actionId(matches.get(0)));
        assertTitleSetsValue(matches.get(0), "option.elytrapitchhelper.flight_detection.any_elytra_glide");
    }

    @Test
    void flightDetectionSearchesAndLabelsNextEquipmentCheckValue() {
        Profile profile = profile("Default", "default.json");
        profile = profile.withVisibility(profile.visibility().withAnyElytraGlide(true));
        List<PaletteAction> actions = PaletteActions.available(state(config(profile)));

        List<PaletteAction> matches = PaletteActions.search(actions, "equipment check");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/flight_detection", actionId(matches.get(0)));
        assertTitleSetsValue(matches.get(0), "option.elytrapitchhelper.flight_detection.equipment_check");
    }

    @Test
    void voidModeSearchesAndLabelsNextSimpleValue() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "simple");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/void_mode", actionId(matches.get(0)));
        assertTitleSetsValue(matches.get(0), "option.elytrapitchhelper.void_mode.simple");
    }

    @Test
    void voidModeSearchesAndLabelsNextPredictedValue() {
        Profile profile = profile("Default", "default.json");
        profile = profile.withVoidWarning(
                profile.voidWarning().withMode(VoidWarningSettings.MODE_SIMPLE_HEIGHT));
        List<PaletteAction> actions = PaletteActions.available(state(config(profile)));

        List<PaletteAction> matches = PaletteActions.search(actions, "predicted");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/void_mode", actionId(matches.get(0)));
        assertTitleSetsValue(matches.get(0), "option.elytrapitchhelper.void_mode.predicted");
    }

    @Test
    void toggleSettingActionMutatesTheTargetProfile() {
        Config config = config(profile("Default", "default.json"));
        SettingsSearch.Entry firework = settingEntry(config.profile(0), ConfigCategory.GENERAL,
                "show_only_with_firework");
        SettingsSearch.Entry repeatDive = settingEntry(config.profile(0), ConfigCategory.AMPLITUDE,
                "amplitude_repeat_dive");
        SettingsSearch.Entry repeatClimb = settingEntry(config.profile(0), ConfigCategory.AMPLITUDE,
                "amplitude_repeat_climb");

        assertFalse(config.profile(0).visibility().showOnlyWithFirework());
        assertTrue(PaletteActions.toggleSetting(config, 0, firework));
        assertTrue(config.profile(0).visibility().showOnlyWithFirework());

        assertFalse(config.profile(0).amplitude().repeatDiveCue());
        assertTrue(config.profile(0).amplitude().repeatClimbCue());
        assertTrue(PaletteActions.toggleSetting(config, 0, repeatDive));
        assertTrue(PaletteActions.toggleSetting(config, 0, repeatClimb));
        assertTrue(config.profile(0).amplitude().repeatDiveCue());
        assertFalse(config.profile(0).amplitude().repeatClimbCue());
    }

    @Test
    void managedToggleRejectsAnIndexThatNoLongerExists(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        try (var store = new com.asphyxiamywife.elytrapitchhelper.client.ConfigStore(
                com.asphyxiamywife.elytrapitchhelper.config.NioConfigFileSystem.rootedAt(root))) {
            Config config = store.get();
            Config before = config.copy();
            SettingsSearch.Entry firework = settingEntry(config.profile(0), ConfigCategory.GENERAL,
                    "show_only_with_firework");
            assertFalse(PaletteActions.toggleSetting(config, config.profileCount(), firework));
            assertTrue(before.hasSameState(config));
        }
    }

    @Test
    void toggleSettingUsesTheSameClampedIndexForReadAndWrite() {
        Config config = config(
                profile("First", "first.json"),
                profile("Second", "second.json"));
        SettingsSearch.Entry firework = settingEntry(config.profile(1), ConfigCategory.GENERAL,
                "show_only_with_firework");

        assertTrue(PaletteActions.toggleSetting(config, 99, firework));

        assertFalse(config.profile(0).visibility().showOnlyWithFirework());
        assertTrue(config.profile(1).visibility().showOnlyWithFirework());
    }

    @Test
    void everyRegisteredToggleEntryMutatesItsTargetProfile() {
        Gson gson = new Gson();
        Profile catalogProfile = profile("Catalog", "catalog.json");
        List<SettingsSearch.Entry> toggles = new ArrayList<>();
        for (ConfigCategory category : ConfigCategory.values()) {
            toggles.addAll(SettingsSearch.entries(category, catalogProfile).stream()
                    .filter(entry -> entry.paletteOpenMode() == SettingsSearch.PaletteOpenMode.TOGGLE_SETTING)
                    .toList());
        }

        assertFalse(toggles.isEmpty());
        for (SettingsSearch.Entry toggle : toggles) {
            Config config = config(profile("Target", "target.json"));
            String before = gson.toJson(config.profile(0));

            assertTrue(PaletteActions.toggleSetting(config, 0, toggle), toggle.optionName());
            assertNotEquals(before, gson.toJson(config.profile(0)), toggle.optionName());
        }
    }

    @Test
    void prideSearchFindsConcreteColorSettingsOnly() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "pride editor");

        assertFalse(hasAction(actions, "palette/settings/open_pride_editor"));
        assertTrue(hasAction(matches, "palette/settings/line_color"));
        assertTrue(hasAction(matches, "palette/settings/amplitude_color"));
        assertTrue(hasAction(matches, "palette/settings/void_color"));
        assertFalse(hasAction(matches, "palette/settings/open_pride_editor"));
    }

    @Test
    void voidPrideSearchTargetsWarningColor() {
        List<PaletteAction> actions = PaletteActions.available(state(config(
                profile("Default", "default.json"))));

        List<PaletteAction> matches = PaletteActions.search(actions, "void pride");

        assertFalse(matches.isEmpty());
        assertEquals("palette/settings/void_color", actionId(matches.get(0)));
    }

    @Test
    void profileSwitchActionsAreDynamicAndSkipTheActiveProfile() {
        Config config = config(
                profile("Default", "default.json"),
                profile("Speedrun", "speedrun.json"));
        config.activeProfileIndex = 0;
        config.activeProfileFile = "default.json";

        List<PaletteAction> actions = PaletteActions.available(state(config));

        assertFalse(hasProfileSwitch(actions, "default.json"));
        assertTrue(hasProfileSwitch(actions, "speedrun.json"));
    }

    @Test
    void readOnlyConfigsExposeNavigationButHideEveryDirectMutation() {
        Profile defaults = profile("Default", "default.json");
        Profile modified = defaults.withLine(
                defaults.line().withLengthPixels(defaults.line().lengthPixels() + 1));
        Config config = ConfigTestFixtures.readOnly(config(
                modified, profile("Speedrun", "speedrun.json")));

        List<PaletteAction> actions = PaletteActions.available(state(config));

        assertTrue(hasAction(actions, "palette/settings/open_general"));
        assertTrue(hasAction(actions, "palette/profiles/open"));
        assertTrue(hasAction(actions, "palette/actions/open_json"));
        assertFalse(hasAction(actions, "palette/settings/show_only_with_firework"));
        assertFalse(hasAction(actions, "palette/settings/reset_line_length"));
        assertFalse(hasProfileSwitch(actions, "speedrun.json"));
        assertFalse(hasAction(actions, "palette/profiles/duplicate_current"));
        assertFalse(hasAction(actions, "palette/actions/toggle_eph"));
        assertFalse(hasAction(actions, "palette/actions/reset_current_profile"));
    }

    @Test
    void toggleSettingRefusesToMutateAReadOnlyConfig() {
        Config config = ConfigTestFixtures.readOnly(config(profile("Default", "default.json")));
        SettingsSearch.Entry firework = settingEntry(config.profile(0), ConfigCategory.GENERAL,
                "show_only_with_firework");

        assertFalse(PaletteActions.toggleSetting(config, 0, firework));
        assertFalse(config.profile(0).visibility().showOnlyWithFirework());
    }

    @Test
    void buildsDistinctIdentifiersForProfileFilesThatAreNotSpelledInAscii() {
        Config config = config(
                profile("Default", "default.json"),
                profile("Flight", "飞行.json"),
                profile("Gliding", "滑翔.json"));
        config.activeProfileIndex = 0;
        config.activeProfileFile = "default.json";

        List<PaletteAction> actions = PaletteActions.available(state(config));

        List<String> switchIds = actions.stream()
                .map(PaletteActionsTest::actionId)
                .filter(id -> id.startsWith("palette/profiles/switch_"))
                .toList();
        assertEquals(2, switchIds.size());
        assertEquals(2, switchIds.stream().distinct().count());
        for (String id : switchIds) {
            assertTrue(id.matches("[a-z0-9/._-]+"), id + " is not a legal resource location path");
        }
    }

    @Test
    void profileUsageRemainsSeparateAfterFilenameNormalization() {
        Config config = config(
                profile("Default", "default.json"),
                profile("Hyphen", "a-b.json"),
                profile("Underscore", "a_b.json"),
                profile("Accent", "café.json"),
                profile("Plain", "cafe.json"),
                profile("Long", "a".repeat(250) + ".json"));
        config.selectProfile(config.profileIndexByFileName("default.json"));
        List<String> ids = PaletteActions.available(state(config)).stream()
                .filter(action -> actionId(action).startsWith("palette/profiles/switch_"))
                .map(action -> action.id().toString()).toList();
        assertEquals(5, ids.size());
        assertEquals(5, ids.stream().distinct().count());
        long now = System.currentTimeMillis();
        for (String id : ids) {
            var usage = new com.asphyxiamywife.elytrapitchhelper.config.CommandPaletteUsageSettings()
                    .recordUse(id, now);
            assertTrue(usage.rankingBoost(id, now) > 0);
            for (String other : ids) {
                if (!id.equals(other)) {
                    assertEquals(0, usage.rankingBoost(other, now));
                }
            }
        }
        assertEquals(ids, PaletteActions.available(state(config.copy())).stream()
                .filter(action -> actionId(action).startsWith("palette/profiles/switch_"))
                .map(action -> action.id().toString()).toList());
    }

    private static boolean hasProfileSwitch(List<PaletteAction> actions, String fileName) {
        return actions.stream().anyMatch(action -> actionId(action).startsWith("palette/profiles/switch_")
                && action.keywords().contains(fileName));
    }

    private static boolean hasAction(List<PaletteAction> actions, String id) {
        return actions.stream().anyMatch(action -> action.id().toString().equals("elytrapitchhelper:" + id));
    }

    private static String actionId(PaletteAction action) {
        return action.id().toString().replace("elytrapitchhelper:", "");
    }

    private static void assertTitleMentions(PaletteAction action, String text) {
        assertTrue(action.title().getString().toLowerCase(java.util.Locale.ROOT).contains(text));
    }

    private static void assertTitleSetsValue(PaletteAction action, String valueKey) {
        assertTrue(action.title().getContents() instanceof TranslatableContents);
        TranslatableContents title = (TranslatableContents) action.title().getContents();
        assertEquals("palette.elytrapitchhelper.set_setting_to", title.getKey());
        assertTrue(componentArgsContainTranslationKey(title.getArgs(), valueKey));
    }

    private static boolean componentArgsContainTranslationKey(Object[] args, String valueKey) {
        for (Object arg : args) {
            if (arg instanceof Component component && componentContainsTranslationKey(component, valueKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean componentContainsTranslationKey(Component component, String valueKey) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals(valueKey)) {
                return true;
            }
            if (componentArgsContainTranslationKey(contents.getArgs(), valueKey)) {
                return true;
            }
        }
        return component.getSiblings().stream()
                .anyMatch(sibling -> componentContainsTranslationKey(sibling, valueKey));
    }

    private static SettingsSearch.Entry settingEntry(Profile profile, ConfigCategory category, String optionName) {
        return SettingsSearch.entries(category, profile).stream()
                .filter(entry -> entry.optionName().equals(optionName))
                .findFirst()
                .orElseThrow();
    }

    private static PaletteState state(Config config) {
        return new PaletteState(null, config, null);
    }

    private static Config config(Profile... profiles) {
        return ConfigTestFixtures.configWith(profiles);
    }

    private static Profile profile(String name, String fileName) {
        return ConfigTestFixtures.minimalProfile(name, fileName);
    }
}
