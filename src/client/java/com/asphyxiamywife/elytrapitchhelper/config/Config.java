package com.asphyxiamywife.elytrapitchhelper.config;

import java.nio.file.Path;
import java.util.List;

public final class Config {
    public static final int AMPLITUDE_TRIGGER_HEIGHT = 0;
    public static final int AMPLITUDE_TRIGGER_VELOCITY = 1;
    public static final int AMPLITUDE_TRIGGER_EITHER = 2;
    public static final int PROFILE_SORT_CREATED = 0;
    public static final int PROFILE_SORT_NAME = 1;
    public static final int PROFILE_SORT_MODIFIED = 2;

    public int version = CURRENT_VERSION;
    public boolean enabled = true;
    public transient VisibilitySettings visibility = new VisibilitySettings();
    public transient PitchSettings pitch = new PitchSettings();
    public transient LineSettings line = new LineSettings();
    public transient AmplitudeSettings amplitude = new AmplitudeSettings();
    public transient VoidWarningSettings voidWarning = new VoidWarningSettings();
    public int activeProfileIndex = 0;
    public String activeProfileFile;
    public int profileSortMode = PROFILE_SORT_CREATED;
    public transient List<Profile> profiles;
    transient boolean skipNextProfileSave;
    transient boolean profilesNormalized;
    transient ProfileMetadataStore profileMetadata = new ProfileMetadataStore();

    static final String JSON_SUFFIX = ProfileFileNames.JSON_SUFFIX;
    static final int CURRENT_VERSION = 2;

    public static Path getConfigPath() {
        return ConfigPaths.configPath();
    }

    public static Path getConfigDirectory() {
        return ConfigPaths.configDirectory();
    }

    public static Path getProfileDirectory() {
        return ConfigPaths.profileDirectory();
    }

    static Path getProfileMetadataPath() {
        return ConfigPaths.profileMetadataPath();
    }

    static void setConfigRootOverrideForTests(Path configRoot) {
        ConfigPaths.setConfigRootOverrideForTests(configRoot);
    }

    public static Config load() {
        return ConfigStorage.load(true);
    }

    public static Config reloadFromDisk() {
        return ConfigStorage.load(false);
    }

    public void save() {
        ConfigStorage.save(this);
    }

    public Config copy() {
        return ConfigCopy.copyOf(this);
    }

    public void syncSavedProfileMetadataFrom(Config savedConfig) {
        ConfigProfileManager.syncSavedProfileMetadataFrom(this, savedConfig);
    }

    public void resetToDefaults() {
        ConfigProfileManager.resetToDefaults(this);
    }

    public void resetActiveProfileToDefaults() {
        ConfigProfileManager.resetActiveProfileToDefaults(this);
    }

    public int profileCount() {
        return ConfigProfileManager.profileCount(this);
    }

    public String profileName(int index) {
        return ConfigProfileManager.profileName(this, index);
    }

    public Profile profile(int index) {
        return ConfigProfileManager.profile(this, index);
    }

    public long profileLastModifiedAtMillis(int index) {
        return ConfigProfileManager.profileLastModifiedAtMillis(this, index);
    }

    public String profileBasedOn(int index) {
        return ConfigProfileManager.profileBasedOn(this, index);
    }

    public void setProfileName(int index, String name) {
        ConfigProfileManager.setProfileName(this, index, name);
    }

    public boolean isActiveProfile(int index) {
        return ConfigProfileManager.isActiveProfile(this, index);
    }

    public String activeProfileName() {
        return ConfigProfileManager.activeProfileName(this);
    }

    public Path getActiveProfilePath() {
        return ConfigProfileManager.activeProfilePath(this);
    }

    public Path getProfilePath(int index) {
        return ConfigProfileManager.profilePath(this, index);
    }

    public int profileIndexByFileName(String fileName) {
        return ConfigProfileManager.profileIndexByFileName(this, fileName);
    }

    public int profileSortMode() {
        return profileSortMode;
    }

    public void cycleProfileSortMode() {
        ConfigProfileManager.cycleProfileSortMode(this);
    }

    public void cycleProfileSortModeBackward() {
        ConfigProfileManager.cycleProfileSortModeBackward(this);
    }

    public void selectProfile(int index) {
        ConfigProfileManager.selectProfile(this, index);
    }

    public void applyProfileIfActive(int index) {
        ConfigProfileManager.applyProfileIfActive(this, index);
    }

    public Profile createProfile() {
        return ConfigProfileManager.createProfile(this);
    }

    public Profile duplicateProfile(int index) {
        return ConfigProfileManager.duplicateProfile(this, index);
    }

    public void resetProfileToDefaults(int index) {
        ConfigProfileManager.resetProfileToDefaults(this, index);
    }

    public boolean deleteActiveProfile() {
        return ConfigProfileManager.deleteActiveProfile(this);
    }

    public boolean deleteProfile(int index) {
        return ConfigProfileManager.deleteProfile(this, index);
    }

    public StagedProfileDelete stageDeleteProfile(int index) {
        return ConfigProfileManager.stageDeleteProfile(this, index);
    }

    public void restoreStagedProfileDelete(StagedProfileDelete deleted) {
        ConfigProfileManager.restoreStagedProfileDelete(this, deleted);
    }

    public void deleteProfileFile(String fileName) {
        ProfilePersistence.deleteProfileFile(fileName);
    }

    public static int nextAmplitudeTriggerMode(int mode) {
        return mode >= AMPLITUDE_TRIGGER_EITHER ? AMPLITUDE_TRIGGER_HEIGHT : mode + 1;
    }

    public static int previousAmplitudeTriggerMode(int mode) {
        return mode <= AMPLITUDE_TRIGGER_HEIGHT ? AMPLITUDE_TRIGGER_EITHER : mode - 1;
    }

    public static int nextProfileSortMode(int mode) {
        return mode >= PROFILE_SORT_MODIFIED ? PROFILE_SORT_CREATED : mode + 1;
    }

    public static int previousProfileSortMode(int mode) {
        return mode <= PROFILE_SORT_CREATED ? PROFILE_SORT_MODIFIED : mode - 1;
    }

    void ensureValueObjects() {
        ConfigSanitizer.ensureValueObjects(this);
    }

    static String defaultProfileBasedOn(String fileName) {
        return ProfileDefaults.basedOn(fileName);
    }
}
