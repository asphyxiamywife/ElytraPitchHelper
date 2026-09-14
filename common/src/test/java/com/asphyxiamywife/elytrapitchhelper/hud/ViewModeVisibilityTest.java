package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.flight.ViewMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ViewModeVisibilityTest {
    @Test
    void bothThirdPersonPosturesFollowTheVisibilitySetting() {
        Config config = new Config();
        ConfigTestFixtures.updateActiveProfile(config,
                p -> p.withVisibility(p.visibility().withShowInThirdPerson(false)));
        assertTrue(HudVisibility.canRender(config, ViewMode.FIRST_PERSON, true, true, 1));
        assertFalse(HudVisibility.canRender(config, ViewMode.THIRD_PERSON_BACK, true, true, 1));
        assertFalse(HudVisibility.canRender(config, ViewMode.THIRD_PERSON_FRONT, true, true, 1));

        ConfigTestFixtures.updateActiveProfile(config,
                p -> p.withVisibility(p.visibility().withShowInThirdPerson(true)));
        for (ViewMode viewMode : ViewMode.values()) {
            assertTrue(HudVisibility.canRender(config, viewMode, true, true, 1));
            assertFalse(HudVisibility.canRender(config, viewMode, true, true, 0));
        }
    }
}
