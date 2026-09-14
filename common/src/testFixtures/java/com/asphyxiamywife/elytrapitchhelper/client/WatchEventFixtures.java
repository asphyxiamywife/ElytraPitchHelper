package com.asphyxiamywife.elytrapitchhelper.client;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

public final class WatchEventFixtures {
    private WatchEventFixtures() {
    }

    public static WatchEvent<Path> modified(String fileName) {
        return event(Path.of(fileName), false);
    }

    public static WatchEvent<Path> overflow() {
        return event(null, true);
    }

    public static WatchEvent<Path> event(Path path, boolean overflow) {
        return new WatchEvent<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Kind<Path> kind() {
                return overflow ? (Kind<Path>) (Kind<?>) StandardWatchEventKinds.OVERFLOW
                        : StandardWatchEventKinds.ENTRY_MODIFY;
            }

            @Override
            public int count() {
                return 1;
            }

            @Override
            public Path context() {
                return overflow ? null : path;
            }
        };
    }
}
