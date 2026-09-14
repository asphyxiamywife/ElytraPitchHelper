package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CoreUtilitiesFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void numericMetadataNamingAndColorUtilitiesKeepTheirContracts(FuzzedDataProvider data) {
        fuzzExactIntegers(data);
        fuzzMetadata(data);
        fuzzProfileNames(data);
        fuzzColorUtilities(data);
    }

    private static void fuzzExactIntegers(FuzzedDataProvider data) {
        long value = data.consumeLong();
        Integer parsed = ConfigJsonReader.parseExactInt(new JsonPrimitive(value));
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            assertEquals((int) value, parsed);
        } else {
            assertNull(parsed);
        }

        double decimal = data.consumeRegularDouble(-1.0E12, 1.0E12);
        Integer parsedDecimal = ConfigJsonReader.parseExactInt(new JsonPrimitive(decimal));
        boolean exactInt = decimal >= Integer.MIN_VALUE && decimal <= Integer.MAX_VALUE
                && decimal == Math.rint(decimal);
        assertEquals(exactInt, parsedDecimal != null);
    }

    private static void fuzzMetadata(FuzzedDataProvider data) {
        ProfileMetadata metadata = new ProfileMetadata(
                data.consumeString(64),
                data.consumeString(64),
                data.consumeLong(),
                data.consumeLong()).ensured("Fallback");

        assertTrue(metadata.createdAtMillis() > 0L);
        assertTrue(metadata.lastModifiedAtMillis() > 0L);
        assertFalse(metadata.basedOn().isBlank());
    }

    private static void fuzzProfileNames(FuzzedDataProvider data) {
        int count = data.consumeInt(0, 16);
        List<Profile> profiles = new ArrayList<>();
        Set<String> existingNames = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String name = nonBlank(data.consumeString(32));
            Profile profile = new Profile().withName(name).withFileName(
                    ProfileFileNames.unique(name, ProfileNameAllocator.usedFileNames(profiles)));
            profiles.add(profile);
            existingNames.add(profile.name().toLowerCase(Locale.ROOT));
        }
        String allocated = ProfileNameAllocator.uniqueName(profiles, nonBlank(data.consumeString(32)));
        String copied = ProfileNameAllocator.uniqueCopyName(profiles,
                data.consumeBoolean() ? null : data.consumeString(32));

        assertFalse(existingNames.contains(allocated.toLowerCase(Locale.ROOT)));
        assertFalse(existingNames.contains(copied.toLowerCase(Locale.ROOT)));
    }

    private static void fuzzColorUtilities(FuzzedDataProvider data) {
        int from = data.consumeInt();
        int to = data.consumeInt();
        float amount = data.consumeRegularFloat(-10.0f, 10.0f);
        int blended = MathUtil.blendRgb(from, to, amount);
        int argb = MathUtil.argb(amount, from);

        assertEquals(0, blended & 0xFF000000);
        assertEquals(from & 0x00FFFFFF, argb & 0x00FFFFFF);
        if (amount <= 0.0f) {
            assertEquals(from & 0x00FFFFFF, blended);
        } else if (amount >= 1.0f) {
            assertEquals(to & 0x00FFFFFF, blended);
        }
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "Profile" : value;
    }
}
