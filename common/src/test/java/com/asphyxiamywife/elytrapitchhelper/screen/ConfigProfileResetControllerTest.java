package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import net.minecraft.client.gui.screens.ConfirmScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigProfileResetControllerTest {
    @Test
    void confirmationResetsTheConfigThatIsLiveAfterReturningToTheScreen() {
        Profile tuned = ConfigTestFixtures.tunedProfile("Flight", "flight.json");
        Config stale = ConfigTestFixtures.configWith(tuned);
        Config reloaded = stale.copy();
        Host host = new Host(stale, reloaded);
        ConfigProfileResetController controller = new ConfigProfileResetController(host);

        controller.completeReset("flight.json", true);

        Config expectedReset = reloaded.copy();
        expectedReset.resetProfileToDefaults(expectedReset.profileIndexByFileName("flight.json"));
        assertTrue(reloaded.hasSameProfileState(expectedReset, "flight.json"));
        assertTrue(stale.hasSameProfileState(ConfigTestFixtures.configWith(tuned), "flight.json"));
        assertEquals(1, host.saves);
        assertEquals(1, host.commits);

        controller.undo();
        assertTrue(reloaded.hasSameProfileState(ConfigTestFixtures.configWith(tuned), "flight.json"));
    }

    private static final class Host implements ConfigProfileResetController.Host {
        private Config config;
        private final Config configAfterReturn;
        private int saves;
        private int commits;

        private Host(Config config, Config configAfterReturn) {
            this.config = config;
            this.configAfterReturn = configAfterReturn;
        }

        @Override
        public Config config() {
            return config;
        }

        @Override
        public String editingProfileFile() {
            return "flight.json";
        }

        @Override
        public void showConfirmation(ConfirmScreen screen) {
        }

        @Override
        public void returnToConfigScreen() {
            config = configAfterReturn;
        }

        @Override
        public void beginHistoryAction(String actionKey) {
        }

        @Override
        public void commitHistoryAction(boolean changed, boolean coalesce) {
            commits++;
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public void rebuildWidgets() {
        }
    }
}
