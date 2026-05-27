package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ElytraPitchHelperClient;
import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("smoke")
final class FabricClientSmokeTest {
    private static final Gson GSON = new Gson();

    @TempDir
    Path configRoot;

    @AfterEach
    void clearConfigRoot() {
        Config.setConfigRootOverrideForTests(null);
    }

    @Test
    void packagedClientEntrypointIsConstructibleAndConfigRoundTrips() throws Exception {
        Config.setConfigRootOverrideForTests(configRoot);
        String entrypoint = clientEntrypointClassName();

        Class<?> entrypointClass = Class.forName(entrypoint);
        assertTrue(ClientModInitializer.class.isAssignableFrom(entrypointClass));
        assertDoesNotThrow(() -> instantiate(entrypointClass));

        Config config = new Config();
        config.enabled = false;
        ClientConfigStore.set(config);

        ElytraPitchHelperClient.reloadConfig();

        Config reloaded = ElytraPitchHelperClient.getConfig();
        assertFalse(reloaded.enabled);
        assertTrue(reloaded.profileCount() > 0);
        assertTrue(Files.exists(Config.getConfigPath()));
        assertTrue(Files.isDirectory(Config.getProfileDirectory()));
    }

    private static String clientEntrypointClassName() throws IOException {
        InputStream stream = FabricClientSmokeTest.class.getClassLoader().getResourceAsStream("fabric.mod.json");
        assertNotNull(stream);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            assertNotNull(root);
            return root.getAsJsonObject("entrypoints").getAsJsonArray("client").get(0).getAsString();
        }
    }

    private static Object instantiate(Class<?> entrypointClass) throws NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {
        return entrypointClass.getDeclaredConstructor().newInstance();
    }
}
