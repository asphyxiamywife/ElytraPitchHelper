package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;

record ToggleBinding(SettingSpec<?> spec) {
    boolean currentValue(Profile profile) {
        if (spec.control() instanceof SettingSpec.Toggle) {
            return getBoolean(profile);
        }
        if (spec.control() instanceof SettingSpec.Cycle cycle) {
            return getInteger(profile) == cycle.max();
        }
        throw new IllegalStateException("Setting is not toggleable: " + spec.id());
    }

    Profile apply(Profile profile) {
        if (spec.control() instanceof SettingSpec.Toggle) {
            return applyBoolean(profile, !getBoolean(profile));
        }
        if (spec.control() instanceof SettingSpec.Cycle cycle) {
            int current = getInteger(profile);
            return applyInteger(profile, current >= cycle.max() ? cycle.min() : current + 1);
        }
        throw new IllegalStateException("Setting is not toggleable: " + spec.id());
    }

    String nextValueKey(Profile profile) {
        if (spec.control() instanceof SettingSpec.Toggle toggle && toggle.nextValueKey() != null) {
            return toggle.nextValueKey().apply(profile);
        }
        if (spec.control() instanceof SettingSpec.Cycle cycle && cycle.nextValueKey() != null) {
            return cycle.nextValueKey().apply(profile);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean getBoolean(Profile profile) {
        return ((SettingSpec<Boolean>) spec).get().apply(profile);
    }

    @SuppressWarnings("unchecked")
    private Profile applyBoolean(Profile profile, boolean value) {
        return ((SettingSpec<Boolean>) spec).apply(profile, value);
    }

    @SuppressWarnings("unchecked")
    private int getInteger(Profile profile) {
        return ((SettingSpec<Integer>) spec).get().apply(profile);
    }

    @SuppressWarnings("unchecked")
    private Profile applyInteger(Profile profile, int value) {
        return ((SettingSpec<Integer>) spec).apply(profile, value);
    }
}
