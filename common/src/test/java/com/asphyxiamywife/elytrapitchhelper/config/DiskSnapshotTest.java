package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures.fs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiskSnapshotTest {
    @TempDir
    Path directory;

    @Test
    void missingPathIsReportedAsMissing() throws IOException {
        assertEquals(DiskSnapshot.MISSING, DiskSnapshot.capture(fs(), directory.resolve("absent.json")));
    }

    @Test
    void existingFileCapturesFingerprint() throws IOException {
        Path file = directory.resolve("present.json");
        Files.writeString(file, "{}", StandardCharsets.UTF_8);

        DiskSnapshot snapshot = DiskSnapshot.capture(fs(), file);

        assertTrue(snapshot.exists());
        assertNotNull(snapshot.fingerprint());
        assertTrue(snapshot.sameContent(DiskSnapshot.capture(fs(), file)));
    }

    @Test
    void resolvingSymlinkIsCapturedAsItsTarget() throws IOException {
        Path target = directory.resolve("target.json");
        Files.writeString(target, "{}", StandardCharsets.UTF_8);
        Path link = directory.resolve("link.json");
        createSymbolicLinkOrSkip(link, target);

        assertTrue(DiskSnapshot.capture(fs(), link).sameContent(DiskSnapshot.capture(fs(), target)));
    }

    @Test
    void danglingSymlinkIsRefusedRatherThanReportedMissing() throws IOException {
        Path link = directory.resolve("dangling.json");
        createSymbolicLinkOrSkip(link, directory.resolve("never-created.json"));

        assertFalse(Files.exists(link));
        assertThrows(NoSuchFileException.class, () -> DiskSnapshot.capture(fs(), link));
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable");
        }
    }
}
