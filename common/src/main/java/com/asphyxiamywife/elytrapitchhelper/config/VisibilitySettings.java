package com.asphyxiamywife.elytrapitchhelper.config;

public record VisibilitySettings(
        boolean showOnlyWithFirework,
        boolean showInThirdPerson,
        boolean anyElytraGlide) {

    public VisibilitySettings() {
        this(ProfileDefaults.template().visibility());
    }

    private VisibilitySettings(VisibilitySettings defaults) {
        this(defaults.showOnlyWithFirework, defaults.showInThirdPerson, defaults.anyElytraGlide);
    }

    public VisibilitySettings withShowOnlyWithFirework(boolean value) {
        return new VisibilitySettings(value, showInThirdPerson, anyElytraGlide);
    }

    public VisibilitySettings withShowInThirdPerson(boolean value) {
        return new VisibilitySettings(showOnlyWithFirework, value, anyElytraGlide);
    }

    public VisibilitySettings withAnyElytraGlide(boolean value) {
        return new VisibilitySettings(showOnlyWithFirework, showInThirdPerson, value);
    }
}
