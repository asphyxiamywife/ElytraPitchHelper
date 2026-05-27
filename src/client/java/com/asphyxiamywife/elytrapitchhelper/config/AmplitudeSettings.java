package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.Config.AMPLITUDE_TRIGGER_EITHER;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.AMPLITUDE_TRIGGER_HEIGHT;
import static com.asphyxiamywife.elytrapitchhelper.config.Config.AMPLITUDE_TRIGGER_VELOCITY;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.clamp;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repair;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairColor;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairFloat;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairPrideFlag;

public final class AmplitudeSettings {
    public boolean enabled = true;
    public int triggerMode = AMPLITUDE_TRIGGER_VELOCITY;
    public int downBlocks = 50;
    public int upBlocks = 50;
    public int toleranceBlocks = 4;
    public float downVelocity = 2.0f;
    public float upVelocity = 0.2f;
    public int cueColorRgb = 0xFF0000;
    public boolean cuePrideEnabled = false;
    public String cuePrideFlag = PrideFlag.defaultId();

    public AmplitudeSettings copy() {
        AmplitudeSettings settings = new AmplitudeSettings();
        settings.copyFrom(this);
        return settings;
    }

    public void copyFrom(AmplitudeSettings other) {
        if (other == null) {
            return;
        }
        enabled = other.enabled;
        triggerMode = other.triggerMode;
        downBlocks = other.downBlocks;
        upBlocks = other.upBlocks;
        toleranceBlocks = other.toleranceBlocks;
        downVelocity = other.downVelocity;
        upVelocity = other.upVelocity;
        cueColorRgb = other.cueColorRgb;
        cuePrideEnabled = other.cuePrideEnabled;
        cuePrideFlag = other.cuePrideFlag;
    }

    void sanitize(Config.RepairLog repairs, AmplitudeSettings defaults) {
        if (defaults == null) {
            defaults = new AmplitudeSettings();
        }
        triggerMode = repairInt(repairs, "amplitude.triggerMode", triggerMode, defaults.triggerMode,
                AMPLITUDE_TRIGGER_HEIGHT, AMPLITUDE_TRIGGER_EITHER);
        downBlocks = repairInt(repairs, "amplitude.downBlocks", downBlocks, defaults.downBlocks, 1, 1024);
        upBlocks = repairInt(repairs, "amplitude.upBlocks", upBlocks, defaults.upBlocks, 1, 1024);
        toleranceBlocks = repairInt(repairs, "amplitude.toleranceBlocks", toleranceBlocks,
                defaults.toleranceBlocks, 0, 128);
        downVelocity = repairFloat(repairs, "amplitude.downVelocity", downVelocity,
                defaults.downVelocity, 0.1f, 10.0f);
        upVelocity = repairFloat(repairs, "amplitude.upVelocity", upVelocity,
                defaults.upVelocity, 0.0f, 10.0f);
        if (upVelocity >= downVelocity) {
            float oldValue = upVelocity;
            upVelocity = clamp(defaults.upVelocity, 0.0f, Math.max(0.0f, downVelocity - 0.1f));
            repair(repairs, "amplitude.upVelocity", oldValue, upVelocity);
        }
        cueColorRgb = repairColor(repairs, "amplitude.cueColorRgb", cueColorRgb, defaults.cueColorRgb);
        cuePrideFlag = repairPrideFlag(repairs, "amplitude.cuePrideFlag", cuePrideFlag,
                defaults.cuePrideFlag);
    }
}
