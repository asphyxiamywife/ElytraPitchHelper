package com.asphyxiamywife.elytrapitchhelper.config.schema;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SettingReset {
    private SettingReset() {
    }

    public record Unit(String id, List<SettingSpec<?>> members) {
        public Unit {
            Objects.requireNonNull(id, "id");
            members = List.copyOf(members);
            if (members.isEmpty()) {
                throw new IllegalArgumentException("A reset unit must own at least one setting");
            }
        }
    }

    public static Optional<Unit> forSetting(String id) {
        SettingSpec<?> owner = SettingsRegistry.byId(id);
        if (owner == null || owner.control() instanceof SettingSpec.Hidden<?>
                || owner.control() instanceof SettingSpec.Dynamic<?>) {
            return Optional.empty();
        }
        List<SettingSpec<?>> members = new ArrayList<>();
        members.add(owner);
        for (String companionId : owner.companions()) {
            SettingSpec<?> companion = SettingsRegistry.byId(companionId);
            if (companion == null) {
                throw new IllegalStateException("Unknown companion setting: " + companionId);
            }
            members.add(companion);
        }
        return Optional.of(new Unit(owner.id(), members));
    }

    public static boolean isModified(Unit unit, Profile profile, Profile defaults) {
        for (SettingSpec<?> member : unit.members()) {
            if (!Objects.deepEquals(value(member, profile), value(member, defaults))) {
                return true;
            }
        }
        return false;
    }

    public static Profile reset(Unit unit, Profile profile, Profile defaults) {
        Profile result = profile;
        for (SettingSpec<?> member : unit.members()) {
            result = applyDefault(member, result, defaults);
        }
        return result;
    }

    private static Object value(SettingSpec<?> spec, Profile profile) {
        return spec.get().apply(profile);
    }

    @SuppressWarnings("unchecked")
    private static <T> Profile applyDefault(SettingSpec<?> raw, Profile profile, Profile defaults) {
        SettingSpec<T> spec = (SettingSpec<T>) raw;
        return spec.apply(profile, spec.get().apply(defaults));
    }
}
