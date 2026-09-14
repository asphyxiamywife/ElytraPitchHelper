package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.SectionCollapseSettings;
import org.junit.jupiter.api.Test;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileEditorPlanTest {
    private static final Profile PROFILE = new Profile();
    private static final SectionCollapseSettings EXPANDED = new SectionCollapseSettings().expandedAll();
    private static final SectionCollapseSettings DEFAULTS = new SectionCollapseSettings();

    private static final Function<String, String> TRANSLATOR = key -> {
        for (SettingSpec<?> spec : SettingsRegistry.all()) {
            if (key.equals(spec.labelKey())) {
                return spec.id().replace('_', ' ');
            }
        }
        return "";
    };

    private static List<ProfileEditorPlan.RowPlan> plan(String query, SectionCollapseSettings collapse) {
        return ProfileEditorPlan.plan(PROFILE, query, collapse, TRANSLATOR);
    }

    private static boolean hasSetting(List<ProfileEditorPlan.RowPlan> plan, String id) {
        return plan.stream().anyMatch(row -> id.equals(row.settingId()));
    }

    private static ProfileEditorPlan.MatchKind kindOf(List<ProfileEditorPlan.RowPlan> plan, String id) {
        return plan.stream().filter(row -> id.equals(row.settingId())).findFirst().orElseThrow().kind();
    }

    private static int headerIndex(List<ProfileEditorPlan.RowPlan> plan, ConfigCategory section) {
        for (int index = 0; index < plan.size(); index++) {
            ProfileEditorPlan.RowPlan row = plan.get(index);
            if (row.settingId() == null && row.section() == section) {
                return index;
            }
        }
        return -1;
    }

    @Test
    void everySectionContributesExactlyOneHeader() {
        List<ProfileEditorPlan.RowPlan> plan = plan("", EXPANDED);
        long headers = plan.stream().filter(row -> row.settingId() == null).count();

        assertEquals(ConfigCategory.values().length, headers);
    }

    @Test
    void theDefaultViewIsOneRowPerSection() {
        List<ProfileEditorPlan.RowPlan> folded = plan("", DEFAULTS);

        assertEquals(ConfigCategory.values().length, folded.size());
        assertTrue(folded.stream().allMatch(row -> row.settingId() == null));
        assertFalse(hasSetting(folded, "void_warning"));
        assertFalse(hasSetting(folded, "target_up"));
        assertTrue(folded.size() < plan("", EXPANDED).size());
    }

    @Test
    void searchOpensFoldedSectionsSoMatchesAreNotHidden() {
        List<ProfileEditorPlan.RowPlan> found = plan("lookahead", DEFAULTS);

        assertTrue(hasSetting(found, "void_lookahead"),
                "a folded section must still surrender its matches to a search");
    }

    @Test
    void aNameMatchReadsAsDirectAndATooltipOnlyMatchAsContext() {
        List<ProfileEditorPlan.RowPlan> found = plan("lookahead", EXPANDED);

        assertEquals(ProfileEditorPlan.MatchKind.DIRECT, kindOf(found, "void_lookahead"));
        assertEquals(ProfileEditorPlan.MatchKind.CONTEXT, kindOf(found, "void_mode"));
        assertEquals(ProfileEditorPlan.MatchKind.CONTEXT, kindOf(found, "void_warning"));
    }

    @Test
    void aMatchDragsItsParentsAlongSoIndentationStillMeansSomething() {
        List<ProfileEditorPlan.RowPlan> found = plan("void custom tolerance", EXPANDED);

        assertEquals(ProfileEditorPlan.MatchKind.DIRECT, kindOf(found, "void_custom_tolerance"));
        assertTrue(hasSetting(found, "void_tolerance_override"), "parent shown for context");
        assertTrue(hasSetting(found, "void_warning"), "grandparent shown for context");
    }

    @Test
    void everythingIsDirectWhenNothingIsSearched() {
        assertTrue(plan("", EXPANDED).stream()
                .allMatch(row -> row.kind() == ProfileEditorPlan.MatchKind.DIRECT));
    }

    @Test
    void aSectionWithNoMatchesDropsOutEntirely() {
        List<ProfileEditorPlan.RowPlan> found = plan("lookahead", EXPANDED);

        assertFalse(found.stream().anyMatch(row -> row.section() == ConfigCategory.PITCH),
                "a section with nothing matching should not leave a bare header behind");
    }

    @Test
    void rowCountAndHeaderIndexAgreeWithThePlan() {
        for (SectionCollapseSettings collapse : List.of(EXPANDED, DEFAULTS)) {
            for (String query : List.of("", "color", "lookahead")) {
                List<ProfileEditorPlan.RowPlan> plan = ProfileEditorPlan.plan(PROFILE, query, collapse, TRANSLATOR);
                assertEquals(plan.size(), ProfileEditorPlan.rowCount(PROFILE, query, collapse, TRANSLATOR));
                for (ConfigCategory section : ConfigCategory.values()) {
                    assertEquals(headerIndex(plan, section),
                            ProfileEditorPlan.headerRowIndex(PROFILE, query, collapse, section, TRANSLATOR));
                }
            }
        }
    }
}
