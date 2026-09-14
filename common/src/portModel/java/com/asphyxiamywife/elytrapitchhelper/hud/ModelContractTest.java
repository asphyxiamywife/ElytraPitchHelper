package com.asphyxiamywife.elytrapitchhelper.hud;

import com.google.gson.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class ModelContractTest {
    private static final Path ROOT = Path.of(System.getProperty("eph.portCorpus"));
    private static final Path REPORT = Path.of(System.getProperty("eph.modelReport"));

    @TestFactory
    Stream<DynamicTest> modelCorpus() throws Exception {
        JsonObject policy = ModelCorpus.read(ROOT.resolve("model-policy.json"));
        ModelCorpus.keys(policy, "schema", "baseline", "serialization", "cadence", "scenarios", "fields", "invariants");
        assertEquals(1, policy.get("schema").getAsInt());
        assertEquals("eph-3.0", policy.get("baseline").getAsString());
        assertEquals("decimal-9-half-even", policy.get("serialization").getAsString());
        assertEquals(ModelCorpus.CADENCE, policy.get("cadence").getAsString());
        assertEquals(Set.of("finite-unit-strengths", "truthful-ground-support", "motion-glyph-direction",
                "simple-void-monotonicity", "guide-ordering", "cue-leg-routing"), policy.getAsJsonArray("invariants").asList().stream()
                .map(JsonElement::getAsString).collect(java.util.stream.Collectors.toSet()));
        var scenarios = ModelCorpus.scenarios(ROOT);
        Set<String> accepted = new HashSet<>();
        policy.getAsJsonArray("scenarios").forEach(id -> assertTrue(accepted.add(id.getAsString()), "Duplicate scenario"));
        assertEquals(accepted, scenarios.stream().map(p -> p.getFileName().toString().replace(".json", ""))
                .collect(java.util.stream.Collectors.toSet()), "Scenario manifest changed");
        Path goldens = ROOT.resolve("model-goldens/eph-3.0");
        try (var paths = Files.list(goldens)) {
            assertEquals(accepted, paths.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .collect(java.util.stream.Collectors.toSet()), "Missing or orphaned accepted model golden");
        }
        Files.createDirectories(REPORT);
        return scenarios.stream().map(scenario -> DynamicTest.dynamicTest(scenario.getFileName().toString(), () -> {
            JsonObject actual = ModelCorpus.observe(scenario);
            assertEquals(actual, ModelCorpus.observe(scenario), "Model observations must reproduce exactly");
            Files.writeString(REPORT.resolve(scenario.getFileName()), ModelCorpus.canonical(actual));
            JsonObject expected = ModelCorpus.read(goldens.resolve(scenario.getFileName()));
            compare(expected, actual, policy.getAsJsonObject("fields"));
        }));
    }

    static void compare(JsonObject expected, JsonObject actual, JsonObject policy) {
        ModelCorpus.keys(expected, "scenario", "scenarioSha256", "schema", "samples");
        ModelCorpus.keys(actual, "scenario", "scenarioSha256", "schema", "samples");
        for (String field : new String[] {"scenario", "scenarioSha256", "schema"}) {
            assertEquals(expected.get(field), actual.get(field), "Contract mismatch: " + field);
        }
        JsonArray expectedSamples = expected.getAsJsonArray("samples");
        JsonArray actualSamples = actual.getAsJsonArray("samples");
        assertEquals(expectedSamples.size(), actualSamples.size(), "Sampling cadence/count changed");
        for (int index = 0; index < expectedSamples.size(); index++) {
            JsonObject wanted = expectedSamples.get(index).getAsJsonObject();
            JsonObject observed = actualSamples.get(index).getAsJsonObject();
            assertEquals(policy.keySet(), wanted.keySet(), "Expected fields lack comparison policy");
            assertEquals(policy.keySet(), observed.keySet(), "Observed fields lack comparison policy");
            for (String field : policy.keySet()) {
                String location = actual.get("scenario").getAsString() + " sample " + index + " field " + field;
                JsonObject rule = policy.getAsJsonObject(field);
                String comparison = rule.get("comparison").getAsString();
                if (comparison.equals("EXACT")) {
                    ModelCorpus.keys(rule, "comparison");
                    assertEquals(wanted.get(field), observed.get(field), location);
                } else if (comparison.equals("EPSILON")) {
                    ModelCorpus.keys(rule, "comparison", "epsilon");
                    assertTrue(wanted.get(field).getAsJsonPrimitive().isNumber(), location + " expected type");
                    assertTrue(observed.get(field).getAsJsonPrimitive().isNumber(), location + " observed type");
                    double epsilon = rule.get("epsilon").getAsDouble();
                    double a = wanted.get(field).getAsDouble();
                    double b = observed.get(field).getAsDouble();
                    assertTrue(Double.isFinite(epsilon) && epsilon > 0, "Invalid declared epsilon");
                    assertTrue(Double.isFinite(a) && Double.isFinite(b), location + " is non-finite");
                    assertEquals(a, b, epsilon, location);
                } else {
                    fail("Unknown comparison class: " + comparison);
                }
            }
        }
    }

    @Test
    void changedInputBytesAndSamplingCadenceCannotReuseAnAcceptedGolden(@TempDir Path directory) throws Exception {
        Path original = ModelCorpus.scenarios(ROOT).getFirst();
        Path altered = directory.resolve(original.getFileName());
        Files.writeString(altered, Files.readString(original) + "\n");
        JsonObject expected = ModelCorpus.observe(original);
        JsonObject actual = ModelCorpus.observe(altered);
        assertEquals(expected.get("samples"), actual.get("samples"));
        assertNotEquals(expected.get("scenarioSha256"), actual.get("scenarioSha256"));
        JsonObject policy = ModelCorpus.read(ROOT.resolve("model-policy.json")).getAsJsonObject("fields");
        assertThrows(AssertionError.class, () -> compare(expected, actual, policy));
        JsonObject changedCadence = ModelCorpus.read(original);
        changedCadence.addProperty("cadence", "sample-before-update");
        Files.writeString(altered, ModelCorpus.canonical(changedCadence));
        assertThrows(AssertionError.class, () -> ModelCorpus.observe(altered));
    }

    @Test
    void runtimeHasNoMinecraftOrLoaderClasses() {
        for (String name : new String[] {"net.minecraft.world.entity.player.Player", "net.minecraft.client.Minecraft",
                "net.fabricmc.loader.api.FabricLoader", "net.neoforged.fml.ModList"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(name, false, getClass().getClassLoader()));
        }
    }

    @Test
    void exactDriftDigestChangesMissingFieldsAndUnknownPoliciesFail() {
        JsonObject wanted = fixture();
        JsonObject rules = JsonParser.parseString("{\"leg\":{\"comparison\":\"EXACT\"}}").getAsJsonObject();
        JsonObject drift = wanted.deepCopy();
        drift.getAsJsonArray("samples").get(0).getAsJsonObject().addProperty("leg", "ASCENDING");
        assertThrows(AssertionError.class, () -> compare(wanted, drift, rules));
        JsonObject stale = wanted.deepCopy();
        stale.addProperty("scenarioSha256", "different-input");
        assertThrows(AssertionError.class, () -> compare(wanted, stale, rules));
        assertThrows(AssertionError.class, () -> compare(wanted, wanted, new JsonObject()));
        rules.getAsJsonObject("leg").addProperty("comparison", "IGNORE");
        assertThrows(AssertionError.class, () -> compare(wanted, wanted, rules));
    }

    @Test
    void epsilonIsPerFieldAndCannotHideLargeOrNonFiniteDrift() {
        JsonObject wanted = fixture();
        wanted.getAsJsonArray("samples").get(0).getAsJsonObject().addProperty("leg", .5);
        JsonObject observed = wanted.deepCopy();
        observed.getAsJsonArray("samples").get(0).getAsJsonObject().addProperty("leg", .5000005);
        JsonObject rule = JsonParser.parseString("{\"leg\":{\"comparison\":\"EPSILON\",\"epsilon\":0.000001}}").getAsJsonObject();
        compare(wanted, observed, rule);
        observed.getAsJsonArray("samples").get(0).getAsJsonObject().addProperty("leg", .500002);
        assertThrows(AssertionError.class, () -> compare(wanted, observed, rule));
        observed.getAsJsonArray("samples").get(0).getAsJsonObject().addProperty("leg", Double.NaN);
        assertThrows(AssertionError.class, () -> compare(wanted, observed, rule));
    }

    private static JsonObject fixture() {
        return JsonParser.parseString("""
                {"scenario":"fixture","scenarioSha256":"input","schema":1,"samples":[{"leg":"DESCENDING"}]}
                """).getAsJsonObject();
    }
}
