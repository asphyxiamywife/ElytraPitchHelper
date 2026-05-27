package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairColor;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairPrideFlag;

public final class LineSettings {
    public int lengthPixels = 26;
    public int widthPixels = 2;
    public int colorRgb = 0xFFFFFF;
    public boolean prideEnabled = false;
    public String prideFlag = PrideFlag.defaultId();

    public LineSettings copy() {
        LineSettings settings = new LineSettings();
        settings.copyFrom(this);
        return settings;
    }

    public void copyFrom(LineSettings other) {
        if (other == null) {
            return;
        }
        lengthPixels = other.lengthPixels;
        widthPixels = other.widthPixels;
        colorRgb = other.colorRgb;
        prideEnabled = other.prideEnabled;
        prideFlag = other.prideFlag;
    }

    void sanitize(Config.RepairLog repairs, LineSettings defaults) {
        if (defaults == null) {
            defaults = new LineSettings();
        }
        lengthPixels = repairInt(repairs, "line.lengthPixels", lengthPixels, defaults.lengthPixels, 2, 200);
        widthPixels = repairInt(repairs, "line.widthPixels", widthPixels, defaults.widthPixels, 1, 20);
        colorRgb = repairColor(repairs, "line.colorRgb", colorRgb, defaults.colorRgb);
        prideFlag = repairPrideFlag(repairs, "line.prideFlag", prideFlag, defaults.prideFlag);
    }
}
