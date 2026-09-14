package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.flight.ViewMode;
import com.google.gson.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ModelCorpus {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final String CADENCE = "set-clock-apply-input-update-sample";
    static final int MAX_SCENARIOS = 32;
    static final int MAX_SAMPLES = 256;

    static List<Path> scenarios(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        for (JsonElement entry : read(root.resolve("model-policy.json")).getAsJsonArray("scenarios")) {
            String id = entry.getAsString();
            assertTrue(id.matches("[a-z0-9_]+"), "Unsafe scenario id");
            Path path = root.resolve("scenarios/flight").resolve(id + ".json");
            assertTrue(Files.isRegularFile(path), "Missing accepted scenario: " + id);
            result.add(path);
        }
        assertFalse(result.isEmpty(), "Empty model corpus");
        assertTrue(result.size() <= MAX_SCENARIOS, "Model corpus size budget exceeded");
        return result.stream().sorted().toList();
    }

    static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    static String digest(Path scenario) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(scenario)));
    }

    static String canonical(JsonObject value) {
        return JSON.toJson(value) + "\n";
    }

    static JsonObject observe(Path scenario) throws Exception {
        JsonObject input = read(scenario);
        keys(input, "schema", "scenario", "cadence", "settings", "samples");
        assertEquals(1, input.get("schema").getAsInt());
        String id = input.get("scenario").getAsString();
        assertTrue(id.matches("[a-z0-9_]+"), "Unsafe scenario id");
        assertEquals(id + ".json", scenario.getFileName().toString());
        assertEquals(CADENCE, input.get("cadence").getAsString());
        JsonArray samples = input.getAsJsonArray("samples");
        assertTrue(!samples.isEmpty() && samples.size() <= MAX_SAMPLES, "Scenario sample budget exceeded");
        JsonObject settings = input.getAsJsonObject("settings");
        keys(settings, "triggerMode", "voidMode", "lookaheadSeconds", "simpleWarningBlocks");
        Config config = new Config();
        Profile profile = config.activeProfile();
        profile = profile.withAmplitude(profile.amplitude().withEnabled(true)
                .withTriggerMode(settings.get("triggerMode").getAsInt())
                .withDownBlocks(10).withUpBlocks(10).withToleranceBlocks(2)
                .withDownVelocity(2.0f).withUpVelocity(0.2f)
                .withRepeatDiveCue(true).withRepeatClimbCue(true)
                .withMotionGlyphsEnabled(true).withPullUpGlyphMillis(1000).withReleaseDownGlyphMillis(1200));
        profile = profile.withVoidWarning(profile.voidWarning().withEnabled(true)
                .withMode(settings.get("voidMode").getAsInt())
                .withLookaheadSeconds(settings.get("lookaheadSeconds").getAsFloat())
                .withSimpleWarningBlocks(settings.get("simpleWarningBlocks").getAsInt()));
        profile = profile.withVisibility(profile.visibility().withShowInThirdPerson(false)
                .withShowOnlyWithFirework(false).withAnyElytraGlide(false));
        config.replaceProfile(config.activeProfileIndex, profile);
        long[] clock = {0};
        AmplitudeTracker amplitude = new AmplitudeTracker(() -> clock[0]);
        VoidProximityTracker voidTracker = new VoidProximityTracker(() -> clock[0]);
        GroundBelowDetector ground = new GroundBelowDetector();
        JsonArray observations = new JsonArray();
        long lastTime = -1;
        double lastY = Double.POSITIVE_INFINITY;
        float lastWarning = 0;
        for (int index = 0; index < samples.size(); index++) {
            JsonObject sample = samples.get(index).getAsJsonObject();
            keys(sample, "timeMillis", "y", "verticalSpeed", "horizontalSpeed", "voidY",
                    "descendingGuideVisible", "ascendingGuideVisible", "amplitudeEnabled", "voidEnabled",
                    "viewMode", "usableElytra", "rocket", "fallFlyingTicks", "showThirdPerson",
                    "requireRocket", "reset", "suppressVoid", "groundLoaded", "supportY", "invalidate");
            clock[0] = sample.get("timeMillis").getAsLong();
            assertTrue(clock[0] >= lastTime, "Sampling clock must be monotonic");
            lastTime = clock[0];
            Profile current = config.activeProfile();
            current = current.withAmplitude(current.amplitude().withEnabled(bool(sample, "amplitudeEnabled")));
            current = current.withVoidWarning(current.voidWarning().withEnabled(bool(sample, "voidEnabled")));
            current = current.withVisibility(current.visibility()
                    .withShowInThirdPerson(bool(sample, "showThirdPerson"))
                    .withShowOnlyWithFirework(bool(sample, "requireRocket")));
            config.replaceProfile(config.activeProfileIndex, current);
            if (bool(sample, "reset")) {
                amplitude.reset();
                voidTracker.reset();
                ground.reset();
            }
            switch (sample.get("invalidate").getAsString()) {
                case "none" -> { }
                case "column" -> ground.invalidateColumn(0, 0);
                case "chunk" -> ground.invalidateChunk(0, 0);
                default -> fail("Unknown invalidation operation");
            }
            double y = number(sample, "y");
            double speed = number(sample, "horizontalSpeed");
            AmplitudeCue cue = amplitude.update(config, y, speed,
                    bool(sample, "descendingGuideVisible"), bool(sample, "ascendingGuideVisible"));
            VoidProximity proximity = voidTracker.update(config, y, number(sample, "verticalSpeed"),
                    sample.get("voidY").getAsInt());
            if (bool(sample, "suppressVoid")) {
                voidTracker.suppress();
                proximity = VoidProximity.NONE;
            }
            if (settings.get("voidMode").getAsInt() == 1 && bool(sample, "voidEnabled")
                    && !bool(sample, "reset") && !bool(sample, "suppressVoid") && y <= lastY) {
                assertTrue(proximity.warning >= lastWarning, "Simple void warning must grow as height falls");
            }
            lastY = y;
            lastWarning = proximity.warning;
            MotionGlyphCue glyph = amplitude.motionGlyphCue();
            boolean loaded = bool(sample, "groundLoaded");
            int supportY = sample.get("supportY").getAsInt();
            ColumnProbe probe = new ColumnProbe() {
                public boolean isLoaded(int x, int z) { return loaded; }
                public int surfaceTopY(int x, int z) { return supportY; }
                public boolean collidesAt(int x, int atY, int z) { return atY == supportY; }
                public ColumnScan scan(int x, int z, int fromY, int throughY, int budget) {
                    return TerrainCollisionScanner.scanColumn(this::collidesAt, x, z, fromY, throughY, budget);
                }
            };
            GroundSupport support = ground.detect(probe, "model:world", 0, 16, 0, 0, 0, 0, clock[0]);
            JsonObject out = new JsonObject();
            out.addProperty("sample", index);
            out.addProperty("timeMillis", clock[0]);
            out.addProperty("leg", cue.leg.name());
            out.addProperty("flashLeg", cue.flashLeg.name());
            out.addProperty("glyphStartedLeg", cue.glyphStartedLeg.name());
            out.addProperty("glyphAfterLeg", glyph.afterLeg().name());
            unit(out, "cueAmount", cue.amount);
            unit(out, "flash", amplitude.flashStrength().value());
            unit(out, "glyphProgress", glyph.progressNow());
            unit(out, "glyphOpacity", glyph.opacityNow());
            unit(out, "voidWarning", proximity.warning);
            unit(out, "voidPulse", voidTracker.pulseStrength().value());
            out.addProperty("voidRelevant", voidTracker.isWarningRelevant());
            out.addProperty("visible", HudVisibility.canRender(config,
                    ViewMode.valueOf(sample.get("viewMode").getAsString()),
                    bool(sample, "usableElytra"), bool(sample, "rocket"), sample.get("fallFlyingTicks").getAsInt()));
            out.addProperty("groundSupport", support.name());
            if (support == GroundSupport.SUPPORTED) assertTrue(loaded && supportY >= 0 && supportY <= 16);
            if (support == GroundSupport.UNSUPPORTED) assertTrue(supportY < 0 || supportY > 16);
            assertGlyphDirection(glyph, config);
            observations.add(out);
        }
        JsonObject result = new JsonObject();
        result.addProperty("scenario", id);
        result.addProperty("scenarioSha256", digest(scenario));
        result.addProperty("schema", 1);
        result.add("samples", observations);
        return result;
    }

    private static void assertGlyphDirection(MotionGlyphCue glyph, Config config) {
        if (glyph.afterLeg() == AmplitudeLeg.NONE || glyph.opacityNow() <= .01f || glyph.progressNow() < .2f) return;
        List<Integer> centers = new ArrayList<>();
        new MotionGlyphRenderer().draw((x1, y1, x2, y2, color) -> centers.add((y1 + y2) / 2),
                0, 0, 40, glyph, HudRenderState.from(config).cue());
        assertFalse(centers.isEmpty());
        double averageY = centers.stream().mapToInt(Integer::intValue).average().orElseThrow();
        assertTrue(glyph.afterLeg() == AmplitudeLeg.DESCENDING ? averageY < 0 : averageY > 0,
                "Motion glyph must point toward the predicted opposite leg");
    }

    static void keys(JsonObject object, String... expected) {
        assertEquals(Set.of(expected), object.keySet(), "Unknown or missing contract fields");
    }

    private static boolean bool(JsonObject object, String key) { return object.get(key).getAsBoolean(); }

    private static double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value.getAsJsonPrimitive().isString() ? switch (value.getAsString()) {
            case "NaN" -> Double.NaN;
            case "Infinity" -> Double.POSITIVE_INFINITY;
            default -> throw new IllegalArgumentException("Unknown numeric token: " + value);
        } : value.getAsDouble();
    }

    private static void unit(JsonObject output, String field, float value) {
        assertTrue(Float.isFinite(value) && value >= 0 && value <= 1, field + " must be finite and in [0,1]");
        output.addProperty(field, BigDecimal.valueOf((double) value).setScale(9, RoundingMode.HALF_EVEN).stripTrailingZeros());
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path report = Path.of(args[1]);
        assertFalse(report.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()),
                "Candidates must not be written into the accepted corpus tree");
        Files.createDirectories(report);
        for (Path scenario : scenarios(root)) {
            Files.writeString(report.resolve(scenario.getFileName()), canonical(observe(scenario)));
        }
    }
}
