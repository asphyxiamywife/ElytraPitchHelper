package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LegacyFlashDefaultTest {
    @Test
    void profileJsonWithoutTheFlashFieldKeepsFullIntensity() {
        JsonObject json = JsonParser.parseString(
                "{\"line\":{\"lengthPixels\":26,\"widthPixels\":2,\"colorRgb\":16777215}}").getAsJsonObject();
        Profile read = SettingsRegistry.read(json, new Profile(), new Profile(), new RepairLog("test"));

        assertEquals(LineSettings.MAX_CUE_FLASH_INTENSITY, read.line().cueFlashIntensity());
        assertEquals(1.0f, read.line().cueFlashScale());
    }
}
