package com.asphyxiamywife.elytrapitchhelper.config;

record ProfileMetadata(String fileName, String basedOn, long createdAtMillis, long lastModifiedAtMillis) {
    ProfileMetadata {
        fileName = ProfileFileNames.normalize(fileName);
        basedOn = isMeaningful(basedOn) ? basedOn : null;
        createdAtMillis = Math.max(0L, createdAtMillis);
        lastModifiedAtMillis = Math.max(0L, lastModifiedAtMillis);
    }

    static ProfileMetadata empty(String fileName) {
        return new ProfileMetadata(fileName, null, 0L, 0L);
    }

    ProfileMetadata ensured(String fallbackBasedOn) {
        long now = System.currentTimeMillis();
        long created = createdAtMillis > 0L ? createdAtMillis : now;
        long modified = lastModifiedAtMillis > 0L ? lastModifiedAtMillis : created;
        String origin = isMeaningful(basedOn) ? basedOn : normalizedBasedOn(fallbackBasedOn);
        return new ProfileMetadata(fileName, origin, created, modified);
    }

    ProfileMetadata markedCreated(long timestamp, String origin) {
        long normalizedTimestamp = timestamp > 0L ? timestamp : System.currentTimeMillis();
        return new ProfileMetadata(fileName, normalizedBasedOn(origin),
                normalizedTimestamp, normalizedTimestamp);
    }

    ProfileMetadata markedModified(long timestamp, String origin) {
        long normalizedTimestamp = timestamp > 0L ? timestamp : System.currentTimeMillis();
        long created = createdAtMillis > 0L ? createdAtMillis : normalizedTimestamp;
        String nextOrigin = isMeaningful(origin) ? origin : basedOn;
        return new ProfileMetadata(fileName, nextOrigin, created, normalizedTimestamp);
    }

    ProfileMetadata markedLoaded(long fileModifiedAtMillis, String fallbackBasedOn,
            boolean changedSinceMetadata) {
        long timestamp = fileModifiedAtMillis > 0L ? fileModifiedAtMillis : System.currentTimeMillis();
        long created = createdAtMillis > 0L ? createdAtMillis : timestamp;
        long modified = lastModifiedAtMillis <= 0L || changedSinceMetadata
                ? timestamp : lastModifiedAtMillis;
        String origin = isMeaningful(basedOn) ? basedOn : normalizedBasedOn(fallbackBasedOn);
        return new ProfileMetadata(fileName, origin, created, modified);
    }

    @Override
    public String basedOn() {
        return normalizedBasedOn(basedOn);
    }

    private static String normalizedBasedOn(String value) {
        return isMeaningful(value) ? value : "Custom";
    }

    private static boolean isMeaningful(String value) {
        return value != null && !value.isBlank();
    }
}
