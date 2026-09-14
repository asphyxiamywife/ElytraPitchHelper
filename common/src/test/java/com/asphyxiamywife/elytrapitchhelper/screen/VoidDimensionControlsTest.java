package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VoidDimensionControlsTest {
    @Test
    void storedCurrentAndRememberedDimensionsRemainAvailableWithoutDuplicates() {
        Profile profile = new Profile();
        profile = profile.withVoidWarning(profile.voidWarning()
                .withDimensionYOverrides(Map.of("custom:stored", -20)));

        List<String> dimensions = VoidDimensionControls.dimensions(profile, "custom:current", "custom:remembered");

        assertEquals(List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether",
                "custom:stored", "custom:current", "custom:remembered"), dimensions);
        List<String> repeated = VoidDimensionControls.dimensions(profile, "custom:stored", "custom:stored");
        assertEquals(4, repeated.size());
        assertTrue(repeated.contains("custom:stored"));
    }

    @Test
    void outsideAWorldKnownDimensionsAreStillAvailable() {
        assertEquals(List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"),
                VoidDimensionControls.dimensions(new Profile(), null, null));
    }
}
