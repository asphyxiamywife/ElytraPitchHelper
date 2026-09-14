package com.asphyxiamywife.elytrapitchhelper.porttest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class ProbeCertificate {
    public static final String REFERENCE_VERSION = "26.1.2";
    public static final String REFERENCE_REFUSAL = "Cannot accept goldens for reference version 26.1.2. Reference goldens are immutable.";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] args) throws Exception {
        evaluate(Path.of(args[0]), args[1], Path.of(args[2]), Boolean.parseBoolean(args[3]));
    }

    static void evaluate(Path corpus, String version, Path reports, boolean accept) throws Exception {
        require(version.matches("[A-Za-z0-9._+-]+") && !version.equals(".") && !version.equals(".."), "Invalid Minecraft version");
        if (accept && version.equals(REFERENCE_VERSION)) throw new IllegalStateException(REFERENCE_REFUSAL);
        JsonObject policy = read(corpus.resolve("probe-policy.json"));
        require(policy.get("schema").getAsInt() == 1, "Unknown probe policy schema");
        require(REFERENCE_VERSION.equals(policy.get("referenceVersion").getAsString()), "Probe reference changed");
        JsonArray ids = policy.getAsJsonArray("scenarios");
        require(!ids.isEmpty(), "Empty probe manifest");
        JsonObject fields = policy.getAsJsonObject("fields");
        Set<String> unique = new java.util.HashSet<>();
        for (JsonElement id : ids) require(unique.add(id.getAsString()), "Duplicate probe id");
        for (String loader : List.of("fabric", "neoforge")) {
            JsonObject completion = read(reports.resolve(loader).resolve("complete.json"));
            require(completion.get("schema").getAsInt() == 1, "Unknown completion schema");
            require(version.equals(completion.get("minecraftVersion").getAsString()), loader + " engine version mismatch");
            require(completion.get("policySha256").getAsString().equals(digest(corpus.resolve("probe-policy.json"))), loader + " used a different probe policy");
            require(ids.equals(completion.getAsJsonArray("scenarios")), loader + " did not complete every scenario");
        }
        List<String> unreviewed = new ArrayList<>();
        Path expectedDirectory = corpus.resolve("probe-goldens").resolve(version);
        List<JsonObject> candidates = new ArrayList<>();
        for (JsonElement entry : ids) {
            String id = entry.getAsString();
            require(id.matches("[a-z0-9_]+"), "Invalid probe id");
            Path scenarioPath = corpus.resolve("scenarios/flight").resolve(id + ".json");
            JsonObject scenario = read(scenarioPath);
            JsonObject fabric = read(reports.resolve("fabric").resolve(id + ".json"));
            JsonObject neo = read(reports.resolve("neoforge").resolve(id + ".json"));
            for (JsonObject actual : List.of(fabric, neo)) {
                require(actual.keySet().equals(Set.of("scenario", "scenarioSha256", "schema", "samples")), "Unknown/missing observation fields");
                require(actual.get("scenario").getAsString().equals(id), "Scenario id mismatch");
                require(actual.get("schema").getAsInt() == scenario.get("schema").getAsInt(), "Scenario schema mismatch");
                require(actual.get("scenarioSha256").getAsString().equals(digest(scenarioPath)), "Scenario input digest mismatch: " + id);
                require(actual.getAsJsonArray("samples").size() == scenario.getAsJsonArray("samples").size(), "Sample count mismatch: " + id);
            }
            compare(fabric, neo, fields, "Fabric/NeoForge parity");
            candidates.add(fabric);
            Path expected = expectedDirectory.resolve(id + ".json");
            if (!Files.isRegularFile(expected)) unreviewed.add(id);
            else if (!accept) {
                compare(read(expected), fabric, fields, "Accepted " + version + " Fabric");
                compare(read(expected), neo, fields, "Accepted " + version + " NeoForge");
            }
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("schema", 1);
        summary.addProperty("minecraftVersion", version);
        summary.addProperty("loaderParity", "PASS");
        summary.addProperty("scenarios", ids.size());
        if (accept) {
            Files.createDirectories(expectedDirectory);
            for (JsonObject candidate : candidates) {
                Files.writeString(expectedDirectory.resolve(candidate.get("scenario").getAsString() + ".json"), JSON.toJson(candidate) + "\n");
            }
            summary.addProperty("status", "ACCEPTED");
        } else {
            summary.addProperty("status", unreviewed.isEmpty() ? "PASS" : "UNREVIEWED");
        }
        Files.writeString(reports.resolve("probe-summary.json"), JSON.toJson(summary) + "\n");
        if (!accept && !unreviewed.isEmpty()) {
            throw new IllegalStateException("UNREVIEWED: no accepted " + version + " probe observations for " + unreviewed
                    + ". Candidates are under " + reports + ". Review before explicit promotion.");
        }
        System.out.println("Probe contract: " + summary.get("status").getAsString() + " (" + ids.size() + " scenarios; both loaders)");
    }

    static void compare(JsonObject expected, JsonObject actual, JsonObject fields, String label) {
        require(expected.keySet().equals(actual.keySet()), label + ": observation schema differs");
        for (String key : List.of("schema", "scenario", "scenarioSha256")) {
            require(expected.get(key).equals(actual.get(key)), label + ": " + key + " mismatch");
        }
        JsonArray left = expected.getAsJsonArray("samples"), right = actual.getAsJsonArray("samples");
        require(left.size() == right.size(), label + ": sample count differs");
        for (int i = 0; i < left.size(); i++) {
            JsonObject a = left.get(i).getAsJsonObject(), b = right.get(i).getAsJsonObject();
            require(a.keySet().equals(fields.keySet()) && b.keySet().equals(fields.keySet()), label + ": comparison policy does not cover every field");
            for (String field : fields.keySet()) {
                JsonObject rule = fields.getAsJsonObject(field);
                String location = label + " " + actual.get("scenario").getAsString() + " sample " + (i + 1) + " " + field;
                switch (rule.get("comparison").getAsString()) {
                    case "EXACT" -> require(a.get(field).equals(b.get(field)), location + ": expected " + a.get(field) + ", observed " + b.get(field));
                    case "EPSILON" -> {
                        require(a.get(field).getAsJsonPrimitive().isNumber() && b.get(field).getAsJsonPrimitive().isNumber(), location + ": nonnumeric field");
                        double av = a.get(field).getAsDouble(), bv = b.get(field).getAsDouble(), epsilon = rule.get("epsilon").getAsDouble();
                        require(Double.isFinite(av) && Double.isFinite(bv) && Double.isFinite(epsilon) && epsilon > 0, location + ": non-finite value or invalid epsilon");
                        require(Math.abs(av - bv) <= epsilon, location + ": expected " + av + ", observed " + bv + ", epsilon " + epsilon);
                    }
                    default -> throw new IllegalStateException(location + ": unknown comparison class");
                }
            }
        }
    }

    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    static String digest(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
}
