package com.asphyxiamywife.elytrapitchhelper.config;

final class ProfileMetadata {
    String fileName;
    String basedOn;
    long createdAtMillis;
    long lastModifiedAtMillis;
    long loadedFileModifiedAtMillis;

    ProfileMetadata copy() {
        ProfileMetadata copy = new ProfileMetadata();
        copy.fileName = fileName;
        copy.basedOn = basedOn;
        copy.createdAtMillis = createdAtMillis;
        copy.lastModifiedAtMillis = lastModifiedAtMillis;
        copy.loadedFileModifiedAtMillis = loadedFileModifiedAtMillis;
        return copy;
    }

    void restoreFromKeepingLoadedFile(ProfileMetadata snapshot) {
        if (snapshot == null) {
            return;
        }
        long currentLoadedFileModifiedAtMillis = loadedFileModifiedAtMillis;
        fileName = snapshot.fileName;
        basedOn = snapshot.basedOn;
        createdAtMillis = snapshot.createdAtMillis;
        lastModifiedAtMillis = snapshot.lastModifiedAtMillis;
        loadedFileModifiedAtMillis = currentLoadedFileModifiedAtMillis;
    }

    void ensure(String fallbackBasedOn) {
        long now = System.currentTimeMillis();
        if (createdAtMillis <= 0L) {
            createdAtMillis = now;
        }
        if (lastModifiedAtMillis <= 0L) {
            lastModifiedAtMillis = createdAtMillis;
        }
        if (basedOn == null || basedOn.isBlank()) {
            basedOn = fallbackBasedOn == null || fallbackBasedOn.isBlank() ? "Custom" : fallbackBasedOn;
        }
    }

    void markCreated(long timestamp, String basedOn) {
        createdAtMillis = timestamp;
        lastModifiedAtMillis = timestamp;
        loadedFileModifiedAtMillis = 0L;
        this.basedOn = basedOn == null || basedOn.isBlank() ? "Custom" : basedOn;
    }

    void markModified(long timestamp) {
        markModified(timestamp, null);
    }

    void markModified(long timestamp, String basedOn) {
        if (createdAtMillis <= 0L) {
            createdAtMillis = timestamp;
        }
        lastModifiedAtMillis = timestamp;
        if (basedOn != null && !basedOn.isBlank()) {
            this.basedOn = basedOn;
        } else if (this.basedOn == null || this.basedOn.isBlank()) {
            this.basedOn = "Custom";
        }
    }

    void markLoaded(long fileModifiedAtMillis, String fallbackBasedOn) {
        long timestamp = fileModifiedAtMillis > 0L ? fileModifiedAtMillis : System.currentTimeMillis();
        boolean changedSinceMetadata = loadedFileModifiedAtMillis > 0L
                && fileModifiedAtMillis > loadedFileModifiedAtMillis;
        if (createdAtMillis <= 0L) {
            createdAtMillis = timestamp;
        }
        if (lastModifiedAtMillis <= 0L || changedSinceMetadata) {
            lastModifiedAtMillis = timestamp;
        }
        loadedFileModifiedAtMillis = fileModifiedAtMillis;
        if (basedOn == null || basedOn.isBlank()) {
            basedOn = fallbackBasedOn == null || fallbackBasedOn.isBlank() ? "Custom" : fallbackBasedOn;
        }
    }

    void syncLoadedFileModifiedAtMillis(long fileModifiedAtMillis) {
        loadedFileModifiedAtMillis = fileModifiedAtMillis;
        if (lastModifiedAtMillis <= 0L) {
            lastModifiedAtMillis = fileModifiedAtMillis;
        }
    }

    void syncLoadedFileFrom(ProfileMetadata current) {
        if (current != null) {
            loadedFileModifiedAtMillis = current.loadedFileModifiedAtMillis;
        }
    }

    void sanitize(String fallbackBasedOn) {
        if (loadedFileModifiedAtMillis < 0L) {
            loadedFileModifiedAtMillis = 0L;
        }
        ensure(fallbackBasedOn);
    }

    String basedOn() {
        return basedOn == null || basedOn.isBlank() ? "Custom" : basedOn;
    }
}
