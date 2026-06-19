package com.asphyxiamywife.elytrapitchhelper.config;

public final class VisibilitySettings {
    public boolean showOnlyWithFirework = false;
    public boolean showInThirdPerson = false;
    public boolean anyElytraGlide = false;

    public VisibilitySettings copy() {
        VisibilitySettings settings = new VisibilitySettings();
        settings.copyFrom(this);
        return settings;
    }

    public void copyFrom(VisibilitySettings other) {
        if (other == null) {
            return;
        }
        showOnlyWithFirework = other.showOnlyWithFirework;
        showInThirdPerson = other.showInThirdPerson;
        anyElytraGlide = other.anyElytraGlide;
    }
}
