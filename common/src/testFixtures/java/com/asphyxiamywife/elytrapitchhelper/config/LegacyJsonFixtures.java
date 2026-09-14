package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.JsonObject;

public final class LegacyJsonFixtures {
    private LegacyJsonFixtures() {
    }

    public static JsonObject flatProfileJson(Profile profile) {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("name", profile.name());
        json.addProperty("showOnlyWithFirework", profile.visibility().showOnlyWithFirework());
        json.addProperty("showInThirdPerson", profile.visibility().showInThirdPerson());
        json.addProperty("targetUpMinecraft", profile.pitch().targetUpMinecraft());
        json.addProperty("targetDownMinecraft", profile.pitch().targetDownMinecraft());
        json.addProperty("toleranceDegrees", profile.pitch().toleranceDegrees());
        json.addProperty("maxOffsetPixels", profile.pitch().maxOffsetPixels());
        json.addProperty("offsetPerDegree", profile.pitch().offsetPerDegree());
        json.addProperty("lineLengthPixels", profile.line().lengthPixels());
        json.addProperty("lineWidthPixels", profile.line().widthPixels());
        json.addProperty("lineColorRgb", profile.line().colorRgb());
        json.addProperty("linePrideEnabled", profile.line().prideEnabled());
        json.addProperty("linePrideFlag", profile.line().prideFlag());
        json.addProperty("amplitudeHelperEnabled", profile.amplitude().enabled());
        json.addProperty("amplitudeTriggerMode", profile.amplitude().triggerMode());
        json.addProperty("amplitudeDownBlocks", profile.amplitude().downBlocks());
        json.addProperty("amplitudeUpBlocks", profile.amplitude().upBlocks());
        json.addProperty("amplitudeToleranceBlocks", profile.amplitude().toleranceBlocks());
        json.addProperty("amplitudeDownVelocity", profile.amplitude().downVelocity());
        json.addProperty("amplitudeUpVelocity", profile.amplitude().upVelocity());
        json.addProperty("amplitudeRepeatDiveCue", profile.amplitude().repeatDiveCue());
        json.addProperty("amplitudeRepeatClimbCue", profile.amplitude().repeatClimbCue());
        json.addProperty("amplitudeCueColorRgb", profile.amplitude().cueColorRgb());
        json.addProperty("amplitudeCuePrideEnabled", profile.amplitude().cuePrideEnabled());
        json.addProperty("amplitudeCuePrideFlag", profile.amplitude().cuePrideFlag());
        return json;
    }
}
