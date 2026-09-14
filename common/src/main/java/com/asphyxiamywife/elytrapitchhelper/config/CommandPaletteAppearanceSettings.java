package com.asphyxiamywife.elytrapitchhelper.config;

public record CommandPaletteAppearanceSettings(
        int shadowOpacity,
        int blurAmount,
        int accentColorRgb,
        int baseColorRgb) {

    public CommandPaletteAppearanceSettings() {
        this(ProfileDefaults.template().commandPaletteAppearance());
    }

    private CommandPaletteAppearanceSettings(CommandPaletteAppearanceSettings defaults) {
        this(defaults.shadowOpacity, defaults.blurAmount, defaults.accentColorRgb, defaults.baseColorRgb);
    }

    public CommandPaletteAppearanceSettings withShadowOpacity(int value) {
        return new CommandPaletteAppearanceSettings(value, blurAmount, accentColorRgb, baseColorRgb);
    }

    public CommandPaletteAppearanceSettings withBlurAmount(int value) {
        return new CommandPaletteAppearanceSettings(shadowOpacity, value, accentColorRgb, baseColorRgb);
    }

    public CommandPaletteAppearanceSettings withAccentColorRgb(int value) {
        return new CommandPaletteAppearanceSettings(shadowOpacity, blurAmount, value, baseColorRgb);
    }

    public CommandPaletteAppearanceSettings withBaseColorRgb(int value) {
        return new CommandPaletteAppearanceSettings(shadowOpacity, blurAmount, accentColorRgb, value);
    }
}
