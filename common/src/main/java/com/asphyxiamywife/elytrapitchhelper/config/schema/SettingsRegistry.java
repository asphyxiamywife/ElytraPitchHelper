package com.asphyxiamywife.elytrapitchhelper.config.schema;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigJsonReader;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair;
import com.asphyxiamywife.elytrapitchhelper.config.LineSettings;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.RepairLog;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import com.asphyxiamywife.elytrapitchhelper.screen.ConfigCategory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec.BOOLEAN_JSON;
import static com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec.FLOAT_JSON;
import static com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec.INT_ARRAY_JSON;
import static com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec.INT_JSON;
import static com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec.STRING_JSON;

public final class SettingsRegistry {
    private static final float VELOCITY_STEP = 0.05f;
    private static final Predicate<Profile> ALWAYS = profile -> true;
    public static final int MAX_DEPENDENCY_DEPTH = 2;
    private static final SettingSpec.Sanitizer<Boolean> BOOLEAN = (repairs, field, value, fallback) -> value;
    private static final SettingSpec.Sanitizer<Integer> COLOR = ConfigRepair::repairColor;
    private static final SettingSpec.Sanitizer<String> PRIDE_FLAG = ConfigRepair::repairPrideFlag;
    private static final SettingSpec.Sanitizer<int[]> PRIDE_COLORS = ConfigRepair::repairCustomPrideColors;

    private static final SettingSpec.JsonCodec<Map<String, Integer>> DIMENSION_OVERRIDES_JSON =
            new SettingSpec.JsonCodec<>() {
                @Override
                public Map<String, Integer> read(JsonObject json, String field, Map<String, Integer> fallback,
                        RepairLog repairs) {
                    JsonElement element = json.get(field);
                    if (element == null || element.isJsonNull()) {
                        return fallback == null ? new HashMap<>() : new HashMap<>(fallback);
                    }
                    if (!element.isJsonObject()) {
                        ConfigRepair.repair(repairs, field, element, fallback);
                        return fallback == null ? new HashMap<>() : new HashMap<>(fallback);
                    }
                    Map<String, Integer> result = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        Integer value = ConfigJsonReader.parseExactInt(entry.getValue());
                        if (VoidWarningSettings.isValidDimensionId(entry.getKey()) && value != null) {
                            result.put(entry.getKey(), value);
                        } else {
                            ConfigRepair.repair(repairs, field + "." + entry.getKey(),
                                    entry.getValue(), "ignored");
                        }
                    }
                    return result;
                }

