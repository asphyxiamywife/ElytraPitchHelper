package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.screen.ConfigCategory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SectionCollapseSettingsTest {
    @Test
    void afreshConfigFoldsEverything() {
        SectionCollapseSettings settings = new SectionCollapseSettings();

        for (ConfigCategory section : ConfigCategory.values()) {
            assertTrue(settings.isCollapsed(section), () -> section + " should start folded");
        }
        assertFalse(settings.collapsed().isEmpty());
    }

    @Test
    void togglingSwitchesOneSectionAndLeavesTheRestAlone() {
        SectionCollapseSettings opened = new SectionCollapseSettings().toggled(ConfigCategory.VOID);

        assertFalse(opened.isCollapsed(ConfigCategory.VOID));
        assertTrue(opened.isCollapsed(ConfigCategory.PITCH));
        assertTrue(opened.toggled(ConfigCategory.VOID).isCollapsed(ConfigCategory.VOID));
    }

    @Test
    void expandingAllLeavesNothingFolded() {
        SectionCollapseSettings settings = new SectionCollapseSettings().expandedAll();

        for (ConfigCategory section : ConfigCategory.values()) {
            assertFalse(settings.isCollapsed(section));
        }
        assertTrue(settings.collapsed().isEmpty());
    }

    @Test
    void unknownSectionNamesAreRepairedAway() {
        RepairLog repairs = new RepairLog("test");
        SectionCollapseSettings settings =
                new SectionCollapseSettings(Set.of("VOID", "SECTION_FROM_THE_FUTURE")).sanitized(repairs);

        assertTrue(settings.isCollapsed(ConfigCategory.VOID));
        assertFalse(settings.collapsed().contains("SECTION_FROM_THE_FUTURE"));
    }

    @Test
    void aMissingSetIsTreatedAsNothingFolded() {
        SectionCollapseSettings settings = new SectionCollapseSettings(null);

        assertTrue(settings.collapsed().isEmpty());
    }
}
