package com.asphyxiamywife.elytrapitchhelper.testing;

import com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore;
import com.asphyxiamywife.elytrapitchhelper.client.ConfigStore;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigFileSystem;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IsolatedConfigRootExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(IsolatedConfigRootExtension.class);
    private static final String ROOT_KEY = "configRoot";
    private static final String PREVIOUS_STORE_KEY = "previousStore";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Field field = isolatedRootField(context.getRequiredTestClass());
        Path root = Files.createTempDirectory("elytra-pitch-helper-test-");
        context.getStore(NAMESPACE).put(ROOT_KEY, root);
        context.getStore(NAMESPACE).put(PREVIOUS_STORE_KEY,
                new StoreDependencies(ClientConfigStore.store().fileSystem()));
        field.setAccessible(true);
        field.set(context.getRequiredTestInstance(), root);
        ClientConfigStore.setStore(new ConfigStore(new FaultingConfigFileSystem(root)));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Path root = context.getStore(NAMESPACE).remove(ROOT_KEY, Path.class);
        StoreDependencies previous =
                context.getStore(NAMESPACE).remove(PREVIOUS_STORE_KEY, StoreDependencies.class);
        try {
            ClientConfigStore.setStore(new ConfigStore(previous.fileSystem()));
        } finally {
            deleteRecursively(root);
        }
    }

    private static Field isolatedRootField(Class<?> testClass) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = testClass; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(IsolatedConfigRoot.class)) {
                    fields.add(field);
                }
            }
        }
        if (fields.size() != 1) {
            throw new IllegalStateException(
                    "Exactly one @IsolatedConfigRoot field is required, found " + fields.size());
        }
        Field field = fields.getFirst();
        if (field.getType() != Path.class) {
            throw new IllegalStateException("@IsolatedConfigRoot field must have type Path");
        }
        return field;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record StoreDependencies(ConfigFileSystem fileSystem) {
    }
}
