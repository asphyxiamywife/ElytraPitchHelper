package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HudVisibilityTest {
    @Test
    void blocksThirdPersonUnlessConfigAllowsIt() {
        Config config = config();

        assertFalse(HudVisibility.canRender(config, false, true, true, 1));

        config.showInThirdPerson = true;
        assertTrue(HudVisibility.canRender(config, false, true, true, 1));
    }

    @Test
    void blocksMissingElytraAndGroundedPlayer() {
        Config config = config();

        assertFalse(HudVisibility.canRender(config, true, false, true, 1));
        assertFalse(HudVisibility.canRender(config, true, true, true, 0));
    }

    @Test
    void fireworkRequirementOnlyAppliesWhenEnabled() {
        Config config = config();

        assertTrue(HudVisibility.canRender(config, true, true, false, 1));

        config.showOnlyWithFirework = true;
        assertFalse(HudVisibility.canRender(config, true, true, false, 1));
        assertTrue(HudVisibility.canRender(config, true, true, true, 1));
    }

    private static Config config() {
        Config config = new Config();
        config.showInThirdPerson = false;
        config.showOnlyWithFirework = false;
        return config;
    }
}
