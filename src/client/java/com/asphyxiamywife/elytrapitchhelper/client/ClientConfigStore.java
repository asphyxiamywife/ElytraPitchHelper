package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientConfigStore {
    private static final Object INITIALIZE_LOCK = new Object();
    private static final Object PUBLISH_LOCK = new Object();
    private static final Object SAVE_LOCK = new Object();
    private static final AtomicInteger ACTIVE_SAVES = new AtomicInteger();
    private static final AtomicLong REVISION = new AtomicLong();
    private static final AtomicLong UNSAVED_REVISION = new AtomicLong();

    private static volatile Config config;

    private ClientConfigStore() {
    }

    public static void initialize() {
        if (config != null) {
            return;
        }

        synchronized (INITIALIZE_LOCK) {
            if (config == null) {
                publish(Config.load());
            }
        }
    }

    public static void reloadFromDisk() {
        reloadFromDisk(false);
    }

    public static boolean reloadFromDiskIfIdle() {
        return reloadFromDisk(true);
    }

    public static void set(Config nextConfig) {
        Config snapshot = nextConfig.copy();
        ACTIVE_SAVES.incrementAndGet();
        try {
            long savedRevision = publish(snapshot);
            synchronized (SAVE_LOCK) {
                snapshot.save();
            }
            nextConfig.syncSavedProfileMetadataFrom(snapshot);
            UNSAVED_REVISION.updateAndGet(revision -> revision <= savedRevision ? 0L : revision);
        } finally {
            ACTIVE_SAVES.decrementAndGet();
        }
    }

    public static void update(Config nextConfig) {
        long updatedRevision = publish(nextConfig.copy());
        UNSAVED_REVISION.set(updatedRevision);
    }

    public static Config get() {
        Config current = config;
        if (current == null) {
            initialize();
            current = config;
        }
        return current;
    }

    public static long revision() {
        return REVISION.get();
    }

    private static boolean reloadFromDisk(boolean skipDuringSave) {
        if (skipDuringSave && (ACTIVE_SAVES.get() > 0 || UNSAVED_REVISION.get() != 0L)) {
            return false;
        }

        long observedRevision = REVISION.get();
        Config loaded = Config.reloadFromDisk();
        if (!skipDuringSave) {
            publish(loaded);
            UNSAVED_REVISION.set(0L);
            return true;
        } else {
            return publishIfUnchanged(loaded, observedRevision);
        }
    }

    private static long publish(Config nextConfig) {
        synchronized (PUBLISH_LOCK) {
            config = nextConfig;
            return REVISION.incrementAndGet();
        }
    }

    private static boolean publishIfUnchanged(Config nextConfig, long observedRevision) {
        synchronized (PUBLISH_LOCK) {
            if (ACTIVE_SAVES.get() == 0 && UNSAVED_REVISION.get() == 0L && REVISION.get() == observedRevision) {
                config = nextConfig;
                REVISION.incrementAndGet();
                return true;
            }
        }
        return false;
    }
}
