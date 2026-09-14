package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Objects;

record LoadedFileState(boolean loaded, boolean exists, long modifiedAtMillis, String fingerprint) {
    static final LoadedFileState NEVER_LOADED = new LoadedFileState(false, false, 0L, null);

    static LoadedFileState existing(long modifiedAtMillis, String fingerprint) {
        return new LoadedFileState(
                true, true, Math.max(0L, modifiedAtMillis),
                validFingerprint(fingerprint) ? fingerprint : null);
    }

    boolean changedSince(DiskSnapshot disk) {
        if (!loaded) {
            return disk.exists();
        }
        if (exists != disk.exists()) {
            return true;
        }
        if (!disk.exists()) {
            return false;
        }
        if (disk.fingerprint() != null) {
            return fingerprint == null || !Objects.equals(fingerprint, disk.fingerprint());
        }
        return modifiedAtMillis != disk.modifiedAtMillis();
    }

    LoadedFileState syncFrom(DiskSnapshot disk) {
        return new LoadedFileState(true, disk.exists(), disk.modifiedAtMillis(), disk.fingerprint());
    }

    private static boolean validFingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