                @Override
                public void write(JsonObject json, String field, Map<String, Integer> value) {
                    JsonObject object = new JsonObject();
                    if (value != null) {
                        value.entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .forEach(entry -> object.addProperty(entry.getKey(), entry.getValue()));
                    }
                    json.add(field, object);
                }
            };

    private static final List<SettingSpec<?>> ALL = createSpecs();
    private static final Map<String, SettingSpec<?>> BY_ID = indexById(ALL);

    private SettingsRegistry() {
    }

    public static List<SettingSpec<?>> all() {
        return ALL;
    }

    public static List<SettingSpec<?>> serialized() {
        return ALL.stream().filter(spec -> spec.json() != null).toList();
    }

    public static List<SettingSpec<?>> visible(ConfigCategory category, Profile profile) {
        return ALL.stream()
                .filter(spec -> spec.category() == category)
                .filter(spec -> spec.isVisible(profile))
                .toList();
    }

    public static SettingSpec<?> byId(String id) {
        return BY_ID.get(id);
    }

    public static Profile sanitize(Profile profile, Profile defaults, RepairLog repairs) {
        Profile result = profile;
        for (SettingSpec<?> spec : serialized()) {
            result = sanitizeUnchecked(spec, result, defaults, repairs);
        }
        if (result.amplitude().upVelocity() >= result.amplitude().downVelocity()) {
            float oldValue = result.amplitude().upVelocity();
            float downVelocity = result.amplitude().downVelocity();
            float repaired = Math.max(0.0f, downVelocity - VELOCITY_STEP);
            result = result.withAmplitude(result.amplitude().withUpVelocity(repaired));
            ConfigRepair.repair(repairs, "amplitude.upVelocity", oldValue, repaired);
        }
        return result;
    }

    public static Profile read(JsonObject root, Profile profile, Profile defaults, RepairLog repairs) {
        Map<String, JsonObject> sections = new HashMap<>();
        Profile result = profile;
        for (SettingSpec<?> spec : serialized()) {
            if (!sections.containsKey(spec.jsonSection())) {
                sections.put(spec.jsonSection(), section(root, spec.jsonSection(), repairs));
            }
            JsonObject section = sections.get(spec.jsonSection());
            result = readUnchecked(spec, root, section, result, defaults, repairs);
        }
        return result;
    }

    public static JsonObject write(Profile profile) {
        JsonObject root = new JsonObject();
        root.addProperty("version", profile.version());
        root.addProperty("name", profile.name());
        Map<String, JsonObject> sections = new LinkedHashMap<>();
        for (SettingSpec<?> spec : serialized()) {
            JsonObject section = sections.computeIfAbsent(spec.jsonSection(), ignored -> new JsonObject());
            writeUnchecked(spec, section, profile);
        }
        sections.forEach(root::add);
        return root;
    }

    private static JsonObject section(JsonObject root, String name, RepairLog repairs) {
        JsonElement element = root.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            ConfigRepair.repair(repairs, name, element, "object");
            return null;
        }
        return element.getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static <T> Profile sanitizeUnchecked(SettingSpec<?> raw, Profile profile, Profile defaults,
            RepairLog repairs) {
        return ((SettingSpec<T>) raw).sanitize(profile, defaults, repairs);
    }

    @SuppressWarnings("unchecked")
    private static <T> Profile readUnchecked(SettingSpec<?> raw, JsonObject root, JsonObject section,
            Profile profile, Profile defaults, RepairLog repairs) {
        return ((SettingSpec<T>) raw).read(root, section, profile, defaults, repairs);
    }

    @SuppressWarnings("unchecked")
    private static <T> void writeUnchecked(SettingSpec<?> raw, JsonObject section, Profile profile) {
        ((SettingSpec<T>) raw).write(section, profile);
    }

    private static List<SettingSpec<?>> createSpecs() {
        List<SettingSpec<?>> specs = new ArrayList<>();

        specs.add(toggle("show_only_with_firework", ConfigCategory.GENERAL,
                profile -> profile.visibility().showOnlyWithFirework(),
                (profile, value) -> profile.withVisibility(
                        profile.visibility().withShowOnlyWithFirework(value)),
                "visibility", "showOnlyWithFirework", "showOnlyWithFirework"));
        specs.add(toggle("show_in_third_person", ConfigCategory.GENERAL,
                profile -> profile.visibility().showInThirdPerson(),
                (profile, value) -> profile.withVisibility(profile.visibility().withShowInThirdPerson(value)),
                "visibility", "showInThirdPerson", "showInThirdPerson"));
        specs.add(toggle("flight_detection", ConfigCategory.GENERAL,
                profile -> profile.visibility().anyElytraGlide(),
                (profile, value) -> profile.withVisibility(profile.visibility().withAnyElytraGlide(value)),
                "visibility", "anyElytraGlide", "anyElytraGlide",
                profile -> profile.visibility().anyElytraGlide()
                        ? "option.elytrapitchhelper.flight_detection.equipment_check"
                        : "option.elytrapitchhelper.flight_detection.any_elytra_glide"));

        specs.add(floatSlider("target_up", ConfigCategory.PITCH, -90.0f, 0.0f, 1.0f, "deg",
                profile -> profile.pitch().targetUpMinecraft(),
                (profile, value) -> profile.withPitch(profile.pitch().withTargetUpMinecraft(value)),
                "pitch", "targetUpMinecraft", "targetUpMinecraft"));
        specs.add(floatSlider("target_down", ConfigCategory.PITCH, 0.0f, 90.0f, 1.0f, "deg",
                profile -> profile.pitch().targetDownMinecraft(),
                (profile, value) -> profile.withPitch(profile.pitch().withTargetDownMinecraft(value)),
                "pitch", "targetDownMinecraft", "targetDownMinecraft"));
        specs.add(floatSlider("tolerance", ConfigCategory.PITCH, 1.0f, 45.0f, 0.5f, "deg",
                profile -> profile.pitch().toleranceDegrees(),
                (profile, value) -> profile.withPitch(profile.pitch().withToleranceDegrees(value)),
                "pitch", "toleranceDegrees", "toleranceDegrees"));
        specs.add(intSlider("max_offset", ConfigCategory.PITCH, 0, 200, 1, "px",
                profile -> profile.pitch().maxOffsetPixels(),
                (profile, value) -> profile.withPitch(profile.pitch().withMaxOffsetPixels(value)),
                "pitch", "maxOffsetPixels", "maxOffsetPixels"));
        specs.add(floatSlider("offset_per_degree", ConfigCategory.PITCH, 0.25f, 10.0f, 0.25f,
                "px/deg", profile -> profile.pitch().offsetPerDegree(),
                (profile, value) -> profile.withPitch(profile.pitch().withOffsetPerDegree(value)),
                "pitch", "offsetPerDegree", "offsetPerDegree"));

        specs.add(toggle("amplitude_helper", ConfigCategory.AMPLITUDE,
                profile -> profile.amplitude().enabled(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withEnabled(value)),
                "amplitude", "enabled", "amplitudeHelperEnabled"));
        specs.add(cycle("amplitude_trigger_mode", ConfigCategory.AMPLITUDE,
                Config.AMPLITUDE_TRIGGER_HEIGHT, Config.AMPLITUDE_TRIGGER_EITHER, false, null,
                profile -> profile.amplitude().triggerMode(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withTriggerMode(value)),
                "amplitude", "triggerMode", "amplitudeTriggerMode"));
        specs.add(intSlider("amplitude_down", ConfigCategory.AMPLITUDE, 10, 300, 1, "blocks",
                profile -> profile.amplitude().downBlocks(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withDownBlocks(value)),
                "amplitude", "downBlocks", "amplitudeDownBlocks", 1, 1024));
        specs.add(intSlider("amplitude_up", ConfigCategory.AMPLITUDE, 10, 300, 1, "blocks",
                profile -> profile.amplitude().upBlocks(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withUpBlocks(value)),
                "amplitude", "upBlocks", "amplitudeUpBlocks", 1, 1024));
        specs.add(intSlider("amplitude_tolerance", ConfigCategory.AMPLITUDE, 0, 30, 1, "blocks",
                profile -> profile.amplitude().toleranceBlocks(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withToleranceBlocks(value)),
                "amplitude", "toleranceBlocks", "amplitudeToleranceBlocks", 0, 128));
        specs.add(floatSlider("amplitude_down_velocity", ConfigCategory.AMPLITUDE,
                0.1f, 5.0f, VELOCITY_STEP, "b/t",
                profile -> profile.amplitude().downVelocity(),
                (profile, value) -> {
                    var amplitude = profile.amplitude().withDownVelocity(value);
                    if (amplitude.upVelocity() >= value) {
                        amplitude = amplitude.withUpVelocity(Math.max(0.0f, value - VELOCITY_STEP));
                    }
                    return profile.withAmplitude(amplitude);
                },
                "amplitude", "downVelocity", "amplitudeDownVelocity", 0.1f, 5.0f));
        specs.add(floatSlider("amplitude_up_velocity", ConfigCategory.AMPLITUDE,
                0.0f, 4.95f, VELOCITY_STEP, "b/t",
                profile -> profile.amplitude().upVelocity(),
                (profile, value) -> {
                    float coordinatedUp = Math.min(value, 5.0f - VELOCITY_STEP);
                    var amplitude = profile.amplitude().withUpVelocity(coordinatedUp);
                    if (coordinatedUp >= amplitude.downVelocity()) {
                        amplitude = amplitude.withDownVelocity(coordinatedUp + VELOCITY_STEP);
                    }
                    return profile.withAmplitude(amplitude);
                },
                "amplitude", "upVelocity", "amplitudeUpVelocity", 0.0f, 4.95f));
        specs.add(toggle("amplitude_repeat_dive", ConfigCategory.AMPLITUDE,
                profile -> profile.amplitude().repeatDiveCue(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withRepeatDiveCue(value)),
                "amplitude", "repeatDiveCue", "amplitudeRepeatDiveCue",
                (Function<Profile, String>) null,
                "repeat", "dive", "down line", "second cue"));
        specs.add(toggle("amplitude_repeat_climb", ConfigCategory.AMPLITUDE,
                profile -> profile.amplitude().repeatClimbCue(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withRepeatClimbCue(value)),
                "amplitude", "repeatClimbCue", "amplitudeRepeatClimbCue",
                (Function<Profile, String>) null,
                "repeat", "climb", "up line", "second cue"));
        specs.add(toggle("motion_glyphs", ConfigCategory.AMPLITUDE,
                profile -> profile.amplitude().motionGlyphsEnabled(),
                (profile, value) -> profile.withAmplitude(
                        profile.amplitude().withMotionGlyphsEnabled(value)),
                "amplitude", "motionGlyphsEnabled", null,
                (Function<Profile, String>) null,
                "glyph", "gesture", "pull up", "release", "turn direction"));
        specs.add(intSlider("pull_up_glyph_duration", ConfigCategory.AMPLITUDE,
                200, 3_000, 50, "ms",
                profile -> profile.amplitude().pullUpGlyphMillis(),
                (profile, value) -> profile.withAmplitude(
                        profile.amplitude().withPullUpGlyphMillis(value)),
                "amplitude", "pullUpGlyphMillis", null));
        specs.add(intSlider("release_down_glyph_duration", ConfigCategory.AMPLITUDE,
                400, 5_000, 50, "ms",
                profile -> profile.amplitude().releaseDownGlyphMillis(),
                (profile, value) -> profile.withAmplitude(
                        profile.amplitude().withReleaseDownGlyphMillis(value)),
                "amplitude", "releaseDownGlyphMillis", null));

        specs.add(intSlider("line_length", ConfigCategory.LINE, 2, 200, 1, "px",
                profile -> profile.line().lengthPixels(),
                (profile, value) -> profile.withLine(profile.line().withLengthPixels(value)),
                "line", "lengthPixels", "lineLengthPixels"));
        specs.add(intSlider("line_width", ConfigCategory.LINE, 1, 20, 1, "px",
                profile -> profile.line().widthPixels(),
                (profile, value) -> profile.withLine(profile.line().withWidthPixels(value)),
                "line", "widthPixels", "lineWidthPixels"));
        specs.add(intSlider("cue_flash_intensity", ConfigCategory.LINE, LineSettings.MIN_CUE_FLASH_INTENSITY,
                LineSettings.MAX_CUE_FLASH_INTENSITY, 5, "%",
                profile -> profile.line().cueFlashIntensity(),
                (profile, value) -> profile.withLine(profile.line().withCueFlashIntensity(value)),
                "line", "cueFlashIntensity", null));
        specs.add(color("line_color", ConfigCategory.LINE, false,
                profile -> profile.line().colorRgb(),
                (profile, value) -> profile.withLine(profile.line().withColorRgb(value)),
                profile -> new SettingSpec.ColorValue(profile.line().colorRgb(), profile.line().prideEnabled(),
                        profile.line().prideFlag(), profile.line().customPrideColors()),
                (profile, enabled, flag, colors) ->
                        profile.withLine(profile.line().withPride(enabled, flag, colors)),
                "line", "colorRgb", "lineColorRgb",
                "line", "guide", "pitch guide", "color editor", "pride", "pride flag",
                "custom pride", "custom stripe", "stripe", "rainbow")
                .withCompanions("line.pride_enabled", "line.pride_flag", "line.custom_pride_colors"));
        specs.add(hiddenBoolean("line.pride_enabled", profile -> profile.line().prideEnabled(),
                (profile, value) -> profile.withLine(profile.line().withPrideEnabled(value)),
                "line", "prideEnabled", "linePrideEnabled"));
        specs.add(hiddenPrideFlag("line.pride_flag", profile -> profile.line().prideFlag(),
                (profile, value) -> profile.withLine(profile.line().withPrideFlag(value)),
                "line", "prideFlag", "linePrideFlag"));
        specs.add(hiddenPrideColors("line.custom_pride_colors", profile -> profile.line().customPrideColors(),
                (profile, value) -> profile.withLine(profile.line().withCustomPrideColors(value)),
                "line", "customPrideColors", "lineCustomPrideColors"));

        specs.add(color("amplitude_color", ConfigCategory.AMPLITUDE, true,
                profile -> profile.amplitude().cueColorRgb(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withCueColorRgb(value)),
                profile -> new SettingSpec.ColorValue(profile.amplitude().cueColorRgb(),
                        profile.amplitude().cuePrideEnabled(), profile.amplitude().cuePrideFlag(),
                        profile.amplitude().customPrideColors()),
                (profile, enabled, flag, colors) ->
                        profile.withAmplitude(profile.amplitude().withCuePride(enabled, flag, colors)),
                "amplitude", "cueColorRgb", "amplitudeCueColorRgb",
                "cue", "amplitude", "velocity", "glow", "color editor", "pride", "pride flag",
                "custom pride", "custom stripe", "stripe", "rainbow")
                .withCompanions("amplitude.cue_pride_enabled", "amplitude.cue_pride_flag",
                        "amplitude.custom_pride_colors"));
        specs.add(hiddenBoolean("amplitude.cue_pride_enabled",
                profile -> profile.amplitude().cuePrideEnabled(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withCuePrideEnabled(value)),
                "amplitude", "cuePrideEnabled", "amplitudeCuePrideEnabled"));
        specs.add(hiddenPrideFlag("amplitude.cue_pride_flag",
                profile -> profile.amplitude().cuePrideFlag(),
                (profile, value) -> profile.withAmplitude(profile.amplitude().withCuePrideFlag(value)),
                "amplitude", "cuePrideFlag", "amplitudeCuePrideFlag"));
        specs.add(hiddenPrideColors("amplitude.custom_pride_colors",
                profile -> profile.amplitude().customPrideColors(),
                (profile, value) -> profile.withAmplitude(
                        profile.amplitude().withCustomPrideColors(value)),
                "amplitude", "customPrideColors", "amplitudeCustomPrideColors"));

        specs.add(toggle("void_warning", ConfigCategory.VOID,
                profile -> profile.voidWarning().enabled(),
                (profile, value) -> profile.withVoidWarning(profile.voidWarning().withEnabled(value)),
                "voidWarning", "enabled", "voidWarningEnabled"));
        specs.add(cycle("void_mode", ConfigCategory.VOID,
                VoidWarningSettings.MODE_PREDICTED_TIME, VoidWarningSettings.MODE_SIMPLE_HEIGHT, true,
                profile -> profile.voidWarning().mode() == VoidWarningSettings.MODE_SIMPLE_HEIGHT
                        ? "option.elytrapitchhelper.void_mode.predicted"
                        : "option.elytrapitchhelper.void_mode.simple",
                profile -> profile.voidWarning().mode(),
                (profile, value) -> profile.withVoidWarning(profile.voidWarning().withMode(value)),
                "voidWarning", "mode", "voidWarningMode"));
        specs.add(floatSlider("void_lookahead", ConfigCategory.VOID, 1.0f, 15.0f, 0.5f, "s",
                profile -> profile.voidWarning().lookaheadSeconds(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withLookaheadSeconds(value)),
                "voidWarning", "lookaheadSeconds", "voidWarningLookaheadSeconds",
                profile -> profile.voidWarning().mode() == VoidWarningSettings.MODE_PREDICTED_TIME));
        specs.add(intSlider("void_simple_blocks", ConfigCategory.VOID, 1, 512, 1, "blocks",
                profile -> profile.voidWarning().simpleWarningBlocks(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withSimpleWarningBlocks(value)),
                "voidWarning", "simpleWarningBlocks", "voidWarningSimpleWarningBlocks",
                profile -> profile.voidWarning().mode() == VoidWarningSettings.MODE_SIMPLE_HEIGHT));
        specs.add(toggle("void_tolerance_override", ConfigCategory.VOID,
                profile -> profile.voidWarning().toleranceOverride(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withToleranceOverride(value)),
                "voidWarning", "toleranceOverride", "voidWarningToleranceOverride"));
        specs.add(floatSlider("void_custom_tolerance", ConfigCategory.VOID,
                VoidWarningSettings.MIN_CUSTOM_TOLERANCE_DEGREES,
                VoidWarningSettings.MAX_CUSTOM_TOLERANCE_DEGREES, 0.5f, "deg",
                profile -> profile.voidWarning().customToleranceDegrees(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withCustomToleranceDegrees(value)),
                "voidWarning", "customToleranceDegrees", "voidWarningCustomToleranceDegrees"));
        specs.add(toggle("void_max_offset_override", ConfigCategory.VOID,
                profile -> profile.voidWarning().maxOffsetOverride(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withMaxOffsetOverride(value)),
                "voidWarning", "maxOffsetOverride", "voidWarningMaxOffsetOverride"));
        specs.add(intSlider("void_custom_max_offset", ConfigCategory.VOID, 0, 200, 1, "px",
                profile -> profile.voidWarning().customMaxOffsetPixels(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withCustomMaxOffsetPixels(value)),
                "voidWarning", "customMaxOffsetPixels", "voidWarningCustomMaxOffsetPixels"));
        specs.add(dynamic("void_dimension", ConfigCategory.VOID));
        specs.add(dynamic("void_y_mode", ConfigCategory.VOID));
        specs.add(new SettingSpec<>("void_y_override", ConfigCategory.VOID, new SettingSpec.Dynamic<>(),
                profile -> profile.voidWarning().dimensionYOverrides(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withDimensionYOverrides(value)),
                List.of(), binding("voidWarning", "dimensionYOverrides", null, DIMENSION_OVERRIDES_JSON),
                SettingsRegistry::sanitizeDimensionOverrides, ALWAYS));

        specs.add(color("void_color", ConfigCategory.VOID, true,
                profile -> profile.voidWarning().warningColorRgb(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withWarningColorRgb(value)),
                profile -> new SettingSpec.ColorValue(profile.voidWarning().warningColorRgb(),
                        profile.voidWarning().warningPrideEnabled(), profile.voidWarning().warningPrideFlag(),
                        profile.voidWarning().customPrideColors()),
                (profile, enabled, flag, colors) -> profile.withVoidWarning(
                        profile.voidWarning().withWarningPride(enabled, flag, colors)),
                "voidWarning", "warningColorRgb", "voidWarningColorRgb",
                "void", "warning", "void warning", "warning color", "color editor", "pride", "pride flag",
                "custom pride", "custom stripe", "stripe", "rainbow")
                .withCompanions("void_warning.pride_enabled", "void_warning.pride_flag",
                        "void_warning.custom_pride_colors"));
        specs.add(hiddenBoolean("void_warning.pride_enabled",
                profile -> profile.voidWarning().warningPrideEnabled(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withWarningPrideEnabled(value)),
                "voidWarning", "warningPrideEnabled", "voidWarningPrideEnabled"));
        specs.add(hiddenPrideFlag("void_warning.pride_flag",
                profile -> profile.voidWarning().warningPrideFlag(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withWarningPrideFlag(value)),
                "voidWarning", "warningPrideFlag", "voidWarningPrideFlag"));
        specs.add(hiddenPrideColors("void_warning.custom_pride_colors",
                profile -> profile.voidWarning().customPrideColors(),
                (profile, value) -> profile.withVoidWarning(
                        profile.voidWarning().withCustomPrideColors(value)),
                "voidWarning", "customPrideColors", "voidWarningCustomPrideColors"));

        specs.add(screen("command_palette_appearance", ConfigCategory.INTERFACE,
                Profile::commandPaletteAppearance,
                Profile::withCommandPaletteAppearance,
                "palette", "appearance", "shadow", "blur", "accent", "base color")
                .withCompanions("command_palette_shadow", "command_palette_blur",
                        "command_palette_accent_color", "command_palette_base_color"));
        specs.add(hiddenInt("command_palette_shadow", 0, 100,
                profile -> profile.commandPaletteAppearance().shadowOpacity(),
                (profile, value) -> profile.withCommandPaletteAppearance(
                        profile.commandPaletteAppearance().withShadowOpacity(value)),
                "commandPaletteAppearance", "shadowOpacity", "commandPaletteShadowOpacity"));
        specs.add(hiddenInt("command_palette_blur", 0, 100,
                profile -> profile.commandPaletteAppearance().blurAmount(),
                (profile, value) -> profile.withCommandPaletteAppearance(
                        profile.commandPaletteAppearance().withBlurAmount(value)),
                "commandPaletteAppearance", "blurAmount", "commandPaletteBlurAmount"));
        specs.add(hiddenColor("command_palette_accent_color",
                profile -> profile.commandPaletteAppearance().accentColorRgb(),
                (profile, value) -> profile.withCommandPaletteAppearance(
                        profile.commandPaletteAppearance().withAccentColorRgb(value)),
                "commandPaletteAppearance", "accentColorRgb", "commandPaletteAccentColorRgb"));
        specs.add(hiddenColor("command_palette_base_color",
                profile -> profile.commandPaletteAppearance().baseColorRgb(),
                (profile, value) -> profile.withCommandPaletteAppearance(
                        profile.commandPaletteAppearance().withBaseColorRgb(value)),
                "commandPaletteAppearance", "baseColorRgb", "commandPaletteBaseColorRgb"));

        specs.add(toggle("flight_telemetry_logging", ConfigCategory.INTERFACE,
                profile -> profile.diagnostics().flightTelemetryLogging(),
                (profile, value) -> profile.withDiagnostics(
                        profile.diagnostics().withFlightTelemetryLogging(value)),
                "diagnostics", "flightTelemetryLogging", null,
                (Function<Profile, String>) null,
                "flight", "telemetry", "logging", "diagnostics", "troubleshooting"));

        return List.copyOf(applyDependencies(specs));
    }

    private static List<SettingSpec<?>> applyDependencies(List<SettingSpec<?>> specs) {
        Predicate<Profile> cueOn = profile -> profile.amplitude().enabled();
        Predicate<Profile> voidOn = profile -> profile.voidWarning().enabled();
        Map<String, Dependency> dependencies = new LinkedHashMap<>();
        for (String child : List.of("amplitude_trigger_mode", "amplitude_down", "amplitude_up",
                "amplitude_tolerance", "amplitude_down_velocity", "amplitude_up_velocity",
                "amplitude_repeat_dive", "amplitude_repeat_climb", "motion_glyphs",
                "amplitude_color")) {
            dependencies.put(child, new Dependency("amplitude_helper", cueOn));
        }
        Predicate<Profile> glyphsOn = profile -> profile.amplitude().motionGlyphsEnabled();
        for (String child : List.of("pull_up_glyph_duration", "release_down_glyph_duration")) {
            dependencies.put(child, new Dependency("motion_glyphs", glyphsOn));
        }
        for (String child : List.of("void_mode", "void_tolerance_override", "void_max_offset_override",
                "void_dimension", "void_y_mode", "void_color")) {
            dependencies.put(child, new Dependency("void_warning", voidOn));
        }
        dependencies.put("void_lookahead", new Dependency("void_mode", voidOn));
        dependencies.put("void_simple_blocks", new Dependency("void_mode", voidOn));
        dependencies.put("void_custom_tolerance",
                new Dependency("void_tolerance_override", profile -> profile.voidWarning().toleranceOverride()));
        dependencies.put("void_custom_max_offset",
                new Dependency("void_max_offset_override", profile -> profile.voidWarning().maxOffsetOverride()));
        dependencies.put("void_y_override", new Dependency("void_y_mode", ALWAYS));

        List<SettingSpec<?>> result = new ArrayList<>(specs.size());
        for (SettingSpec<?> spec : specs) {
            Dependency dependency = dependencies.get(spec.id());
            result.add(dependency == null
                    ? spec
                    : spec.withParent(dependency.parent(), dependency.enabled()));
        }
        return result;
    }

    private record Dependency(String parent, Predicate<Profile> enabled) {
    }

    public static int depth(SettingSpec<?> spec) {
        int depth = 0;
        SettingSpec<?> current = spec;
        while (current != null && current.parent() != null && depth < MAX_DEPENDENCY_DEPTH) {
            current = byId(current.parent());
            depth++;
        }
        return depth;
    }

    public static boolean isEnabled(SettingSpec<?> spec, Profile profile) {
        SettingSpec<?> current = spec;
        for (int guard = 0; current != null && guard <= MAX_DEPENDENCY_DEPTH; guard++) {
            if (!current.enabled().test(profile)) {
                return false;
            }
            current = current.parent() == null ? null : byId(current.parent());
        }
        return true;
    }

    private static SettingSpec<Boolean> toggle(String id, ConfigCategory category,
            Function<Profile, Boolean> get, Assign<Boolean> set, String section, String field, String legacy,
            String... keywords) {
        return toggle(id, category, get, set, section, field, legacy, null, keywords);
    }

    private static SettingSpec<Boolean> toggle(String id, ConfigCategory category,
            Function<Profile, Boolean> get, Assign<Boolean> set, String section, String field, String legacy,
            Function<Profile, String> nextValueKey, String... keywords) {
        return new SettingSpec<>(id, category, new SettingSpec.Toggle(nextValueKey), get,
                setter(set), List.of(keywords), binding(section, field, legacy, BOOLEAN_JSON),
                BOOLEAN, ALWAYS);
    }

    private static SettingSpec<Integer> intSlider(String id, ConfigCategory category,
            int min, int max, int step, String unit, Function<Profile, Integer> get, Assign<Integer> set,
            String section, String field, String legacy) {
        return intSlider(id, category, min, max, step, unit, get, set,
                section, field, legacy, min, max, ALWAYS);
    }

    private static SettingSpec<Integer> intSlider(String id, ConfigCategory category,
            int min, int max, int step, String unit, Function<Profile, Integer> get, Assign<Integer> set,
            String section, String field, String legacy, int validMin, int validMax) {
        return intSlider(id, category, min, max, step, unit, get, set,
                section, field, legacy, validMin, validMax, ALWAYS);
    }

    private static SettingSpec<Integer> intSlider(String id, ConfigCategory category,
            int min, int max, int step, String unit, Function<Profile, Integer> get, Assign<Integer> set,
            String section, String field, String legacy, Predicate<Profile> visible) {
        return intSlider(id, category, min, max, step, unit, get, set,
                section, field, legacy, min, max, visible);
    }

    private static SettingSpec<Integer> intSlider(String id, ConfigCategory category,
            int min, int max, int step, String unit, Function<Profile, Integer> get, Assign<Integer> set,
            String section, String field, String legacy, int validMin, int validMax,
            Predicate<Profile> visible) {
        return new SettingSpec<>(id, category, new SettingSpec.IntSlider(min, max, step, unit),
                get, setter(set), List.of(), binding(section, field, legacy, INT_JSON),
                (repairs, path, value, fallback) ->
                        ConfigRepair.repairInt(repairs, path, value, fallback, validMin, validMax),
                visible);
    }

    private static SettingSpec<Float> floatSlider(String id, ConfigCategory category,
            float min, float max, float step, String unit, Function<Profile, Float> get, Assign<Float> set,
            String section, String field, String legacy) {
        return floatSlider(id, category, min, max, step, unit, get, set,
                section, field, legacy, min, max, ALWAYS);
    }

    private static SettingSpec<Float> floatSlider(String id, ConfigCategory category,
            float min, float max, float step, String unit, Function<Profile, Float> get, Assign<Float> set,
            String section, String field, String legacy, float validMin, float validMax) {
        return floatSlider(id, category, min, max, step, unit, get, set,
                section, field, legacy, validMin, validMax, ALWAYS);
    }

    private static SettingSpec<Float> floatSlider(String id, ConfigCategory category,
            float min, float max, float step, String unit, Function<Profile, Float> get, Assign<Float> set,
            String section, String field, String legacy, Predicate<Profile> visible) {
        return floatSlider(id, category, min, max, step, unit, get, set,
                section, field, legacy, min, max, visible);
    }

    private static SettingSpec<Float> floatSlider(String id, ConfigCategory category,
            float min, float max, float step, String unit, Function<Profile, Float> get, Assign<Float> set,
            String section, String field, String legacy, float validMin, float validMax,
            Predicate<Profile> visible) {
        return new SettingSpec<>(id, category, new SettingSpec.FloatSlider(min, max, step, unit),
                get, setter(set), List.of(), binding(section, field, legacy, FLOAT_JSON),
                (repairs, path, value, fallback) ->
                        ConfigRepair.repairFloat(repairs, path, value, fallback, validMin, validMax),
                visible);
    }

    private static SettingSpec<Integer> cycle(String id, ConfigCategory category,
            int min, int max, boolean paletteToggle, Function<Profile, String> nextValueKey,
            Function<Profile, Integer> get, Assign<Integer> set,
            String section, String field, String legacy) {
        return new SettingSpec<>(id, category,
                new SettingSpec.Cycle(min, max, paletteToggle, nextValueKey),
                get, setter(set), List.of(), binding(section, field, legacy, INT_JSON),
                (repairs, path, value, fallback) ->
                        ConfigRepair.repairInt(repairs, path, value, fallback, min, max),
                ALWAYS);
    }

    private static SettingSpec<Integer> color(String id, ConfigCategory category, boolean previewCuePeak,
            Function<Profile, Integer> get, Assign<Integer> set,
            Function<Profile, SettingSpec.ColorValue> state, SettingSpec.PrideSetter setPride,
            String section, String field, String legacy,
            String... keywords) {
        return new SettingSpec<>(id, category,
                new SettingSpec.Color(previewCuePeak, state, setPride),
                get, setter(set), List.of(keywords), binding(section, field, legacy, INT_JSON),
                COLOR, ALWAYS);
    }

    private static <T> SettingSpec<T> screen(String id, ConfigCategory category,
            Function<Profile, T> get, Assign<T> set, String... keywords) {
        return new SettingSpec<>(id, category, new SettingSpec.Screen<>(),
                get, setter(set), List.of(keywords), null, (repairs, path, value, fallback) -> value, ALWAYS);
    }

    private static SettingSpec<Object> dynamic(String id, ConfigCategory category) {
        return new SettingSpec<>(id, category, new SettingSpec.Dynamic<>(),
                profile -> null, (profile, value) -> profile, List.of(), null,
                (repairs, path, value, fallback) -> value, ALWAYS);
    }

    private static SettingSpec<Boolean> hiddenBoolean(String id,
            Function<Profile, Boolean> get, Assign<Boolean> set, String section, String field, String legacy) {
        return new SettingSpec<>(id, ConfigCategory.LINE, new SettingSpec.Hidden<>(),
                get, setter(set), List.of(), binding(section, field, legacy, BOOLEAN_JSON), BOOLEAN, ALWAYS);
    }

    private static SettingSpec<Integer> hiddenInt(String id, int min, int max,
            Function<Profile, Integer> get, Assign<Integer> set, String section, String field, String legacy) {
        return new SettingSpec<>(id, ConfigCategory.LINE,
                new SettingSpec.Hidden<>(new SettingSpec.IntSlider(min, max, 1, "%")),
                get, setter(set), List.of(), binding(section, field, legacy, INT_JSON),
                (repairs, path, value, fallback) ->
                        ConfigRepair.repairInt(repairs, path, value, fallback, min, max),
                ALWAYS);
    }

    private static SettingSpec<Integer> hiddenColor(String id,
            Function<Profile, Integer> get, Assign<Integer> set, String section, String field, String legacy) {
        return new SettingSpec<>(id, ConfigCategory.LINE, new SettingSpec.Hidden<>(),
                get, setter(set), List.of(), binding(section, field, legacy, INT_JSON), COLOR, ALWAYS);
    }

    private static SettingSpec<String> hiddenPrideFlag(String id,
            Function<Profile, String> get, Assign<String> set, String section, String field, String legacy) {
        return new SettingSpec<>(id, ConfigCategory.LINE, new SettingSpec.Hidden<>(),
                get, setter(set), List.of(), binding(section, field, legacy, STRING_JSON), PRIDE_FLAG, ALWAYS);
    }

    private static SettingSpec<int[]> hiddenPrideColors(String id,
            Function<Profile, int[]> get, Assign<int[]> set, String section, String field, String legacy) {
        return new SettingSpec<>(id, ConfigCategory.LINE, new SettingSpec.Hidden<>(),
                get, setter(set), List.of(),
                binding(section, field, legacy, INT_ARRAY_JSON), PRIDE_COLORS, ALWAYS);
    }

    private static <T> SettingSpec.JsonBinding<T> binding(String section, String field, String legacy,
            SettingSpec.JsonCodec<T> codec) {
        return new SettingSpec.JsonBinding<>(section, field, legacy, codec);
    }

    private static <T> BiFunction<Profile, T, Profile> setter(Assign<T> assign) {
        return assign::apply;
    }

    private static Map<String, Integer> sanitizeDimensionOverrides(RepairLog repairs, String field,
            Map<String, Integer> value, Map<String, Integer> fallback) {
        Map<String, Integer> result = new HashMap<>();
        if (value == null) {
            return result;
        }
        value.forEach((dimension, y) -> {
            if (!VoidWarningSettings.isValidDimensionId(dimension)) {
                ConfigRepair.repair(repairs, field + "." + dimension, y, "ignored");
            } else if (y == null) {
                ConfigRepair.repair(repairs, field + "." + dimension, null, 0);
                result.put(dimension, 0);
            } else {
                result.put(dimension, ConfigRepair.repairInt(repairs, field + "." + dimension, y, 0,
                        VoidWarningSettings.MIN_VOID_Y, VoidWarningSettings.MAX_VOID_Y));
            }
        });
        return result;
    }

    private static Map<String, SettingSpec<?>> indexById(List<SettingSpec<?>> specs) {
        Map<String, SettingSpec<?>> result = new LinkedHashMap<>();
        for (SettingSpec<?> spec : specs) {
            if (result.put(spec.id(), spec) != null) {
                throw new IllegalStateException("Duplicate setting id: " + spec.id());
            }
        }
        Map<String, String> owners = new HashMap<>();
        for (SettingSpec<?> owner : specs) {
            if (!owner.companions().isEmpty() && !owner.searchable()) {
                throw new IllegalStateException("Reset owner is not visible: " + owner.id());
            }
            for (String companionId : owner.companions()) {
                if (owner.id().equals(companionId)) {
                    throw new IllegalStateException("Setting cannot own itself: " + owner.id());
                }
                SettingSpec<?> companion = result.get(companionId);
                if (companion == null) {
                    throw new IllegalStateException("Unknown companion setting: " + companionId);
                }
                if (!(companion.control() instanceof SettingSpec.Hidden<?>)) {
                    throw new IllegalStateException("Companion is not hidden: " + companionId);
                }
                String previous = owners.put(companionId, owner.id());
                if (previous != null) {
                    throw new IllegalStateException("Companion " + companionId
                            + " is claimed by both " + previous + " and " + owner.id());
                }
            }
        }
        for (SettingSpec<?> spec : specs) {
            if (spec.control() instanceof SettingSpec.Hidden<?> && !owners.containsKey(spec.id())) {
                throw new IllegalStateException("Hidden setting has no reset owner: " + spec.id());
            }
        }
        return Map.copyOf(result);
    }

    @FunctionalInterface
    private interface Assign<T> {
        Profile apply(Profile profile, T value);
    }
}
