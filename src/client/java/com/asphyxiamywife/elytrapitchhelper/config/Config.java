package com.asphyxiamywife.elytrapitchhelper.config;

import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.clamp;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repair;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairColor;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairFloat;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairInt;
import static com.asphyxiamywife.elytrapitchhelper.config.ConfigRepair.repairPrideFlag;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Config {
    public static final int AMPLITUDE_TRIGGER_HEIGHT = 0;
    public static final int AMPLITUDE_TRIGGER_VELOCITY = 1;
    public static final int AMPLITUDE_TRIGGER_EITHER = 2;
    public static final int PROFILE_SORT_CREATED = 0;
    public static final int PROFILE_SORT_NAME = 1;
    public static final int PROFILE_SORT_MODIFIED = 2;

    public int version = CURRENT_VERSION;
    public boolean enabled = true;
    public transient float targetUpMinecraft;
    public transient float targetDownMinecraft;
    public transient float toleranceDegrees;
    public transient int maxOffsetPixels;
    public transient float offsetPerDegree;
    public transient int lineLengthPixels;
    public transient int lineWidthPixels;
    public transient int lineColorRgb;
    public transient boolean linePrideEnabled;
    public transient String linePrideFlag = PrideFlag.defaultId();
    public transient boolean showOnlyWithFirework = false;
    public transient boolean showInThirdPerson = false;
    public int activeProfileIndex = 0;
    public String activeProfileFile;
    public int profileSortMode = PROFILE_SORT_CREATED;
    public transient List<Profile> profiles;
    public transient boolean amplitudeHelperEnabled;
    public transient int amplitudeTriggerMode;
    public transient int amplitudeDownBlocks;
    public transient int amplitudeUpBlocks;
    public transient int amplitudeToleranceBlocks;
    public transient float amplitudeDownVelocity;
    public transient float amplitudeUpVelocity;
    public transient int amplitudeCueColorRgb;
    public transient boolean amplitudeCuePrideEnabled;
    public transient String amplitudeCuePrideFlag = PrideFlag.defaultId();
    private transient boolean skipNextProfileSave;
    private transient boolean profilesNormalized;
    private transient ProfileMetadataStore profileMetadata = new ProfileMetadataStore();

    private static final String MOD_ID = "elytrapitchhelper";
    private static final String FILE_NAME = "elytra-pitch-helper.json";
    private static final String CONFIG_DIRECTORY_NAME = "elytra-pitch-helper";
    private static final String PROFILE_DIRECTORY_NAME = "profiles";
    private static final String INTERNAL_DIRECTORY_NAME = ".internal";
    private static final String PROFILE_METADATA_FILE_NAME = "profile-metadata.json";
    static final String JSON_SUFFIX = ProfileFileNames.JSON_SUFFIX;
    static final int CURRENT_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    static final Gson GSON = ConfigFiles.GSON;
    private static Path configRootOverride;

    public static Path getConfigPath() {
        return getConfigDirectory().resolve(FILE_NAME);
    }

    private static Path getLegacyConfigPath() {
        return loaderConfigDirectory().resolve(FILE_NAME);
    }

    public static Path getConfigDirectory() {
        return loaderConfigDirectory().resolve(CONFIG_DIRECTORY_NAME);
    }

    public static Path getProfileDirectory() {
        return getConfigDirectory().resolve(PROFILE_DIRECTORY_NAME);
    }

    static Path getProfileMetadataPath() {
        return getConfigDirectory().resolve(INTERNAL_DIRECTORY_NAME).resolve(PROFILE_METADATA_FILE_NAME);
    }

    static void setConfigRootOverrideForTests(Path configRoot) {
        configRootOverride = configRoot;
    }

    private static Path loaderConfigDirectory() {
        return configRootOverride == null ? FabricLoader.getInstance().getConfigDir() : configRootOverride;
    }

    public static Config load() {
        return load(true);
    }

    public static Config reloadFromDisk() {
        return load(false);
    }

    private static Config load(boolean saveAfterLoad) {
        Path path = getConfigPath();
        Config cfg = null;
        boolean currentConfigExists = Files.exists(path);
        boolean migratedLegacyConfig = false;
        if (currentConfigExists) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(reader, Config.class);
            } catch (IOException | JsonParseException | IllegalStateException e) {
                LOGGER.warn("Failed to load {}, using defaults and available profile json files", path, e);
            }
        }

        if (cfg == null && !currentConfigExists && !ProfilePersistence.hasProfileFiles()) {
            cfg = LegacyConfigMigration.load(getLegacyConfigPath(), getConfigPath());
            migratedLegacyConfig = cfg != null;
        }

        if (cfg == null) {
            cfg = new Config();
        }

        cfg.profileMetadata = ProfileMetadataStore.load();
        if (migratedLegacyConfig) {
            cfg.ensureProfiles();
            ProfilePersistence.markMigratedProfileMetadata(cfg.profileMetadata, cfg.profiles, getLegacyConfigPath());
        } else {
            cfg.loadProfiles(saveAfterLoad);
        }
        cfg.applyActiveProfile();
        RepairLog repairs = new RepairLog("main config " + path);
        cfg.clampValues(repairs);
        repairs.log();
        if (saveAfterLoad) {
            cfg.save();
        }
        return cfg;
    }

    public void save() {
        ensureProfiles();
        clampValues();
        updateActiveProfileFromCurrent();
        boolean saveProfiles = !skipNextProfileSave;
        skipNextProfileSave = false;
        if (saveProfiles) {
            saveProfiles();
        }

        Path path = getConfigPath();
        try {
            ConfigFiles.writeJsonAtomic(path, this);
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", path, e);
        }
        saveProfileMetadata();
    }

    public Config copy() {
        Config cfg = new Config();
        cfg.copyFrom(this);
        return cfg;
    }

    public void syncSavedProfileMetadataFrom(Config savedConfig) {
        if (savedConfig == null) {
            return;
        }

        ensureProfiles();
        savedConfig.ensureProfiles();
        profileMetadata = savedConfig.profileMetadata == null ? new ProfileMetadataStore()
                : savedConfig.profileMetadata.copy();

        if (profileSortMode == PROFILE_SORT_MODIFIED) {
            profilesNormalized = false;
            sortProfiles(activeProfileFile);
        }
    }

    public void resetToDefaults() {
        copyFrom(new Config());
        profiles = ProfileDefaults.profiles();
        profilesNormalized = false;
        activeProfileIndex = 0;
        activeProfileFile = profiles.get(activeProfileIndex).fileName;
        applyActiveProfile();
    }

    public void resetActiveProfileToDefaults() {
        ensureProfiles();
        Profile active = profiles.get(activeProfileIndex);
        String name = active.name;
        String fileName = active.fileName;
        active.copyFrom(ProfileDefaults.template());
        active.name = name;
        active.fileName = fileName;
        profileMetadata.profile(active.fileName).markModified(System.currentTimeMillis(), "Default");
        active.applyTo(this);
    }

    public int profileCount() {
        ensureProfiles();
        return profiles.size();
    }

    public String profileName(int index) {
        ensureProfiles();
        return profiles.get(clampIndex(index)).name;
    }

    public Profile profile(int index) {
        ensureProfiles();
        return profiles.get(clampIndex(index));
    }

    public long profileLastModifiedAtMillis(int index) {
        ensureProfiles();
        return ProfilePersistence.lastModifiedAtMillis(profileMetadata, profiles.get(clampIndex(index)).fileName);
    }

    public String profileBasedOn(int index) {
        ensureProfiles();
        return ProfilePersistence.basedOn(profileMetadata, profiles.get(clampIndex(index)).fileName);
    }

    public void setProfileName(int index, String name) {
        ensureProfiles();
        int clampedIndex = clampIndex(index);
        profiles.get(clampedIndex).name = name == null ? "" : name;
        profilesNormalized = false;
    }

    public boolean isActiveProfile(int index) {
        ensureProfiles();
        return clampIndex(index) == activeProfileIndex;
    }

    public String activeProfileName() {
        ensureProfiles();
        return profiles.get(activeProfileIndex).name;
    }

    public Path getActiveProfilePath() {
        ensureProfiles();
        return ProfilePersistence.profilePath(profiles.get(activeProfileIndex));
    }

    public Path getProfilePath(int index) {
        ensureProfiles();
        return ProfilePersistence.profilePath(profiles.get(clampIndex(index)));
    }

    public int profileIndexByFileName(String fileName) {
        ensureProfiles();
        return indexOfProfileFileWithoutEnsure(fileName);
    }

    public int profileSortMode() {
        return profileSortMode;
    }

    public void cycleProfileSortMode() {
        ensureProfiles();
        String activeFile = profiles.get(activeProfileIndex).fileName;
        profileSortMode = nextProfileSortMode(profileSortMode);
        sortProfiles(activeFile);
    }

    public void selectProfile(int index) {
        ensureProfiles();
        updateActiveProfileFromCurrent();
        activeProfileIndex = clampIndex(index);
        activeProfileFile = profiles.get(activeProfileIndex).fileName;
        applyActiveProfile();
    }

    public void applyProfileIfActive(int index) {
        ensureProfiles();
        if (clampIndex(index) == activeProfileIndex) {
            applyActiveProfile();
        }
    }

    public Profile createProfile() {
        ensureProfiles();
        updateActiveProfileFromCurrent();

        Set<String> usedFileNames = usedFileNames();
        String name = uniqueProfileName("New Profile");
        Profile profile = ProfileDefaults.template();
        profile.name = name;
        profile.fileName = ProfileFileNames.unique(name, usedFileNames);
        profileMetadata.profile(profile.fileName).markCreated(System.currentTimeMillis(), "Default");
        profiles.add(profile);
        profilesNormalized = false;
        return profile;
    }

    public Profile duplicateProfile(int index) {
        ensureProfiles();
        updateActiveProfileFromCurrent();

        int clampedIndex = clampIndex(index);
        Profile original = profiles.get(clampedIndex);
        Profile duplicate = original.copy();
        duplicate.name = uniqueCopyName(original.name);
        duplicate.fileName = ProfileFileNames.unique(duplicate.name, usedFileNames());
        profileMetadata.profile(duplicate.fileName).markCreated(System.currentTimeMillis(), original.name);
        profiles.add(clampedIndex + 1, duplicate);
        if (activeProfileIndex > clampedIndex) {
            activeProfileIndex++;
        }
        activeProfileFile = profiles.get(activeProfileIndex).fileName;
        profilesNormalized = false;
        return duplicate;
    }

    public void resetProfileToDefaults(int index) {
        ensureProfiles();
        int clampedIndex = clampIndex(index);
        Profile profile = profiles.get(clampedIndex);
        String name = profile.name;
        String fileName = profile.fileName;
        profile.copyFrom(ProfileDefaults.template());
        profile.name = name;
        profile.fileName = fileName;
        profileMetadata.profile(profile.fileName).markModified(System.currentTimeMillis(), "Default");
        if (clampedIndex == activeProfileIndex) {
            applyActiveProfile();
        }
    }

    public boolean deleteActiveProfile() {
        return deleteProfile(activeProfileIndex);
    }

    public boolean deleteProfile(int index) {
        StagedProfileDelete deleted = stageDeleteProfile(index);
        if (deleted == null) {
            return false;
        }

        deleteProfileFile(deleted.fileName());
        return true;
    }

    public StagedProfileDelete stageDeleteProfile(int index) {
        ensureProfiles();
        if (profiles.size() <= 1) {
            return null;
        }

        int clampedIndex = clampIndex(index);
        Profile removed = profiles.remove(clampedIndex);
        boolean wasActive = activeProfileIndex == clampedIndex;

        if (wasActive) {
            activeProfileIndex = Math.max(0, Math.min(clampedIndex, profiles.size() - 1));
            applyActiveProfile();
        } else if (activeProfileIndex > clampedIndex) {
            activeProfileIndex--;
        }
        activeProfileFile = profiles.get(activeProfileIndex).fileName;
        profilesNormalized = false;
        return new StagedProfileDelete(removed, clampedIndex, wasActive);
    }

    public void restoreStagedProfileDelete(StagedProfileDelete deleted) {
        if (deleted == null || deleted.profile() == null) {
            return;
        }

        ensureProfiles();
        String selectedFile = deleted.wasActive() ? deleted.fileName() : activeProfileFile;
        int insertIndex = Math.max(0, Math.min(deleted.index(), profiles.size()));
        profiles.add(insertIndex, deleted.profile().copy());
        profilesNormalized = false;
        sortProfiles(selectedFile);
        if (deleted.wasActive()) {
            applyActiveProfile();
        }
    }

    public void deleteProfileFile(String fileName) {
        ProfilePersistence.deleteProfileFile(fileName);
    }

    public static int nextAmplitudeTriggerMode(int mode) {
        return mode >= AMPLITUDE_TRIGGER_EITHER ? AMPLITUDE_TRIGGER_HEIGHT : mode + 1;
    }

    public static int nextProfileSortMode(int mode) {
        return mode >= PROFILE_SORT_MODIFIED ? PROFILE_SORT_CREATED : mode + 1;
    }

    private void copyFrom(Config other) {
        version = other.version;
        enabled = other.enabled;
        targetUpMinecraft = other.targetUpMinecraft;
        targetDownMinecraft = other.targetDownMinecraft;
        toleranceDegrees = other.toleranceDegrees;
        maxOffsetPixels = other.maxOffsetPixels;
        offsetPerDegree = other.offsetPerDegree;
        lineLengthPixels = other.lineLengthPixels;
        lineWidthPixels = other.lineWidthPixels;
        lineColorRgb = other.lineColorRgb;
        linePrideEnabled = other.linePrideEnabled;
        linePrideFlag = other.linePrideFlag;
        showOnlyWithFirework = other.showOnlyWithFirework;
        showInThirdPerson = other.showInThirdPerson;
        activeProfileIndex = other.activeProfileIndex;
        activeProfileFile = other.activeProfileFile;
        profileSortMode = other.profileSortMode;
        profiles = ProfileDefaults.copyProfiles(other.profiles);
        profileMetadata = other.profileMetadata == null ? new ProfileMetadataStore() : other.profileMetadata.copy();
        profilesNormalized = false;
        amplitudeHelperEnabled = other.amplitudeHelperEnabled;
        amplitudeTriggerMode = other.amplitudeTriggerMode;
        amplitudeDownBlocks = other.amplitudeDownBlocks;
        amplitudeUpBlocks = other.amplitudeUpBlocks;
        amplitudeToleranceBlocks = other.amplitudeToleranceBlocks;
        amplitudeDownVelocity = other.amplitudeDownVelocity;
        amplitudeUpVelocity = other.amplitudeUpVelocity;
        amplitudeCueColorRgb = other.amplitudeCueColorRgb;
        amplitudeCuePrideEnabled = other.amplitudeCuePrideEnabled;
        amplitudeCuePrideFlag = other.amplitudeCuePrideFlag;
        ensureProfiles();
    }

    private void loadProfiles(boolean createMissingProfiles) {
        ProfilePersistence.LoadResult result = ProfilePersistence.loadProfiles(createMissingProfiles,
                profileMetadata);
        profiles = result.profiles();
        skipNextProfileSave = result.skipNextProfileSave();
        profilesNormalized = false;
        ensureProfiles();
    }

    private void saveProfiles() {
        if (ProfilePersistence.saveProfiles(profiles, profileMetadata) && profileSortMode == PROFILE_SORT_MODIFIED) {
            profilesNormalized = false;
            ensureProfiles();
        }
    }

    private void clampValues() {
        clampValues(null);
    }

    private void clampValues(RepairLog repairs) {
        ensureProfiles();
        Profile defaults = ProfileDefaults.template();
        version = repairInt(repairs, "version", version, CURRENT_VERSION, CURRENT_VERSION, CURRENT_VERSION);
        profileSortMode = repairInt(repairs, "profileSortMode", profileSortMode, PROFILE_SORT_CREATED,
                PROFILE_SORT_CREATED, PROFILE_SORT_MODIFIED);
        targetUpMinecraft = repairFloat(repairs, "targetUpMinecraft", targetUpMinecraft,
                defaults.targetUpMinecraft, -90.0f, 0.0f);
        targetDownMinecraft = repairFloat(repairs, "targetDownMinecraft", targetDownMinecraft,
                defaults.targetDownMinecraft, 0.0f, 90.0f);
        toleranceDegrees = repairFloat(repairs, "toleranceDegrees", toleranceDegrees,
                defaults.toleranceDegrees, 1.0f, 45.0f);
        maxOffsetPixels = repairInt(repairs, "maxOffsetPixels", maxOffsetPixels,
                defaults.maxOffsetPixels, 0, 200);
        offsetPerDegree = repairFloat(repairs, "offsetPerDegree", offsetPerDegree,
                defaults.offsetPerDegree, 0.25f, 10.0f);
        lineLengthPixels = repairInt(repairs, "lineLengthPixels", lineLengthPixels,
                defaults.lineLengthPixels, 2, 200);
        lineWidthPixels = repairInt(repairs, "lineWidthPixels", lineWidthPixels,
                defaults.lineWidthPixels, 1, 20);
        lineColorRgb = repairColor(repairs, "lineColorRgb", lineColorRgb, defaults.lineColorRgb);
        linePrideFlag = repairPrideFlag(repairs, "linePrideFlag", linePrideFlag, defaults.linePrideFlag);
        amplitudeTriggerMode = repairInt(repairs, "amplitudeTriggerMode", amplitudeTriggerMode,
                defaults.amplitudeTriggerMode, AMPLITUDE_TRIGGER_HEIGHT, AMPLITUDE_TRIGGER_EITHER);
        amplitudeDownBlocks = repairInt(repairs, "amplitudeDownBlocks", amplitudeDownBlocks,
                defaults.amplitudeDownBlocks, 1, 1024);
        amplitudeUpBlocks = repairInt(repairs, "amplitudeUpBlocks", amplitudeUpBlocks,
                defaults.amplitudeUpBlocks, 1, 1024);
        amplitudeToleranceBlocks = repairInt(repairs, "amplitudeToleranceBlocks", amplitudeToleranceBlocks,
                defaults.amplitudeToleranceBlocks, 0, 128);
        amplitudeDownVelocity = repairFloat(repairs, "amplitudeDownVelocity", amplitudeDownVelocity,
                defaults.amplitudeDownVelocity, 0.1f, 10.0f);
        amplitudeUpVelocity = repairFloat(repairs, "amplitudeUpVelocity", amplitudeUpVelocity,
                defaults.amplitudeUpVelocity, 0.0f, 10.0f);
        if (amplitudeUpVelocity >= amplitudeDownVelocity) {
            float oldValue = amplitudeUpVelocity;
            amplitudeUpVelocity = clamp(defaults.amplitudeUpVelocity, 0.0f, Math.max(0.0f,
                    amplitudeDownVelocity - 0.1f));
            repair(repairs, "amplitudeUpVelocity", oldValue, amplitudeUpVelocity);
        }
        amplitudeCueColorRgb = repairColor(repairs, "amplitudeCueColorRgb", amplitudeCueColorRgb,
                defaults.amplitudeCueColorRgb);
        amplitudeCuePrideFlag = repairPrideFlag(repairs, "amplitudeCuePrideFlag", amplitudeCuePrideFlag,
                defaults.amplitudeCuePrideFlag);
        activeProfileIndex = clampIndex(activeProfileIndex);
        activeProfileFile = profiles.get(activeProfileIndex).fileName;
        for (Profile profile : profiles) {
            profile.sanitize(null, defaults);
        }
    }

    private void ensureProfiles() {
        if (profilesNormalized && profiles != null && !profiles.isEmpty()) {
            return;
        }
        ProfileOrdering.Selection selection = ProfileOrdering.normalize(profiles, activeProfileIndex,
                activeProfileFile, profileSortMode, profileMetadata);
        applySelection(selection);
        profilesNormalized = true;
    }

    private void sortProfiles(String selectedFile) {
        ensureProfilesWithoutIndexClamp();
        applySelection(ProfileOrdering.sort(profiles, activeProfileIndex, selectedFile, profileSortMode,
                profileMetadata));
        profilesNormalized = true;
    }

    private void applySelection(ProfileOrdering.Selection selection) {
        profiles = selection.profiles();
        activeProfileIndex = selection.activeIndex();
        activeProfileFile = selection.activeFile();
    }

    private void applyActiveProfile() {
        ensureProfiles();
        profiles.get(activeProfileIndex).applyTo(this);
    }

    private void updateActiveProfileFromCurrent() {
        ensureProfiles();
        profiles.get(activeProfileIndex).copyFrom(this);
        activeProfileFile = profiles.get(activeProfileIndex).fileName;
    }

    private int indexOfProfileFile(String fileName) {
        ensureProfiles();
        return indexOfProfileFileWithoutEnsure(fileName);
    }

    private int indexOfProfileFileWithoutEnsure(String fileName) {
        return ProfileOrdering.indexOfFile(profiles, fileName);
    }

    private int clampIndex(int index) {
        ensureProfilesWithoutIndexClamp();
        return ProfileOrdering.clampIndex(profiles, index);
    }

    private void ensureProfilesWithoutIndexClamp() {
        if (profiles == null || profiles.isEmpty()) {
            profiles = ProfileDefaults.profiles();
            profilesNormalized = false;
        }
    }

    private void saveProfileMetadata() {
        if (profileMetadata == null) {
            profileMetadata = new ProfileMetadataStore();
        }
        ProfilePersistence.saveProfileMetadata(profileMetadata, profiles);
    }

    static String defaultProfileBasedOn(String fileName) {
        return ProfileDefaults.basedOn(fileName);
    }

    private Set<String> usedFileNames() {
        Set<String> used = new HashSet<>();
        for (Profile profile : profiles) {
            if (profile.fileName != null) {
                used.add(profile.fileName.toLowerCase(Locale.ROOT));
            }
        }
        return used;
    }

    private String uniqueProfileName(String baseName) {
        Set<String> usedNames = new HashSet<>();
        for (Profile profile : profiles) {
            usedNames.add(profile.name.toLowerCase(Locale.ROOT));
        }

        String name = baseName;
        int suffix = 2;
        while (usedNames.contains(name.toLowerCase(Locale.ROOT))) {
            name = baseName + " " + suffix;
            suffix++;
        }
        return name;
    }

    private String uniqueCopyName(String originalName) {
        Set<String> usedNames = new HashSet<>();
        for (Profile profile : profiles) {
            usedNames.add(profile.name.toLowerCase(Locale.ROOT));
        }

        String base = (originalName == null || originalName.isBlank() ? "Profile" : originalName) + " copy";
        String name = base;
        int suffix = 2;
        while (usedNames.contains(name.toLowerCase(Locale.ROOT))) {
            name = base + " " + suffix;
            suffix++;
        }
        return name;
    }

    static final class RepairLog {
        private final String source;
        private final List<String> repairs = new ArrayList<>();

        RepairLog(String source) {
            this.source = source;
        }

        void add(String repair) {
            repairs.add(repair);
        }

        void log() {
            if (!repairs.isEmpty()) {
                LOGGER.warn("Repaired {}: {}", source, String.join("; ", repairs));
            }
        }
    }

}
