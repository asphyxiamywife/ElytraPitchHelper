package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.ConfigTestFixtures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileDeletionMarkingTest {
    private static Config threeProfiles() {
        return ConfigTestFixtures.configWith(
                ConfigTestFixtures.minimalProfile("A", "a.json"),
                ConfigTestFixtures.minimalProfile("B", "b.json"),
                ConfigTestFixtures.minimalProfile("C", "c.json"));
    }

    @Test
    void beginningDeletionMarksTheProfileItWasAskedFor() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("b.json");

        assertTrue(host.deleteMode);
        assertEquals(List.of("b.json"), deletes.markedFiles());
        assertEquals(3, host.config.profileCount(), "marking removed a profile");
    }

    @Test
    void markingIsATogglePerRow() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("b.json");
        deletes.toggleMark("a.json");
        assertEquals(List.of("b.json", "a.json"), deletes.markedFiles());

        deletes.toggleMark("b.json");
        assertEquals(List.of("a.json"), deletes.markedFiles());
        assertTrue(deletes.hasMarks());

        deletes.toggleMark("a.json");
        assertFalse(deletes.hasMarks());
        assertEquals(3, host.config.profileCount(), "toggling marks removed a profile");
    }

    @Test
    void theLastProfileCannotBeMarked() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("a.json");
        deletes.toggleMark("b.json");
        assertFalse(deletes.canMark("c.json"));

        deletes.toggleMark("c.json");
        assertEquals(List.of("a.json", "b.json"), deletes.markedFiles());

        deletes.toggleMark("b.json");
        assertTrue(deletes.canMark("c.json"));
    }

    @Test
    void aMarkedProfileCanAlwaysBeUnmarked() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("a.json");
        deletes.toggleMark("b.json");
        assertTrue(deletes.canMark("a.json"));
        assertTrue(deletes.canMark("b.json"));
    }

    @Test
    void abandoningTheScreenDropsTheBatchAndLeavesTheProfilesAlone() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("b.json");
        deletes.toggleMark("c.json");
        deletes.restoreIfAbandoned();

        assertFalse(host.deleteMode);
        assertFalse(deletes.hasMarks());
        assertEquals(3, host.config.profileCount());
        assertEquals(List.of(), host.savedDeletes, "nothing should have been scheduled for deletion");
    }

    @Test
    void committingRemovesExactlyTheMarkedProfiles() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("a.json");
        deletes.toggleMark("c.json");
        deletes.commit();

        assertEquals(1, host.config.profileCount());
        assertEquals("b.json", host.config.profile(0).fileName());
        assertEquals(List.of("c.json", "a.json"), host.savedDeletes,
                "the save is told what was really removed, deepest index first");
        assertFalse(host.deleteMode);
        assertFalse(deletes.hasMarks());
    }

    @Test
    void committingNothingRemovesNothing() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion(null);
        deletes.commit();

        assertEquals(3, host.config.profileCount());
        assertEquals(List.of(), host.savedDeletes);
    }

    @Test
    void aSingleProfileNeverEntersTheMode() {
        RecordingHost host = new RecordingHost(ConfigTestFixtures.configWith(
                ConfigTestFixtures.minimalProfile("Only", "only.json")));
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("only.json");

        assertFalse(host.deleteMode);
    }

    @Test
    void theCommitLabelCountsTheBatch() {
        RecordingHost host = new RecordingHost(threeProfiles());
        ConfigProfileDeletionController deletes = new ConfigProfileDeletionController(host);

        deletes.beginDeletion("a.json");
        assertEquals(1, deletes.markCount());

        deletes.toggleMark("b.json");
        assertEquals(2, deletes.markCount());
    }

    private static final class RecordingHost implements ConfigProfileDeletionController.Host {
        private final Config config;
        private boolean deleteMode;
        private int scroll;
        private final List<String> savedDeletes = new ArrayList<>();

        RecordingHost(Config config) {
            this.config = config;
        }

        @Override
        public Config config() {
            return config;
        }

        @Override
        public Minecraft minecraftClient() {
            return null;
        }

        @Override
        public boolean deleteMode() {
            return deleteMode;
        }

        @Override
        public void setDeleteMode(boolean deleteMode) {
            this.deleteMode = deleteMode;
        }

        @Override
        public int profileScroll() {
            return scroll;
        }

        @Override
        public void setProfileScrollDirect(int profileScroll) {
            scroll = profileScroll;
        }

        @Override
        public int maxProfileScroll() {
            return 0;
        }

        @Override
        public void syncEditingProfileIndex() {
        }

        @Override
        public void clearHistory() {
        }

        @Override
        public void updateHistoryContextBaseline() {
        }

        @Override
        public void saveProfileDeletes(List<String> profileFiles) {
            savedDeletes.addAll(profileFiles);
        }

        @Override
        public void flushSaveAsync() {
        }

        @Override
        public boolean openChildScreen(Screen screen) {
            return false;
        }

        @Override
        public void rebuildWidgets() {
        }
    }
}
