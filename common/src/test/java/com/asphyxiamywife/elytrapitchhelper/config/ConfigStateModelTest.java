package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigStateModelTest {
    @Test
    void documentIsIndependentFromFilesystemBaselines() {
        Config config = ConfigTestFixtures.configWith(
                ConfigTestFixtures.tunedProfile("Alpha", "alpha.json"));
        ConfigDocument before = config.document();

        config.restoreMainConfigFileState(LoadedFileState.existing(100L, "a".repeat(64)));
        config.restoreProfileFileState("alpha.json", LoadedFileState.existing(200L, "b".repeat(64)));

        assertEquals(before, config.document());
        assertNotEquals(StoreMetadata.EMPTY, config.state().metadata());
    }

    @Test
    void configDocumentDefensivelyCopiesItsProfileList() {
        List<Profile> profiles = new ArrayList<>();
        profiles.add(ConfigTestFixtures.tunedProfile("Alpha", "alpha.json"));
        ConfigDocument document = new ConfigDocument(
                Config.CURRENT_VERSION, true, 0, "alpha.json", Config.PROFILE_SORT_CREATED,
                new CommandPaletteUsageSettings(), new SectionCollapseSettings(), false, profiles);

        profiles.clear();

        assertEquals(1, document.profiles().size());
        assertThrows(UnsupportedOperationException.class,
                () -> document.profiles().add(new Profile()));
    }

    @Test
    void storeMetadataProfileLookupIsPureAndCaseInsensitive() {
        LoadedFileState loaded = LoadedFileState.existing(200L, "b".repeat(64));
        StoreMetadata metadata = new StoreMetadata(
                LoadedFileState.NEVER_LOADED, Map.of("Alpha.JSON", loaded));

        assertEquals(loaded, metadata.profile("alpha.json"));
        assertEquals(1, metadata.profiles().size());
        assertEquals(LoadedFileState.NEVER_LOADED, metadata.profile("missing.json"));
        assertEquals(1, metadata.profiles().size());
    }

    @Test
    void loadResultCarriesReadOnlyModeOutsideTheDocument() {
        Config config = ConfigTestFixtures.configWith(
                ConfigTestFixtures.tunedProfile("Alpha", "alpha.json"));
        ConfigState state = config.state();
        LoadResult loaded = new LoadResult.Loaded(state);
        LoadResult readOnly = new LoadResult.ReadOnly(state, "newer version");

        assertFalse(loaded.readOnly());
        assertTrue(readOnly.readOnly());
        assertEquals(loaded.state().document(), readOnly.state().document());
        assertEquals("newer version", readOnly.warning());
        assertInstanceOf(LoadResult.ReadOnly.class, readOnly);
    }
}
