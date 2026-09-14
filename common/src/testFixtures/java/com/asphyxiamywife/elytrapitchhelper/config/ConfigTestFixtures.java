package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class ConfigTestFixtures {
    private ConfigTestFixtures() {
    }

    public static ConfigFileSystem fs() {
        return com.asphyxiamywife.elytrapitchhelper.client.ClientConfigStore.store().fileSystem();
    }

    public static Profile minimalProfile(String name, String fileName) {
        return new Profile().withName(name).withFileName(fileName);
    }

    public static Profile tunedProfile(String name, String fileName) {
        Profile profile = minimalProfile(name, fileName);
        AmplitudeSettings amplitude = profile.amplitude()
                .withTriggerMode(Config.AMPLITUDE_TRIGGER_HEIGHT)
                .withUpBlocks(45)
                .withCuePrideFlag("trans");
        return profile.withAmplitude(amplitude);
    }

    public static Config configWith(Profile... profiles) {
        if (profiles.length == 0) {
            throw new IllegalArgumentException("At least one profile is required");
        }
        Config config = new Config();
        config.profiles = new ArrayList<>(List.of(profiles));
        config.activeProfileIndex = 0;
        config.activeProfileFile = profiles[0].fileName();
        ConfigProfileManager.ensureProfiles(config);
        return config;
    }

    public static Config readOnly(Config config) {
        config.enterReadOnlyMode("test fixture");
        return config;
    }

    public static void updateActiveProfile(Config config, UnaryOperator<Profile> update) {
        Profile current = config.activeProfile();
        config.replaceProfile(config.activeProfileIndex, update.apply(current));
    }
}
