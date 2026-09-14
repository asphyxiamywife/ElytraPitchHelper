package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingsWitherTest {
    private static final List<Object> SAMPLES = List.of(
            new VisibilitySettings(),
            new PitchSettings(),
            new LineSettings(),
            new AmplitudeSettings(),
            new VoidWarningSettings(),
            new CommandPaletteAppearanceSettings(),
            new Profile());

    @Test
    void everyWitherChangesOnlyItsOwnComponent() throws Exception {
        int checked = 0;
        for (Object sample : SAMPLES) {
            Class<?> type = sample.getClass();
            assertTrue(type.isRecord(), type.getSimpleName() + " must be a record");
            Map<String, RecordComponent> components = new LinkedHashMap<>();
            for (RecordComponent component : type.getRecordComponents()) {
                components.put(component.getName(), component);
            }

            for (Method wither : type.getDeclaredMethods()) {
                String target = witherTarget(wither, type, components.keySet());
                if (target == null) {
                    continue;
                }
                Object replacement = differentValue(read(components.get(target), sample));
                if (replacement == null) {
                    continue;
                }

                Object updated = wither.invoke(sample, replacement);
                checked++;

                List<String> unexpected = new ArrayList<>();
                for (Map.Entry<String, RecordComponent> entry : components.entrySet()) {
                    if (entry.getKey().equals(target)) {
                        continue;
                    }
                    Object before = read(entry.getValue(), sample);
                    Object after = read(entry.getValue(), updated);
                    if (!Objects.deepEquals(before, after)) {
                        unexpected.add(entry.getKey());
                    }
                }
                assertTrue(unexpected.isEmpty(),
                        type.getSimpleName() + "." + wither.getName()
                                + " also changed " + unexpected);
                assertFalse(Objects.deepEquals(read(components.get(target), sample),
                                read(components.get(target), updated)),
                        type.getSimpleName() + "." + wither.getName() + " did not change " + target);
            }
        }
        assertEquals(49, checked, "wither coverage changed; update this count deliberately");
    }

    private static String witherTarget(Method method, Class<?> owner, Iterable<String> componentNames) {
        if (!method.getName().startsWith("with")
                || method.getParameterCount() != 1
                || method.getReturnType() != owner) {
            return null;
        }
        String suffix = method.getName().substring("with".length());
        String candidate = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        for (String name : componentNames) {
            if (name.equals(candidate)) {
                return name;
            }
        }
        return null;
    }

    private static Object read(RecordComponent component, Object instance)
            throws IllegalAccessException, InvocationTargetException {
        return component.getAccessor().invoke(instance);
    }

    private static Object differentValue(Object current) {
        return switch (current) {
            case Boolean value -> !value;
            case Integer value -> value + 1;
            case Float value -> value + 1.0f;
            case Long value -> value + 1L;
            case String value -> value + "-changed";
            case int[] value -> {
                int[] next = new int[value.length + 1];
                System.arraycopy(value, 0, next, 0, value.length);
                next[value.length] = 0x123456;
                yield next;
            }
            case Map<?, ?> value -> {
                Map<String, Integer> next = new LinkedHashMap<>();
                value.forEach((key, mapped) -> next.put(String.valueOf(key), (Integer) mapped));
                next.put("minecraft:the_end", 12);
                yield next;
            }
            case null, default -> null;
        };
    }
}
