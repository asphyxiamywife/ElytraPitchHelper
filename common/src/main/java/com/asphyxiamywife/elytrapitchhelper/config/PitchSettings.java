package com.asphyxiamywife.elytrapitchhelper.config;

public record PitchSettings(
        float targetUpMinecraft,
        float targetDownMinecraft,
        float toleranceDegrees,
        int maxOffsetPixels,
        float offsetPerDegree) {

    public PitchSettings() {
        this(ProfileDefaults.template().pitch());
    }

    private PitchSettings(PitchSettings defaults) {
        this(defaults.targetUpMinecraft, defaults.targetDownMinecraft, defaults.toleranceDegrees,
                defaults.maxOffsetPixels, defaults.offsetPerDegree);
    }

    public PitchSettings withTargetUpMinecraft(float value) {
        return new PitchSettings(value, targetDownMinecraft, toleranceDegrees, maxOffsetPixels, offsetPerDegree);
    }

    public PitchSettings withTargetDownMinecraft(float value) {
        return new PitchSettings(targetUpMinecraft, value, toleranceDegrees, maxOffsetPixels, offsetPerDegree);
    }

    public PitchSettings withToleranceDegrees(float value) {
        return new PitchSettings(targetUpMinecraft, targetDownMinecraft, value, maxOffsetPixels, offsetPerDegree);
    }

    public PitchSettings withMaxOffsetPixels(int value) {
        return new PitchSettings(targetUpMinecraft, targetDownMinecraft, toleranceDegrees, value, offsetPerDegree);
    }

    public PitchSettings withOffsetPerDegree(float value) {
        return new PitchSettings(targetUpMinecraft, targetDownMinecraft, toleranceDegrees, maxOffsetPixels, value);
    }
}
