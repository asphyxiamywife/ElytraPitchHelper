package com.asphyxiamywife.elytrapitchhelper.config.schema;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigJsonReader;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.RepairLog;
import com.asphyxiamywife.elytrapitchhelper.screen.ConfigCategory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public record SettingSpec<T>(
        String id,
        ConfigCategory category,
        Control<T> control,
        Function<Profile, T> get,
        BiFunction<Profile, T, Profile> set,
        List<String> paletteKeywords,
        JsonBinding<T> json,
        Sanitizer<T> sanitizer,
        Predicate<Profile> visible,
        List<String> companions,
        String parent,
        Predicate<Profile> enabled) {

    public SettingSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(get, "get");
        Objects.requireNonNull(set, "set");
        paletteKeywords = List.copyOf(paletteKeywords);
        Objects.requireNonNull(sanitizer, "sanitizer");
        Objects.requireNonNull(visible, "visible");
        companions = List.copyOf(companions);
        Objects.requireNonNull(enabled, "enabled");
    }

    public SettingSpec(String id, ConfigCategory category, Control<T> control,
            Function<Profile, T> get, BiFunction<Profile, T, Profile> set, List<String> paletteKeywords,
            JsonBinding<T> json, Sanitizer<T> sanitizer, Predicate<Profile> visible) {
        this(id, category, control, get, set, paletteKeywords, json, sanitizer, visible,
                List.of(), null, profile -> true);
    }

    public SettingSpec<T> withParent(String parentId, Predicate<Profile> enabled) {
        return new SettingSpec<>(id, category, control, get, set, paletteKeywords, json,
                sanitizer, visible, companions, parentId, enabled);
    }

    public SettingSpec<T> withCompanions(String... companionIds) {
        return new SettingSpec<>(id, category, control, get, set, paletteKeywords, json,
                sanitizer, visible, List.of(companionIds), parent, enabled);
    }

    public String labelKey() {
        return "option.elytrapitchhelper." + id;
    }

    public String tooltipKey() {
        return "tooltip.elytrapitchhelper." + id;
    }

    public String actionKey() {
        return id;
    }

    public boolean searchable() {
        return !(control instanceof Hidden<?>);
    }

    public boolean isVisible(Profile profile) {
        return searchable() && visible.test(profile);
    }

    public Profile apply(Profile profile, T value) {
        return set.apply(profile, value);
    }

    public T defaultValue() {
        return get.apply(Config.defaultProfileTemplate());
    }

    public Profile sanitize(Profile profile, Profile defaults, RepairLog repairs) {
        T value = get.apply(profile);
        T fallback = defaults == null ? defaultValue() : get.apply(defaults);
        T sanitized = sanitizer.sanitize(repairs, jsonPath(), value, fallback);
        return Objects.deepEquals(value, sanitized) ? profile : apply(profile, sanitized);
    }

    public Profile read(JsonObject root, JsonObject section, Profile profile, Profile defaults,
            RepairLog repairs) {
        if (json == null) {
            return profile;
        }
        T fallback = defaults == null ? defaultValue() : get.apply(defaults);
        return apply(profile, json.read(root, section, fallback, repairs));
    }

    public void write(JsonObject section, Profile profile) {
        if (json != null) {
            json.write(section, get.apply(profile));
        }
    }

    public String jsonSection() {
        return json == null ? null : json.section();
    }

    private String jsonPath() {
        return json == null ? id : json.section() + "." + json.field();
    }

    public sealed interface Control<T>
            permits Toggle, IntSlider, FloatSlider, Cycle, Color, Screen, Dynamic, Hidden {
    }

    public record Toggle(Function<Profile, String> nextValueKey) implements Control<Boolean> {
        public Toggle() {
            this(null);
        }
    }

    public record IntSlider(int min, int max, int step, String unit) implements Control<Integer> {
        public IntSlider {
            if (min > max || step <= 0) {
                throw new IllegalArgumentException("Invalid integer slider range");
            }
            Objects.requireNonNull(unit, "unit");
        }
    }

    public record FloatSlider(float min, float max, float step, String unit) implements Control<Float> {
        public FloatSlider {
            if (Float.compare(min, max) > 0 || !(step > 0.0f)) {
                throw new IllegalArgumentException("Invalid float slider range");
            }
            Objects.requireNonNull(unit, "unit");
        }
    }

    public record Cycle(int min, int max, boolean paletteToggle,
            Function<Profile, String> nextValueKey) implements Control<Integer> {
        public Cycle {
            if (min > max) {
                throw new IllegalArgumentException("Invalid cycle range");
            }
        }
    }

    public record Color(boolean previewCuePeak, Function<Profile, ColorValue> state,
            PrideSetter setPride) implements Control<Integer> {
        public Color {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(setPride, "setPride");
        }
    }

    public record ColorValue(int color, boolean prideEnabled, String prideFlagId, int[] customPrideColors) {
    }

    @FunctionalInterface
    public interface PrideSetter {
        Profile set(Profile profile, boolean enabled, String flagId, int[] customColors);
    }

    public record Screen<T>() implements Control<T> {
    }

    public record Dynamic<T>() implements Control<T> {
    }

    public record Hidden<T>(Control<T> metadata) implements Control<T> {
        public Hidden() {
            this(null);
        }
    }

    public record JsonBinding<T>(String section, String field, String legacyField, JsonCodec<T> codec) {
        public JsonBinding {
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(codec, "codec");
        }

        T read(JsonObject root, JsonObject sectionObject, T fallback, RepairLog repairs) {
            if (sectionObject != null && sectionObject.has(field)) {
                return codec.read(sectionObject, field, fallback, repairs);
            }
            if (legacyField == null) {
                return fallback;
            }
            return codec.read(root, legacyField, fallback, repairs);
        }

        void write(JsonObject sectionObject, T value) {
            codec.write(sectionObject, field, value);
        }
    }

    public interface JsonCodec<T> {
        T read(JsonObject json, String field, T fallback, RepairLog repairs);

        void write(JsonObject json, String field, T value);
    }

    @FunctionalInterface
    public interface Sanitizer<T> {
        T sanitize(RepairLog repairs, String field, T value, T fallback);
    }

    public static final JsonCodec<Boolean> BOOLEAN_JSON = new JsonCodec<>() {
        @Override
        public Boolean read(JsonObject json, String field, Boolean fallback, RepairLog repairs) {
            return ConfigJsonReader.readBoolean(json, field, fallback, repairs);
        }

        @Override
        public void write(JsonObject json, String field, Boolean value) {
            json.addProperty(field, value);
        }
    };

    public static final JsonCodec<Integer> INT_JSON = new JsonCodec<>() {
        @Override
        public Integer read(JsonObject json, String field, Integer fallback, RepairLog repairs) {
            return ConfigJsonReader.readInt(json, field, fallback, repairs);
        }

        @Override
        public void write(JsonObject json, String field, Integer value) {
            json.addProperty(field, value);
        }
    };

    public static final JsonCodec<Float> FLOAT_JSON = new JsonCodec<>() {
        @Override
        public Float read(JsonObject json, String field, Float fallback, RepairLog repairs) {
            return ConfigJsonReader.readFloat(json, field, fallback, repairs);
        }

        @Override
        public void write(JsonObject json, String field, Float value) {
            json.addProperty(field, value);
        }
    };

    public static final JsonCodec<String> STRING_JSON = new JsonCodec<>() {
        @Override
        public String read(JsonObject json, String field, String fallback, RepairLog repairs) {
            return ConfigJsonReader.readString(json, field, fallback, repairs);
        }

        @Override
        public void write(JsonObject json, String field, String value) {
            json.addProperty(field, value);
        }
    };

    public static final JsonCodec<int[]> INT_ARRAY_JSON = new JsonCodec<>() {
        @Override
        public int[] read(JsonObject json, String field, int[] fallback, RepairLog repairs) {
            JsonElement element = json.get(field);
            if (element == null || element.isJsonNull()) {
                return fallback;
            }
            if (!element.isJsonArray()) {
                com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repair(
                        repairs, field, element, fallback);
                return fallback;
            }
            int size = element.getAsJsonArray().size();
            if (size > com.asphyxiamywife.elytrapitchhelper.config.ConfigInputLimits.MAX_ARRAY_ELEMENTS) {
                com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repair(
                        repairs, field, "array with " + size + " elements", fallback);
                return fallback;
            }
            int[] parsed = new int[size];
            for (int i = 0; i < parsed.length; i++) {
                Integer value = ConfigJsonReader.parseExactInt(element.getAsJsonArray().get(i));
                if (value == null) {
                    com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repair(
                            repairs, field, element, fallback);
                    return fallback;
                }
                parsed[i] = value;
            }
            return parsed;
        }

        @Override
        public void write(JsonObject json, String field, int[] value) {
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            if (value != null) {
                for (int color : value) {
                    array.add(color);
                }
            }
            json.add(field, array);
        }
    };
}
