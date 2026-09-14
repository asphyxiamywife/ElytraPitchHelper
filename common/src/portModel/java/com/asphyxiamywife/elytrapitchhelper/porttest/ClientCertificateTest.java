package com.asphyxiamywife.elytrapitchhelper.porttest;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClientCertificateTest {
    @TempDir Path reports;
    private JsonObject complete() {
        JsonObject report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("minecraftVersion", "26.1.2");
        report.addProperty("status", "PASS");
        for (String claim : ClientCertificate.CLAIMS) report.addProperty(claim, true);
        return report;
    }
    @Test void validatesOnlyCompleteCurrentVersion() {
        assertDoesNotThrow(() -> ClientCertificate.validate(complete(), "26.1.2", "fabric"));
        assertThrows(IllegalStateException.class, () -> ClientCertificate.validate(complete(), "26.future", "fabric"));
    }
    @Test void rejectsMissingFalseAndStringClaims() {
        for (String claim : ClientCertificate.CLAIMS) {
            JsonObject report = complete();
            report.remove(claim);
            assertThrows(IllegalStateException.class, () -> ClientCertificate.validate(report, "26.1.2", "fabric"));
            report.addProperty(claim, false);
            assertThrows(IllegalStateException.class, () -> ClientCertificate.validate(report, "26.1.2", "fabric"));
            report.addProperty(claim, "true");
            assertThrows(IllegalStateException.class, () -> ClientCertificate.validate(report, "26.1.2", "fabric"));
        }
    }
    @Test void rejectsAbsentClientEvenIfProcessExitedNormally() {
        assertThrows(IllegalStateException.class, () -> ClientCertificate.main(new String[]{"26.1.2", reports.toString()}));
    }
    @Test void rejectsFailedOrUnknownReport() {
        JsonObject report = complete();
        report.addProperty("status", "FAIL");
        assertThrows(IllegalStateException.class, () -> ClientCertificate.validate(report, "26.1.2", "fabric"));
        report.addProperty("status", "PASS");
        report.addProperty("schema", 2);
        assertThrows(IllegalStateException.class, () -> ClientCertificate.validate(report, "26.1.2", "fabric"));
    }
}
