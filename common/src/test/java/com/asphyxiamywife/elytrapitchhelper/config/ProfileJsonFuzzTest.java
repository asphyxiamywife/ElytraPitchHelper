package com.asphyxiamywife.elytrapitchhelper.config;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static com.asphyxiamywife.elytrapitchhelper.config.ProfileInvariants.assertValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ProfileJsonFuzzTest {
    @MethodSource("validProfileSeeds")
    @FuzzTest(maxDuration = "30s")
    void arbitraryJsonProducesAValidSanitizedProfile(byte[] input) {
        JsonElement json;
        try {
            json = JsonParser.parseString(new String(input, StandardCharsets.UTF_8));
        } catch (JsonParseException expectedForMalformedInput) {
            return;
        }

        if (!json.isJsonObject()) {
            return;
        }

        Profile defaults = new Profile();
        defaults = defaults.withName("Default");
        Profile profile = ProfileJson.parse(json.getAsJsonObject(), "fuzz.json", defaults, null);
        profile = profile.sanitized(null, defaults);

        assertEquals("fuzz.json", profile.fileName());
        assertNotNull(profile.name());
        assertFalse(profile.name().isBlank());
        assertValid(profile);
    }

    private static Stream<Arguments> validProfileSeeds() {
        return Stream.of(
                "{}",
                "{\"name\":\"Fuzz\",\"pitch\":{\"targetUpMinecraft\":-40}}",
                "{\"line\":{\"customPrideColors\":[0,16777215]}}",
                "{\"voidWarning\":{\"dimensionYOverrides\":{\"minecraft:overworld\":-64}}}",
                "{\"targetUpMinecraft\":-35,\"lineWidthPixels\":3}")
                .map(seed -> Arguments.of((Object) seed.getBytes(StandardCharsets.UTF_8)));
    }
}
