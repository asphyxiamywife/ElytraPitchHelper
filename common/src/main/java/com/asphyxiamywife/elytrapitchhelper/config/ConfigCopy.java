package com.asphyxiamywife.elytrapitchhelper.config;

final class ConfigCopy {
    private ConfigCopy() {
    }

    static Config copyOf(Config source) {
        Config copy = new Config();
        copyFrom(copy, source);
        return copy;
    }

    static void copyFrom(Config target, Config source) {
        ConfigSanitizer.ensureValueObjects(source);
        ConfigSanitizer.ensureValueObjects(target);
        source.document().applyTo(target);
        target.copyRuntimeStateFrom(source);
        ConfigProfileManager.ensureProfiles(target);
    }
}
