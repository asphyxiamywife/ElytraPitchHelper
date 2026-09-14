package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SettingsFieldCoverageTest {
    private static final List<Class<?>> VALUE_TYPES = List.of(
            Profile.class,
            VisibilitySettings.class,
            PitchSettings.class,
            LineSettings.class,
            AmplitudeSettings.class,
            VoidWarningSettings.class,
            CommandPaletteAppearanceSettings.class,
            DiagnosticsSettings.class,
            CommandPaletteUsageSettings.class);

    @Test
    void everySettingsValueTypeIsAnImmutableRecord() {
        for (Class<?> type : VALUE_TYPES) {
            assertTrue(type.isRecord(), () -> type.getSimpleName() + " must remain a record");
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    assertTrue(Modifier.isFinal(field.getModifiers()),
                            () -> type.getSimpleName() + "." + field.getName() + " is writable");
                }
            }
        }
    }

    @Test
    void profileCanonicalConstructorFillsNullSettings() {
        Profile profile = new Profile(Config.CURRENT_VERSION, "Test", null, null, null,
                null, null, null, null, "test.json");

        assertNotNull(profile.visibility());
        assertNotNull(profile.pitch());
        assertNotNull(profile.line());
        assertNotNull(profile.amplitude());
        assertNotNull(profile.voidWarning());
        assertNotNull(profile.commandPaletteAppearance());
        assertNotNull(profile.diagnostics());
    }

    @Test
    void profileWithersStructurallyShareUnchangedSettings() {
        Profile source = ConfigTestFixtures.tunedProfile("Source", "source.json");
        Profile changed = source.withPitch(source.pitch().withTargetUpMinecraft(-25.0f));

        assertEquals(-25.0f, changed.pitch().targetUpMinecraft());
        assertFalse(source.equals(changed));
        assertSame(source.visibility(), changed.visibility());
        assertSame(source.line(), changed.line());
        assertSame(source.amplitude(), changed.amplitude());
        assertSame(source.voidWarning(), changed.voidWarning());
    }

    @Test
    void configReadsSettingsDirectlyFromActiveProfileWithoutAliasFields() {
        Profile profile = ConfigTestFixtures.tunedProfile("Test", "test.json");
        Config config = ConfigTestFixtures.configWith(profile);

        assertSame(profile.pitch(), config.pitch());
        assertSame(profile.line(), config.line());
        assertSame(profile.amplitude(), config.amplitude());
        assertSame(profile.voidWarning(), config.voidWarning());
        assertFalse(hasDeclaredField(Config.class, "pitch"));
        assertFalse(hasDeclaredField(Config.class, "line"));
    }

    @Test
    void persistenceBaselinesAreNotFlattenedIntoConfigOrProfileMetadata() {
        assertFalse(hasDeclaredField(Config.class, "mainConfigFileLoaded"));
        assertFalse(hasDeclaredField(Config.class, "mainConfigFileExists"));
        assertFalse(hasDeclaredField(Config.class, "mainConfigFileModifiedAtMillis"));
        assertFalse(hasDeclaredField(Config.class, "mainConfigFileFingerprint"));
        assertFalse(hasDeclaredField(ProfileMetadata.class, "loadedFileModifiedAtMillis"));
        assertFalse(hasDeclaredField(ProfileMetadata.class, "loadedFileFingerprint"));
        assertTrue(ConfigDocument.class.isRecord());
        assertTrue(StoreMetadata.class.isRecord());
        assertTrue(ConfigState.class.isRecord());
    }

    private static boolean hasDeclaredField(Class<?> owner, String name) {
        try {
            owner.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException missing) {
            return false;
        }
    }
}
