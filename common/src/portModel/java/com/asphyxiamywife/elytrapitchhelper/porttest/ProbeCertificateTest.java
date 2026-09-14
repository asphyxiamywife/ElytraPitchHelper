package com.asphyxiamywife.elytrapitchhelper.porttest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProbeCertificateTest {
    @TempDir Path root;

    private Path corpus() { return root.resolve("corpus"); }
    private Path reports() { return root.resolve("reports"); }
    private void write(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
    }
    private void fixture() throws Exception {
        write(corpus().resolve("probe-policy.json"), """
                {"schema":1,"referenceVersion":"26.1.2","scenarios":["test"],
                 "fields":{"tick":{"comparison":"EXACT"},"y":{"comparison":"EPSILON","epsilon":0.01}}}
                """);
        Path scenario = corpus().resolve("scenarios/flight/test.json");
        write(scenario, "{\"schema\":1,\"samples\":[{}]}");
        String observation = "{\"scenario\":\"test\",\"schema\":1,\"scenarioSha256\":\""
                + ProbeCertificate.digest(scenario) + "\",\"samples\":[{\"tick\":1,\"y\":2.0}]}";
        for (String loader : new String[]{"fabric", "neoforge"}) {
            write(reports().resolve(loader + "/test.json"), observation);
            write(reports().resolve(loader + "/complete.json"), "{\"schema\":1,\"minecraftVersion\":\"26.future\",\"scenarios\":[\"test\"],\"policySha256\":\""
                    + ProbeCertificate.digest(corpus().resolve("probe-policy.json")) + "\"}");
        }
    }
    private void mutate(String loader, String field, JsonElement value) throws Exception {
        Path path = reports().resolve(loader + "/test.json");
        JsonObject data = ProbeCertificate.read(path);
        data.getAsJsonArray("samples").get(0).getAsJsonObject().add(field, value);
        write(path, data.toString());
    }
    @Test void referencePromotionRefusedBeforeReadingOrWriting() {
        var failure = assertThrows(IllegalStateException.class,
                () -> ProbeCertificate.evaluate(corpus(), "26.1.2", reports(), true));
        assertEquals(ProbeCertificate.REFERENCE_REFUSAL, failure.getMessage());
        assertFalse(Files.exists(corpus()));
    }
    @Test void unknownVersionProducesUnreviewedReportWithoutGoldens() throws Exception {
        fixture();
        var failure = assertThrows(IllegalStateException.class,
                () -> ProbeCertificate.evaluate(corpus(), "26.future", reports(), false));
        assertTrue(failure.getMessage().startsWith("UNREVIEWED"));
        assertEquals("UNREVIEWED", ProbeCertificate.read(reports().resolve("probe-summary.json")).get("status").getAsString());
        assertFalse(Files.exists(corpus().resolve("probe-goldens")));
    }
    @Test void explicitNonReferencePromotionPreservesInputsAndReference() throws Exception {
        fixture();
        Path input = corpus().resolve("scenarios/flight/test.json");
        String before = Files.readString(input);
        Path reference = corpus().resolve("probe-goldens/26.1.2/test.json");
        write(reference, "reference sentinel");
        ProbeCertificate.evaluate(corpus(), "26.future", reports(), true);
        ProbeCertificate.evaluate(corpus(), "26.future", reports(), false);
        assertEquals(before, Files.readString(input));
        assertEquals("reference sentinel", Files.readString(reference));
        assertEquals("PASS", ProbeCertificate.read(reports().resolve("probe-summary.json")).get("status").getAsString());
    }
    @Test void staleInputDigestCannotBePromoted() throws Exception {
        fixture();
        Files.writeString(corpus().resolve("scenarios/flight/test.json"), " ", StandardOpenOption.APPEND);
        assertThrows(IllegalStateException.class, () -> ProbeCertificate.evaluate(corpus(), "26.future", reports(), true));
        assertFalse(Files.exists(corpus().resolve("probe-goldens")));
    }
    @Test void loaderDivergenceCannotBePromoted() throws Exception {
        fixture();
        mutate("neoforge", "tick", new JsonPrimitive(2));
        assertThrows(IllegalStateException.class, () -> ProbeCertificate.evaluate(corpus(), "26.future", reports(), true));
        assertFalse(Files.exists(corpus().resolve("probe-goldens")));
    }
    @Test void bothLoadersMustIndependentlyMatchAcceptedTolerance() throws Exception {
        fixture();
        ProbeCertificate.evaluate(corpus(), "26.future", reports(), true);
        mutate("fabric", "y", new JsonPrimitive(2.009));
        mutate("neoforge", "y", new JsonPrimitive(2.018));
        assertThrows(IllegalStateException.class, () -> ProbeCertificate.evaluate(corpus(), "26.future", reports(), false));
    }
    @Test void unclassifiedAndNonfiniteFieldsFail() throws Exception {
        fixture();
        mutate("fabric", "extra", new JsonPrimitive(1));
        assertThrows(IllegalStateException.class, () -> ProbeCertificate.evaluate(corpus(), "26.future", reports(), true));
        fixture();
        mutate("fabric", "y", new JsonPrimitive("NaN"));
        assertThrows(IllegalStateException.class, () -> ProbeCertificate.evaluate(corpus(), "26.future", reports(), true));
    }
    @Test void incompleteRunCannotReuseObservations() throws Exception {
        fixture();
        Files.delete(reports().resolve("neoforge/complete.json"));
        assertThrows(Exception.class, () -> ProbeCertificate.evaluate(corpus(), "26.future", reports(), true));
        assertFalse(Files.exists(corpus().resolve("probe-goldens")));
    }
}
