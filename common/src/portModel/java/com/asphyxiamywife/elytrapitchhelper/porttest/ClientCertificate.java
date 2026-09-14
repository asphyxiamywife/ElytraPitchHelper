package com.asphyxiamywife.elytrapitchhelper.porttest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientCertificate {
    static final List<String> CLAIMS = List.of("entrypoint", "hudAttachment", "keys", "mixinAudit",
            "localPlayer", "hudRender", "blockCallback", "chunkCallback");

    public static void main(String[] args) throws Exception {
        Path reports = Path.of(args[1]);
        for (String loader : List.of("fabric", "neoforge")) {
            Path report = reports.resolve(loader + ".json");
            if (!Files.isRegularFile(report)) throw new IllegalStateException("Missing " + loader + " client completion: " + report);
            validate(JsonParser.parseString(Files.readString(report)).getAsJsonObject(), args[0], loader);
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("schema", 1);
        summary.addProperty("minecraftVersion", args[0]);
        summary.addProperty("status", "PASS");
        summary.addProperty("fabric", "PASS");
        summary.addProperty("neoforge", "PASS");
        Files.writeString(reports.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary) + "\n");
        System.out.println("Client contract: PASS (both loaders; actual HUD drawing and LocalPlayer capture)");
    }

    static void validate(JsonObject report, String version, String loader) {
        Set<String> fields = new HashSet<>(CLAIMS);
        fields.addAll(Set.of("schema", "minecraftVersion", "status"));
        require(report.keySet().equals(fields), loader + ": missing/unknown client claims");
        require(report.get("schema").getAsInt() == 1, loader + ": unknown client schema");
        require(version.equals(report.get("minecraftVersion").getAsString()), loader + ": wrong client engine version");
        require("PASS".equals(report.get("status").getAsString()), loader + ": client did not pass");
        for (String claim : CLAIMS) {
            require(report.get(claim).isJsonPrimitive() && report.getAsJsonPrimitive(claim).isBoolean()
                    && report.get(claim).getAsBoolean(), loader + ": unproven " + claim);
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
