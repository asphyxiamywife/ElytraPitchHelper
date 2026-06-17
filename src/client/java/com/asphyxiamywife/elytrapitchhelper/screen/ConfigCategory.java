package com.asphyxiamywife.elytrapitchhelper.screen;

enum ConfigCategory {
    GENERAL("screen.elytrapitchhelper.category.general"),
    PITCH("screen.elytrapitchhelper.category.pitch"),
    AMPLITUDE("screen.elytrapitchhelper.category.amplitude"),
    VISUALS("screen.elytrapitchhelper.category.visuals"),
    VOID("screen.elytrapitchhelper.category.void");

    final String translationKey;

    ConfigCategory(String translationKey) {
        this.translationKey = translationKey;
    }
}
