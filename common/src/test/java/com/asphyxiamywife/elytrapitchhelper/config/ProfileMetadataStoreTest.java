package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

final class ProfileMetadataStoreTest {
    @Test
    void lookupIsPureAndMissingMetadataIsNotInserted() {
        ProfileMetadataStore store = new ProfileMetadataStore();
        store.markCreated("../Alpha.JSON", 100L, "Hand Tuned");
        List<ProfileMetadata> before = List.copyOf(store.profiles);

        ProfileMetadata found = store.profile("alpha.json");
        ProfileMetadata missing = store.profile("missing.json");

        assertEquals(before, store.profiles);
        assertEquals("alpha.json", found.fileName());
        assertEquals("Hand Tuned", found.basedOn());
        assertEquals(100L, found.createdAtMillis());
        assertEquals("missing.json", missing.fileName());
    }

    @Test
    void copyAndPruneDoNotMutateTheSource() {
        ProfileMetadataStore store = new ProfileMetadataStore();
        store.markCreated("alpha.json", 100L, "Test");
        store.markCreated("beta.json", 200L, "Test");

        ProfileMetadataStore copy = store.copy();
        copy.markModified("alpha.json", 300L, "Changed");
        copy.prune(Set.of("ALPHA.JSON"));

        assertNotSame(store.profiles, copy.profiles);
        assertEquals("Test", store.profile("alpha.json").basedOn());
        assertEquals(2, store.profiles.size());
        assertEquals(1, copy.profiles.size());
        assertEquals("ALPHA.JSON", copy.profiles.get(0).fileName());
    }

    @Test
    void canonicalLookupKeepsUnicodeDistinctFileNamesSeparate() {
        ProfileMetadataStore store = new ProfileMetadataStore();
        store.markCreated("İ.json", 100L, "Test");
        store.markCreated("i.json", 200L, "Test");

        assertEquals("İ.json", store.profile("İ.json").fileName());
        assertEquals("i.json", store.profile("i.json").fileName());
        assertEquals(2, store.profiles.size());
    }

    @Test
    void serializationToleratesMissingOrMalformedProfileEntries() {
        ProfileMetadataStore missing = new ProfileMetadataStore();
        missing.profiles = null;
        assertFalse(missing.toJson(StoreMetadata.EMPTY).has("profiles"));

        ProfileMetadataStore malformed = new ProfileMetadataStore();
        malformed.profiles = new ArrayList<>();
        malformed.profiles.add(null);
        assertEquals(1, malformed.toJson(StoreMetadata.EMPTY).getAsJsonArray("profiles").size());
    }
}
