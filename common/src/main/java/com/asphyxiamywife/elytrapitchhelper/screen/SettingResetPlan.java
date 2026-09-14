package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingReset;

import java.util.HashMap;
import java.util.Optional;

final class SettingResetPlan {
    private SettingResetPlan() {}

    static Optional<Profile> setting(Profile current, String settingId) {
        return SettingReset.forSetting(settingId).flatMap(unit -> setting(current, unit));
    }

    static Optional<Profile> setting(Profile current, SettingReset.Unit unit) {
        Profile defaults = Config.defaultProfileTemplate();
        return SettingReset.isModified(unit, current, defaults)
                ? Optional.of(SettingReset.reset(unit, current, defaults))
                : Optional.empty();
    }

    static Optional<Profile> voidDimension(Profile current, String dimension) {
        if (dimension == null || !current.voidWarning().dimensionYOverrides().containsKey(dimension)) {
            return Optional.empty();
        }
        var overrides = new HashMap<>(current.voidWarning().dimensionYOverrides());
        overrides.remove(dimension);
        return Optional.of(current.withVoidWarning(current.voidWarning().withDimensionYOverrides(overrides)));
    }
}
