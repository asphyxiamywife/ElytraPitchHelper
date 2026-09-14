package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.neoforge.ElytraPitchHelperNeoForgeClient;
import com.asphyxiamywife.elytrapitchhelper.testing.IsolatedConfigRoot;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("smoke")
final class NeoForgeClientSmokeTest {
    @IsolatedConfigRoot
    Path configRoot;

    @Test
    void packagedClientEntrypointAndConfigRoundTrip() throws Exception {
        Class<?> entrypoint = Class.forName(
                "com.asphyxiamywife.elytrapitchhelper.neoforge.ElytraPitchHelperNeoForgeClient");

        Annotation mod = Arrays.stream(entrypoint.getAnnotations())
                .filter(annotation -> annotation.annotationType().getName().equals("net.neoforged.fml.common.Mod"))
                .findFirst()
                .orElse(null);
        assertNotNull(mod);
        Method value = mod.annotationType().getMethod("value");
        assertEquals("elytrapitchhelper", value.invoke(mod));
        assertTrue(Arrays.stream(entrypoint.getConstructors()).anyMatch(constructor -> {
            Class<?>[] parameters = constructor.getParameterTypes();
            return parameters.length == 2
                    && parameters[0].getName().equals("net.neoforged.bus.api.IEventBus")
                    && parameters[1].getName().equals("net.neoforged.fml.ModContainer");
        }));

        IEventBus modBus = BusBuilder.builder().build();
        ModContainer modContainer = testModContainer(modBus);
        new ElytraPitchHelperNeoForgeClient(modBus, modContainer);
        assertTrue(modContainer.getCustomExtension(IConfigScreenFactory.class).isPresent());

        Config config = ClientConfigStore.get().copy();
        config.enabled = false;
        assertEquals(ClientConfigStore.SaveOutcome.COMMITTED,
                ClientConfigStore.saveAsync(config).get(10L, TimeUnit.SECONDS).result().outcome());
        entrypoint.getMethod("reloadConfig").invoke(null);

        Config reloaded = (Config) entrypoint.getMethod("getConfig").invoke(null);
        assertFalse(reloaded.enabled);
        assertTrue(reloaded.profileCount() > 0);
        assertTrue(Files.exists(ClientConfigStore.store().configPath()));
        assertTrue(Files.isDirectory(ClientConfigStore.store().profileDirectory()));
    }

    private static ModContainer testModContainer(IEventBus modBus) {
        IModInfo modInfo = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(),
                new Class<?>[] { IModInfo.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getModId", "getNamespace" -> "elytrapitchhelper";
                    case "getDisplayName" -> "Elytra Pitch Helper";
                    case "getDescription" -> "Smoke test mod container";
                    case "getDependencies", "getForgeFeatures" -> List.of();
                    case "getModProperties" -> Map.of();
                    case "getUpdateURL", "getModURL", "getLogoFile" -> Optional.empty();
                    case "getLogoBlur" -> false;
                    default -> null;
                });
        return new ModContainer(modInfo) {
            @Override
            public IEventBus getEventBus() {
                return modBus;
            }
        };
    }
}
