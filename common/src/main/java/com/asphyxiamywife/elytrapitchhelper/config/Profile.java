package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;

import static com.asphyxiamywife.elytrapitchhelper.config.Config.CURRENT_VERSION;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;

public record Profile(
        int version,
        String name,
        VisibilitySettings visibility,
        PitchSettings pitch,
        LineSettings line,
        AmplitudeSettings amplitude,
        VoidWarningSettings voidWarning,
        CommandPaletteAppearanceSettings commandPaletteAppearance,
        DiagnosticsSettings diagnostics,
        String fileName) {

    public Profile() {
        this(ProfileDefaults.template());
    }

    private Profile(Profile defaults) {
        this(defaults.version, null, defaults.visibility, defaults.pitch, defaults.line,
                defaults.amplitude, defaults.voidWarning, defaults.commandPaletteAppearance,
                defaults.diagnostics, null);
    }

    public Profile {
        if (visibility == null || pitch == null || line == null || amplitude == null
                || voidWarning == null || commandPaletteAppearance == null || diagnostics == null) {
            Profile defaults = ProfileDefaults.template();
            visibility = visibility == null ? defaults.visibility : visibility;
            pitch = pitch == null ? defaults.pitch : pitch;
            line = line == null ? defaults.line : line;
            amplitude = amplitude == null ? defaults.amplitude : amplitude;
            voidWarning = voidWarning == null ? defaults.voidWarning : voidWarning;
            commandPaletteAppearance = commandPaletteAppearance == null
                    ? defaults.commandPaletteAppearance : commandPaletteAppearance;
            diagnostics = diagnostics == null ? defaults.diagnostics : diagnostics;
        }
    }

    public Profile withVersion(int value) {
        return recreate(value, name, visibility, pitch, line, amplitude, voidWarning,
                commandPaletteAppearance, diagnostics, fileName);
    }

    public Profile withName(String value) {
        return recreate(version, value, visibility, pitch, line, amplitude, voidWarning,
                commandPaletteAppearance, diagnostics, fileName);
    }

    public Profile withVisibility(VisibilitySettings value) {
        return recreate(version, name, value, pitch, line, amplitude, voidWarning,
                commandPaletteAppearance, diagnostics, fileName);
    }

    public Profile withPitch(PitchSettings value) {
        return recreate(version, name, visibility, value, line, amplitude, voidWarning,
                commandPaletteAppearance, diagnostics, fileName);
    }

    public Profile withLine(LineSettings value) {
        return recreate(version, name, visibility, pitch, value, amplitude, voidWarning,
                commandPaletteAppearance, diagnostics, fileName);
    }

    public Profile withAmplitude(AmplitudeSettings value) {
        return recreate(version, name, visibility, pitch, line, value, voidWarning,
                commandPaletteAppearance, diagnostics, fileName);
    }

    public Profile withVoidWarning(VoidWarningSettings value) {
        return recreate(version, name, visibility, pitch, line, amplitude, value,
                commandPaletteAppearance, diagnostics, fileName);
    }

    public Profile withCommandPaletteAppearance(CommandPaletteAppearanceSettings value) {
        return recreate(version, name, visibility, pitch, line, amplitude, voidWarning, value,
                diagnostics, fileName);
    }

    public Profile withDiagnostics(DiagnosticsSettings value) {
        return recreate(version, name, visibility, pitch, line, amplitude, voidWarning,
                commandPaletteAppearance, value, fileName);
    }

    public Profile withFileName(String value) {
        return recreate(version, name, visibility, pitch, line, amplitude, voidWarning,
                commandPaletteAppearance, diagnostics, value);
    }

    Profile sanitized(RepairLog repairs, Profile defaults) {
        Profile fallback = defaults == null ? ProfileDefaults.template() : defaults;
        int repairedVersion = repairInt(repairs, "version", version,
                CURRENT_VERSION, CURRENT_VERSION, CURRENT_VERSION);
        Profile repaired = repairedVersion == version ? this : withVersion(repairedVersion);
        return SettingsRegistry.sanitize(repaired, fallback, repairs);
    }

    private static Profile recreate(int version, String name, VisibilitySettings visibility,
            PitchSettings pitch, LineSettings line, AmplitudeSettings amplitude,
            VoidWarningSettings voidWarning,
            CommandPaletteAppearanceSettings commandPaletteAppearance,
            DiagnosticsSettings diagnostics,
            String fileName) {
        return new Profile(version, name, visibility, pitch, line, amplitude, voidWarning,
                commandPaletteAppearance, diagnostics, fileName);
    }
}
