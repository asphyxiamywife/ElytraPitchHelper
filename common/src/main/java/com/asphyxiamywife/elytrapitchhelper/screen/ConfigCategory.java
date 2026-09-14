package com.asphyxiamywife.elytrapitchhelper.screen;

public enum ConfigCategory {
    GENERAL("screen.elytrapitchhelper.category.general"),
    PITCH("screen.elytrapitchhelper.category.pitch"),
    LINE("screen.elytrapitchhelper.category.line"),
    AMPLITUDE("screen.elytrapitchhelper.category.amplitude"),
    VOID("screen.elytrapitchhelper.category.void"),
    INTERFACE("screen.elytrapitchhelper.category.interface");

    public final String translationKey;

    ConfigCategory(String translationKey) {
        this.translationKey = translationKey;
    }
}
