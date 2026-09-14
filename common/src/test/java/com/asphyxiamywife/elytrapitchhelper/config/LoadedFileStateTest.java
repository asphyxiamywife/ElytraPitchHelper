package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LoadedFileStateTest {
    @Test
    void neverLoadedStateOnlyConflictsWithAnExistingFile() {
        assertFalse(LoadedFileState.NEVER_LOADED.changedSince(DiskSnapshot.MISSING));
        assertTrue(LoadedFileState.NEVER_LOADED.changedSince(snapshot(10L, "a")));
    }

    @Test
    void existenceChangesConflictAfterLoad() {
        assertTrue(LoadedFileState.existing(10L, fingerprint("a")).changedSince(DiskSnapshot.MISSING));
        assertTrue(LoadedFileState.NEVER_LOADED.syncFrom(DiskSnapshot.MISSING)
                .changedSince(snapshot(10L, "a")));
    }

    @Test
    void fingerprintWinsWhenBothSnapshotsHaveOne() {
        LoadedFileState loaded = LoadedFileState.existing(10L, fingerprint("a"));

        assertFalse(loaded.changedSince(snapshot(20L, "a")));
        assertTrue(loaded.changedSince(snapshot(10L, "b")));
    }

    @Test
    void aLegacyStateWithoutFingerprintConflictsConservatively() {
        LoadedFileState loaded = LoadedFileState.existing(10L, null);

        assertTrue(loaded.changedSince(snapshot(10L, "a")));
        assertTrue(loaded.changedSince(snapshot(20L, "a")));
    }

    private static DiskSnapshot snapshot(long modifiedAtMillis, String fingerprintCharacter) {
        return new DiskSnapshot(true, modifiedAtMillis, fingerprint(fingerprintCharacter));
    }

    private static String fingerprint(String character) {
        return character.repeat(64);
    }
}
