package com.asphyxiamywife.elytrapitchhelper.config;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

public final class FuzzGenerators {
    private FuzzGenerators() {
    }

    public static Profile arbitraryProfile(FuzzedDataProvider data) {
        VisibilitySettings visibility = new VisibilitySettings(
                data.consumeBoolean(), data.consumeBoolean(), data.consumeBoolean());
        PitchSettings pitch = new PitchSettings(data.consumeFloat(), data.consumeFloat(),
                data.consumeFloat(), data.consumeInt(), data.consumeFloat());
        LineSettings line = new LineSettings(data.consumeInt(), data.consumeInt(), data.consumeInt(),
                data.consumeBoolean(), data.consumeBoolean() ? null : data.consumeString(32),
                data.consumeBoolean() ? null : data.consumeInts(32), data.consumeInt());
        AmplitudeSettings amplitude = new AmplitudeSettings(
                data.consumeBoolean(), data.consumeInt(), data.consumeInt(), data.consumeInt(),
                data.consumeInt(), data.consumeFloat(), data.consumeFloat(),
                data.consumeBoolean(), data.consumeBoolean(), data.consumeBoolean(),
                data.consumeInt(), data.consumeInt(), data.consumeInt(), data.consumeBoolean(),
                data.consumeBoolean() ? null : data.consumeString(32),
                data.consumeBoolean() ? null : data.consumeInts(32));
        Map<String, Integer> overrides = new HashMap<>();
        if (!data.consumeBoolean()) {
            int overrideCount = data.consumeInt(0, 8);
            for (int i = 0; i < overrideCount; i++) {
                overrides.put(data.consumeString(32),
                        data.consumeBoolean() ? null : data.consumeInt());
            }
        }
        VoidWarningSettings voidWarning = new VoidWarningSettings(
                data.consumeBoolean(), data.consumeInt(), data.consumeFloat(), data.consumeInt(),
                data.consumeInt(), data.consumeBoolean(),
                data.consumeBoolean() ? null : data.consumeString(32),
                data.consumeBoolean() ? null : data.consumeInts(32),
                data.consumeBoolean(), data.consumeFloat(), data.consumeBoolean(), data.consumeInt(),
                overrides);
        return new Profile(data.consumeInt(),
                data.consumeBoolean() ? null : data.consumeString(64),
                visibility, pitch, line, amplitude, voidWarning,
                new CommandPaletteAppearanceSettings(), new DiagnosticsSettings(data.consumeBoolean()),
                data.consumeBoolean() ? null : data.consumeString(64));
    }

    private static float boundedFloat(FuzzedDataProvider data, float min, float max) {
        return switch (data.consumeInt(0, 7)) {
            case 0 -> min;
            case 1 -> max;
            default -> data.consumeRegularFloat(min, max);
        };
    }

    public static Config validConfig(FuzzedDataProvider data) {
        Config config = new Config();
        float downVelocity = boundedFloat(data, 0.1f, 5.0f);
        AmplitudeSettings amplitude = new AmplitudeSettings()
                .withTriggerMode(data.consumeInt(
                        Config.AMPLITUDE_TRIGGER_HEIGHT, Config.AMPLITUDE_TRIGGER_EITHER))
                .withDownBlocks(data.consumeInt(1, 1024))
                .withUpBlocks(data.consumeInt(1, 1024))
                .withToleranceBlocks(data.consumeInt(0, 128))
                .withDownVelocity(downVelocity)
                .withUpVelocity(boundedFloat(data, 0.0f, Math.max(0.0f, downVelocity - 0.1f)));
        VoidWarningSettings voidWarning = new VoidWarningSettings()
                .withMode(data.consumeInt(
                        VoidWarningSettings.MODE_PREDICTED_TIME, VoidWarningSettings.MODE_SIMPLE_HEIGHT))
                .withLookaheadSeconds(boundedFloat(data, 1.0f, 15.0f))
                .withSimpleWarningBlocks(data.consumeInt(1, 512));
        Profile profile = new Profile().withName("Fuzz").withFileName("fuzz.json")
                .withAmplitude(amplitude).withVoidWarning(voidWarning);
        config.profiles = new ArrayList<>();
        config.profiles.add(profile);
        config.activeProfileFile = profile.fileName();
        return config;
    }
}
