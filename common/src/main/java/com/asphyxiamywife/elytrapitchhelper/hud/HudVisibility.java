package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.flight.ViewMode;

final class HudVisibility {
    private HudVisibility() {
    }

    static boolean canRender(Config config, boolean firstPerson, boolean hasUsableElytra,
            boolean hasFireworkRocket, int fallFlyingTicks) {
        return canRender(config, firstPerson ? ViewMode.FIRST_PERSON : ViewMode.THIRD_PERSON_BACK,
                hasUsableElytra, hasFireworkRocket, fallFlyingTicks);
    }

    static boolean canRender(Config config, ViewMode viewMode, boolean hasUsableElytra, boolean hasFireworkRocket,
            int fallFlyingTicks) {
        if (!config.visibility().showInThirdPerson() && viewMode != ViewMode.FIRST_PERSON) {
            return false;
        }
        if (!hasUsableElytra) {
            return false;
        }
        if (config.visibility().showOnlyWithFirework() && !hasFireworkRocket) {
            return false;
        }
        return fallFlyingTicks > 0;
    }
}
