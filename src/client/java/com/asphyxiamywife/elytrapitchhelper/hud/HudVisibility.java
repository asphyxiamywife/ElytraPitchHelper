package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;

final class HudVisibility {
    private HudVisibility() {
    }

    static boolean canRender(Config config, boolean firstPerson, boolean hasUsableElytra, boolean hasFireworkRocket,
            int fallFlyingTicks) {
        if (!config.showInThirdPerson && !firstPerson) {
            return false;
        }
        if (!hasUsableElytra) {
            return false;
        }
        if (config.showOnlyWithFirework && !hasFireworkRocket) {
            return false;
        }
        return fallFlyingTicks > 0;
    }
}
