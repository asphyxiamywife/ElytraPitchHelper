package com.asphyxiamywife.elytrapitchhelper.client;

import com.asphyxiamywife.elytrapitchhelper.config.Config;

import java.util.List;

public final class ClientConfigStoreTestSupport {
    private ClientConfigStoreTestSupport() {
    }

    public static void save(Config nextConfig) {
        save(nextConfig, List.of());
    }

    public static void save(Config nextConfig, List<String> deletedProfileFiles) {
        ClientConfigStore.saveNow(nextConfig, deletedProfileFiles);
    }

    public static ClientConfigStore.SaveResult trySave(Config nextConfig, List<String> deletedProfileFiles) {
        return ClientConfigStore.trySet(nextConfig, deletedProfileFiles);
    }

    public static void savePending() {
        Config pending = ClientConfigStore.pendingSaveConfig();
        if (pending != null) {
            save(pending, ClientConfigStore.pendingProfileDeletes());
        }
    }
}
