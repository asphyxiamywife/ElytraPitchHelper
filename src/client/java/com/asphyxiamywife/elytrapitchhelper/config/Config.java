package com.asphyxiamywife.elytrapitchhelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

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
    private static final String BACKUP_DIRECTORY_NAME = "backups";
    private static final String BUNDLED_PROFILE_DIRECTORY = "assets/elytrapitchhelper/profiles";
    private static final String DEFAULT_PROFILE_FILE_NAME = "default.json";
    private static final String LEGACY_PROFILE_BASIS = "Migrated legacy config";
    static final String JSON_SUFFIX = ".json";
    private static final int MAX_PROFILE_BACKUPS = 8;
    static final int CURRENT_VERSION = 1;
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Object DEFAULT_PROFILE_LOCK = new Object();
    private static volatile List<Profile> cachedDefaultProfiles;
    private static volatile List<BundledProfileResource> cachedBundledProfileResources;

    public static Path getConfigPath() {
        return getConfigDirectory().resolve(FILE_NAME);
    }

    private static Path getLegacyConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIRECTORY_NAME);
    }

    public static Path getProfileDirectory() {
        return getConfigDirectory().resolve(PROFILE_DIRECTORY_NAME);
    }

    static Path getProfileMetadataPath() {
        return getConfigDirectory().resolve(INTERNAL_DIRECTORY_NAME).resolve(PROFILE_METADATA_FILE_NAME);
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

        if (cfg == null && !currentConfigExists && !hasNewFormatProfileFiles()) {
            cfg = loadLegacyConfig();
            migratedLegacyConfig = cfg != null;
        }

        if (cfg == null) {
            cfg = new Config();
        }

        cfg.profileMetadata = ProfileMetadataStore.load();
        if (migratedLegacyConfig) {
            cfg.ensureProfiles();
            cfg.markMigratedProfileMetadata(getLegacyConfigPath());
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

    private static Config loadLegacyConfig() {
        Path legacyPath = getLegacyConfigPath();
        if (!Files.exists(legacyPath)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return null;
            }

            RepairLog repairs = new RepairLog("legacy config " + legacyPath);
            Profile defaults = defaultProfileTemplate();
            Profile migratedProfile = defaults.copy();
            migratedProfile.name = defaults.name == null || defaults.name.isBlank() ? "Default" : defaults.name;
            migratedProfile.fileName = DEFAULT_PROFILE_FILE_NAME;
            migratedProfile.showOnlyWithFirework = readOptionalBoolean(root, "showOnlyWithFirework",
                    migratedProfile.showOnlyWithFirework, repairs);
            migratedProfile.targetUpMinecraft = readLegacyFloat(root, "targetUpMinecraft",
                    migratedProfile.targetUpMinecraft, -90.0f, 0.0f, repairs);
            migratedProfile.targetDownMinecraft = readLegacyFloat(root, "targetDownMinecraft",
                    migratedProfile.targetDownMinecraft, 0.0f, 90.0f, repairs);
            migratedProfile.toleranceDegrees = readLegacyFloat(root, "toleranceDegrees",
                    migratedProfile.toleranceDegrees, 1.0f, 45.0f, repairs);
            migratedProfile.maxOffsetPixels = readLegacyInt(root, "maxOffsetPixels",
                    migratedProfile.maxOffsetPixels, 0, 200, repairs);
            migratedProfile.offsetPerDegree = readLegacyFloat(root, "offsetPerDegree",
                    migratedProfile.offsetPerDegree, 0.25f, 10.0f, repairs);
            migratedProfile.lineColorRgb = readLegacyColor(root, "lineColorRgb", migratedProfile.lineColorRgb,
                    repairs);
            migratedProfile.sanitize(repairs, defaults);

            Config cfg = new Config();
            cfg.enabled = readOptionalBoolean(root, "enabled", cfg.enabled, repairs);
            cfg.profiles = new ArrayList<>();
            cfg.profiles.add(migratedProfile);
            cfg.activeProfileIndex = 0;
            cfg.activeProfileFile = migratedProfile.fileName;
            cfg.profileSortMode = PROFILE_SORT_CREATED;
            repairs.log();
            LOGGER.info("Migrated legacy Elytra Pitch Helper config from {} to {}", legacyPath, getConfigPath());
            return cfg;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Failed to migrate legacy config {}, using defaults", legacyPath, e);
            return null;
        }
    }

    private static boolean hasNewFormatProfileFiles() {
        Path profileDirectory = getProfileDirectory();
        if (!Files.isDirectory(profileDirectory)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(profileDirectory)) {
            return paths.anyMatch(Config::isJsonFile);
        } catch (IOException ignored) {
            return false;
        }
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
            writeJsonAtomic(path, this);
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
        profiles = defaultProfiles();
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
        active.copyFrom(defaultProfileTemplate());
        active.name = name;
        active.fileName = fileName;
        profileMetadata(active.fileName).markModified(System.currentTimeMillis(), "Default");
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
        return profileMetadata(profiles.get(clampIndex(index)).fileName).lastModifiedAtMillis;
    }

    public String profileBasedOn(int index) {
        ensureProfiles();
        return profileMetadata(profiles.get(clampIndex(index)).fileName).basedOn();
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
        return profilePath(profiles.get(activeProfileIndex));
    }

    public Path getProfilePath(int index) {
        ensureProfiles();
        return profilePath(profiles.get(clampIndex(index)));
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
        Profile profile = defaultProfileTemplate();
        profile.name = name;
        profile.fileName = uniqueFileName(name, usedFileNames);
        profileMetadata(profile.fileName).markCreated(System.currentTimeMillis(), "Default");
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
        duplicate.fileName = uniqueFileName(duplicate.name, usedFileNames());
        profileMetadata(duplicate.fileName).markCreated(System.currentTimeMillis(), original.name);
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
        profile.copyFrom(defaultProfileTemplate());
        profile.name = name;
        profile.fileName = fileName;
        profileMetadata(profile.fileName).markModified(System.currentTimeMillis(), "Default");
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
        String normalized = normalizeProfileFileName(fileName);
        if (normalized == null) {
            return;
        }

        try {
            Files.deleteIfExists(getProfileDirectory().resolve(normalized));
        } catch (IOException ignored) {
        }
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
        profiles = copyProfiles(other.profiles);
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
        ProfileLoadResult result = readProfileDirectory();
        List<Profile> loaded = result.profiles();

        if (loaded.isEmpty()) {
            if (createMissingProfiles && !result.foundProfileFiles()) {
                copyBundledProfilesToConfig();
                loaded = readProfileDirectory().profiles();
            } else if (result.foundProfileFiles()) {
                skipNextProfileSave = true;
            }
            if (loaded.isEmpty()) {
                loaded = defaultProfiles();
            }
        }

        profiles = loaded;
        profilesNormalized = false;
        ensureProfiles();
    }

    private ProfileLoadResult readProfileDirectory() {
        List<Profile> loaded = new ArrayList<>();
        boolean foundProfileFiles = false;
        Path profileDirectory = getProfileDirectory();
        if (Files.isDirectory(profileDirectory)) {
            try (Stream<Path> paths = Files.list(profileDirectory)) {
                List<Path> profilePaths = paths.filter(Config::isJsonFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
                for (Path path : profilePaths) {
                    foundProfileFiles = true;
                    Profile profile = readProfile(path);
                    if (profile != null) {
                        loaded.add(profile);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return new ProfileLoadResult(loaded, foundProfileFiles);
    }

    private Profile readProfile(Path path) {
        try {
            Profile defaults = latestValidProfileBackup(path);
            if (defaults == null) {
                defaults = defaultProfileTemplate();
            }
            Profile profile = readProfileFile(path, path.getFileName().toString(), "profile " + path,
                    defaults);
            syncProfileMetadataFromFile(profile.fileName, path);
            saveProfileBackup(path, profile);
            return profile;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Failed to load profile {}, trying newest backup", path, e);
            Profile profile = restoreProfileFromBackup(path);
            if (profile != null) {
                syncProfileMetadataFromFile(profile.fileName, path);
            }
            return profile;
        }
    }

    private static Profile readProfileFile(Path path, String fileName, String source) throws IOException {
        return readProfileFile(path, fileName, source, defaultProfileTemplate());
    }

    private static Profile readProfileFile(Path path, String fileName, String source, Profile defaults)
            throws IOException {
        if (defaults == null) {
            defaults = defaultProfileTemplate();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            RepairLog repairs = new RepairLog(source);
            Profile profile = parseProfile(root, fileName, defaults, repairs);
            profile.sanitize(repairs, defaults);
            repairs.log();
            return profile;
        }
    }

    private void saveProfiles() {
        Path profileDirectory = getProfileDirectory();
        try {
            Files.createDirectories(profileDirectory);
            boolean savedProfile = false;
            for (Profile profile : profiles) {
                RepairLog repairs = new RepairLog("profile " + profilePath(profile));
                profile.sanitize(repairs, defaultProfileTemplate());
                repairs.log();
                Path path = profilePath(profile);
                ProfileMetadata metadata = profileMetadata(profile.fileName);
                boolean profileFileExists = Files.exists(path);
                ProfileFileDiff profileFileDiff = profileFileDiff(path, profile);
                if (profileFileDiff.needsWrite()) {
                    if (profileChangedExternallySinceLoad(path, metadata)) {
                        LOGGER.warn("Skipped saving stale profile {} because it changed on disk", path);
                        continue;
                    }
                    if (profileFileDiff.settingsChanged()) {
                        metadata.markModified(System.currentTimeMillis());
                    }
                    savedProfile = true;
                    writeJsonAtomic(path, profile);
                    if (profileFileExists) {
                        saveProfileBackup(path, profile);
                    }
                    syncWrittenProfileTimestamp(metadata, path);
                }
            }
            if (savedProfile && profileSortMode == PROFILE_SORT_MODIFIED) {
                profilesNormalized = false;
                ensureProfiles();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save profiles into {}", profileDirectory, e);
        }
    }

    private void clampValues() {
        clampValues(null);
    }

    private void clampValues(RepairLog repairs) {
        ensureProfiles();
        Profile defaults = defaultProfileTemplate();
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
        if (profiles == null || profiles.isEmpty()) {
            profiles = defaultProfiles();
        }

        String selectedFile = activeProfileFile;
        if ((selectedFile == null || selectedFile.isBlank()) && activeProfileIndex >= 0
                && activeProfileIndex < profiles.size() && profiles.get(activeProfileIndex) != null) {
            selectedFile = profiles.get(activeProfileIndex).fileName;
        }

        Set<String> usedFileNames = new HashSet<>();
        Profile defaults = defaultProfileTemplate();
        for (int i = 0; i < profiles.size(); i++) {
            Profile profile = profiles.get(i);
            if (profile == null) {
                profile = defaults.copy();
                profile.name = "Profile " + (i + 1);
                profiles.set(i, profile);
            }
            if (profile.name == null || profile.name.isBlank()) {
                profile.name = "Profile " + (i + 1);
            }

            String fileName = normalizeProfileFileName(profile.fileName);
            if (fileName == null || usedFileNames.contains(fileName.toLowerCase(Locale.ROOT))) {
                fileName = uniqueFileName(profile.name, usedFileNames);
            } else {
                usedFileNames.add(fileName.toLowerCase(Locale.ROOT));
            }
            profile.fileName = fileName;
            profile.sanitize(null, defaults);
            profileMetadata(fileName).ensure(defaultProfileBasedOn(fileName));
        }

        sortProfiles(selectedFile);
    }

    private void sortProfiles(String selectedFile) {
        ensureProfilesWithoutIndexClamp();
        profiles.sort(profileComparator());

        int selectedIndex = indexOfProfileFileWithoutEnsure(selectedFile);
        if (selectedIndex >= 0) {
            activeProfileIndex = selectedIndex;
        } else {
            activeProfileIndex = Math.max(0, Math.min(activeProfileIndex, profiles.size() - 1));
        }
        activeProfileFile = profiles.get(activeProfileIndex).fileName;
        profilesNormalized = true;
    }

    private Comparator<Profile> profileComparator() {
        if (profileSortMode == PROFILE_SORT_NAME) {
            return Comparator.comparing((Profile profile) -> profile.name.toLowerCase(Locale.ROOT))
                    .thenComparing(profile -> profile.fileName.toLowerCase(Locale.ROOT));
        }
        if (profileSortMode == PROFILE_SORT_MODIFIED) {
            return Comparator.comparingLong((Profile profile) -> profileMetadata(profile.fileName).lastModifiedAtMillis)
                    .reversed()
                    .thenComparing(profile -> profile.name.toLowerCase(Locale.ROOT))
                    .thenComparing(profile -> profile.fileName.toLowerCase(Locale.ROOT));
        }
        return Comparator.comparingLong((Profile profile) -> profileMetadata(profile.fileName).createdAtMillis)
                .thenComparing(profile -> profile.name.toLowerCase(Locale.ROOT))
                .thenComparing(profile -> profile.fileName.toLowerCase(Locale.ROOT));
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
        if (fileName == null || fileName.isBlank()) {
            return -1;
        }
        for (int i = 0; i < profiles.size(); i++) {
            if (fileName.equalsIgnoreCase(profiles.get(i).fileName)) {
                return i;
            }
        }
        return -1;
    }

    private int clampIndex(int index) {
        ensureProfilesWithoutIndexClamp();
        return Math.max(0, Math.min(index, profiles.size() - 1));
    }

    private void ensureProfilesWithoutIndexClamp() {
        if (profiles == null || profiles.isEmpty()) {
            profiles = defaultProfiles();
            profilesNormalized = false;
        }
    }

    private Path profilePath(Profile profile) {
        return getProfileDirectory().resolve(profile.fileName);
    }

    private ProfileMetadata profileMetadata(String fileName) {
        if (profileMetadata == null) {
            profileMetadata = new ProfileMetadataStore();
        }
        return profileMetadata.profile(fileName);
    }

    private void saveProfileMetadata() {
        if (profileMetadata == null) {
            profileMetadata = new ProfileMetadataStore();
        }
        Set<String> profileFiles = new HashSet<>();
        for (Profile profile : profiles) {
            if (profile.fileName != null) {
                profileFiles.add(profile.fileName.toLowerCase(Locale.ROOT));
            }
        }
        profileMetadata.prune(profileFiles);
        Path path = getProfileMetadataPath();
        try {
            writeJsonAtomic(path, profileMetadata);
        } catch (IOException e) {
            LOGGER.warn("Failed to save internal profile metadata {}", path, e);
        }
    }

    private void markMigratedProfileMetadata(Path legacyPath) {
        long timestamp = System.currentTimeMillis();
        try {
            timestamp = Files.getLastModifiedTime(legacyPath).toMillis();
        } catch (IOException ignored) {
        }

        for (Profile profile : profiles) {
            profileMetadata(profile.fileName).markCreated(timestamp, LEGACY_PROFILE_BASIS);
        }
    }

    static String defaultProfileBasedOn(String fileName) {
        String normalized = normalizeProfileFileName(fileName);
        return DEFAULT_PROFILE_FILE_NAME.equalsIgnoreCase(normalized) ? "Bundled default" : "Custom";
    }

    private static Path profileBackupDirectory(String fileName) {
        String normalized = normalizeProfileFileName(fileName);
        String name = normalized == null ? "profile" : normalized.substring(0, normalized.length() - JSON_SUFFIX.length());
        return getConfigDirectory().resolve(BACKUP_DIRECTORY_NAME).resolve(PROFILE_DIRECTORY_NAME).resolve(slug(name));
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

    private static List<Profile> copyProfiles(List<Profile> source) {
        if (source == null) {
            return null;
        }
        List<Profile> copy = new ArrayList<>();
        for (Profile profile : source) {
            copy.add(profile == null ? null : profile.copy());
        }
        return copy;
    }

    private static List<Profile> defaultProfiles() {
        return copyProfiles(cachedDefaultProfiles());
    }

    private static Profile defaultProfileTemplate() {
        return cachedDefaultProfiles().get(0).copy();
    }

    private static List<Profile> cachedDefaultProfiles() {
        List<Profile> profiles = cachedDefaultProfiles;
        if (profiles == null) {
            synchronized (DEFAULT_PROFILE_LOCK) {
                profiles = cachedDefaultProfiles;
                if (profiles == null) {
                    profiles = loadDefaultProfiles();
                    cachedDefaultProfiles = profiles;
                }
            }
        }
        return profiles;
    }

    private static List<Profile> loadDefaultProfiles() {
        List<Profile> profiles = readBundledProfiles();
        if (!profiles.isEmpty()) {
            return List.copyOf(profiles);
        }
        return List.copyOf(fallbackDefaultProfiles());
    }

    private static List<Profile> fallbackDefaultProfiles() {
        List<Profile> fallback = new ArrayList<>();
        Profile profile = new Profile();
        profile.name = "Default";
        profile.fileName = DEFAULT_PROFILE_FILE_NAME;
        fallback.add(profile);
        return fallback;
    }

    private static void copyBundledProfilesToConfig() {
        List<BundledProfileResource> resources = bundledProfileResources();
        if (resources.isEmpty()) {
            return;
        }

        Path profileDirectory = getProfileDirectory();
        try {
            Files.createDirectories(profileDirectory);
            for (BundledProfileResource resource : resources) {
                String fileName = normalizeProfileFileName(resource.fileName());
                if (fileName != null) {
                    writeBytesAtomic(profileDirectory.resolve(fileName), resource.contents());
                    LOGGER.info("Copied bundled Elytra Pitch Helper profile {}", fileName);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to copy bundled profiles into {}", profileDirectory, e);
        }
    }

    private static List<Profile> readBundledProfiles() {
        List<Profile> profiles = new ArrayList<>();
        Profile bundledDefaults = null;
        for (BundledProfileResource resource : bundledProfileResources()) {
            try {
                JsonObject root = GSON.fromJson(new String(resource.contents(), StandardCharsets.UTF_8), JsonObject.class);
                RepairLog repairs = new RepairLog("bundled profile " + resource.fileName());
                Profile defaults = bundledDefaults == null ? new Profile() : bundledDefaults;
                Profile profile = parseProfile(root, resource.fileName(), defaults, repairs);
                profile.sanitize(repairs, defaults);
                repairs.log();
                profiles.add(profile);
                if (bundledDefaults == null || DEFAULT_PROFILE_FILE_NAME.equals(profile.fileName)) {
                    bundledDefaults = profile.copy();
                }
            } catch (JsonParseException | IllegalStateException e) {
                LOGGER.warn("Failed to parse bundled profile {}, skipping it", resource.fileName(), e);
            }
        }
        return profiles;
    }

    private static Profile latestValidProfileBackup(Path profilePath) {
        Path backupDirectory = profileBackupDirectory(profilePath.getFileName().toString());
        if (!Files.isDirectory(backupDirectory)) {
            return null;
        }

        try (Stream<Path> paths = Files.list(backupDirectory)) {
            List<Path> backups = paths.filter(Config::isJsonFile)
                    .sorted(Comparator.<Path, String>comparing(path -> path.getFileName().toString()).reversed())
                    .toList();
            for (Path backup : backups) {
                try {
                    return readProfileFile(backup, profilePath.getFileName().toString(),
                            "profile backup " + backup);
                } catch (IOException | JsonParseException | IllegalStateException e) {
                    LOGGER.warn("Skipped invalid profile backup {}", backup, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read profile backups from {}", backupDirectory, e);
        }
        return null;
    }

    private static Profile restoreProfileFromBackup(Path profilePath) {
        Path backupDirectory = profileBackupDirectory(profilePath.getFileName().toString());
        if (!Files.isDirectory(backupDirectory)) {
            return null;
        }

        try (Stream<Path> paths = Files.list(backupDirectory)) {
            List<Path> backups = paths.filter(Config::isJsonFile)
                    .sorted(Comparator.<Path, String>comparing(path -> path.getFileName().toString()).reversed())
                    .toList();
            for (Path backup : backups) {
                try {
                    Profile profile = readProfileFile(backup, profilePath.getFileName().toString(),
                            "profile backup " + backup);
                    writeJsonAtomic(profilePath, profile);
                    validateAndBackupWrittenProfile(profilePath, profile.fileName);
                    LOGGER.warn("Restored profile {} from backup {}", profilePath, backup);
                    return profile;
                } catch (IOException | JsonParseException | IllegalStateException e) {
                    LOGGER.warn("Skipped invalid profile backup {}", backup, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read profile backups from {}", backupDirectory, e);
        }
        return null;
    }

    private static void validateAndBackupWrittenProfile(Path profilePath, String fileName) {
        try {
            Profile validated = readProfileFile(profilePath, fileName, "written profile " + profilePath);
            saveProfileBackup(profilePath, validated);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Wrote profile {}, but could not validate it for backup", profilePath, e);
        }
    }

    private static void saveProfileBackup(Path profilePath, Profile profile) {
        try {
            profile.sanitize(null, defaultProfileTemplate());
            byte[] contents = GSON.toJson(profile).getBytes(StandardCharsets.UTF_8);
            String fileName = profile.fileName == null ? profilePath.getFileName().toString() : profile.fileName;
            if (latestProfileBackupMatches(fileName, contents)) {
                return;
            }

            Path backupDirectory = profileBackupDirectory(fileName);
            Files.createDirectories(backupDirectory);
            String timestamp = BACKUP_TIMESTAMP_FORMAT.format(Instant.now());
            Path backupPath = backupDirectory.resolve(timestamp + JSON_SUFFIX);
            int suffix = 2;
            while (Files.exists(backupPath)) {
                backupPath = backupDirectory.resolve(timestamp + "-" + suffix + JSON_SUFFIX);
                suffix++;
            }
            writeBytesAtomic(backupPath, contents);
            rotateProfileBackups(backupDirectory);
        } catch (IOException e) {
            LOGGER.warn("Failed to save profile backup for {}", profilePath, e);
        }
    }

    private static boolean latestProfileBackupMatches(String fileName, byte[] contents) throws IOException {
        Path backupDirectory = profileBackupDirectory(fileName);
        if (!Files.isDirectory(backupDirectory)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(backupDirectory)) {
            Path latest = paths.filter(Config::isJsonFile)
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
            return latest != null && Arrays.equals(Files.readAllBytes(latest), contents);
        }
    }

    private static void rotateProfileBackups(Path backupDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(backupDirectory)) {
            List<Path> backups = paths.filter(Config::isJsonFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            int removeCount = backups.size() - MAX_PROFILE_BACKUPS;
            for (int i = 0; i < removeCount; i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }

    private static ProfileFileDiff profileFileDiff(Path path, Profile profile) {
        if (!Files.exists(path)) {
            return new ProfileFileDiff(true, false);
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject existing = GSON.fromJson(reader, JsonObject.class);
            JsonObject next = GSON.toJsonTree(profile).getAsJsonObject();
            boolean needsWrite = !next.equals(existing);
            Profile defaults = defaultProfileTemplate();
            Profile existingProfile = parseProfile(existing, profile.fileName, defaults, null);
            existingProfile.sanitize(null, defaults);
            Profile nextProfile = profile.copy();
            nextProfile.sanitize(null, defaults);
            boolean settingsChanged = !GSON.toJsonTree(nextProfile).equals(GSON.toJsonTree(existingProfile));
            return new ProfileFileDiff(needsWrite, settingsChanged);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return new ProfileFileDiff(true, true);
        }
    }

    private static boolean profileChangedExternallySinceLoad(Path path, ProfileMetadata metadata) {
        if (!Files.exists(path) || metadata.loadedFileModifiedAtMillis <= 0L) {
            return false;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis() > metadata.loadedFileModifiedAtMillis;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void syncWrittenProfileTimestamp(ProfileMetadata metadata, Path path) {
        try {
            metadata.syncLoadedFileModifiedAtMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (IOException ignored) {
        }
    }

    private void syncProfileMetadataFromFile(String fileName, Path path) {
        try {
            profileMetadata(fileName).markLoaded(Files.getLastModifiedTime(path).toMillis(),
                    defaultProfileBasedOn(fileName));
        } catch (IOException ignored) {
            profileMetadata(fileName).ensure(defaultProfileBasedOn(fileName));
        }
    }

    private static List<BundledProfileResource> bundledProfileResources() {
        List<BundledProfileResource> cachedResources = cachedBundledProfileResources;
        if (cachedResources != null) {
            return cachedResources;
        }

        synchronized (DEFAULT_PROFILE_LOCK) {
            cachedResources = cachedBundledProfileResources;
            if (cachedResources != null) {
                return cachedResources;
            }
            cachedResources = loadBundledProfileResources();
            cachedBundledProfileResources = cachedResources;
            return cachedResources;
        }
    }

    private static List<BundledProfileResource> loadBundledProfileResources() {
        List<BundledProfileResource> resources = new ArrayList<>();
        Set<String> seenFileNames = new HashSet<>();
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
            for (Path root : container.getRootPaths()) {
                Path profileDirectory = root.resolve(BUNDLED_PROFILE_DIRECTORY);
                if (!Files.isDirectory(profileDirectory)) {
                    continue;
                }
                try (Stream<Path> paths = Files.list(profileDirectory)) {
                    for (Path path : paths.filter(Config::isJsonFile).toList()) {
                        String fileName = normalizeProfileFileName(path.getFileName().toString());
                        if (fileName != null && seenFileNames.add(fileName.toLowerCase(Locale.ROOT))) {
                            try {
                                resources.add(new BundledProfileResource(fileName, Files.readAllBytes(path)));
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        });
        resources.sort(Comparator.<BundledProfileResource>comparingInt(
                resource -> defaultProfileOrder(resource.fileName()))
                .thenComparing(resource -> resource.fileName().toLowerCase(Locale.ROOT)));
        return List.copyOf(resources);
    }

    private static boolean isJsonFile(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX);
    }

    private static int defaultProfileOrder(Path path) {
        return defaultProfileOrder(path.getFileName().toString());
    }

    private static int defaultProfileOrder(String fileName) {
        return DEFAULT_PROFILE_FILE_NAME.equals(fileName.toLowerCase(Locale.ROOT)) ? 0 : 100;
    }

    static String normalizeProfileFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String normalized = fileName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.isBlank()) {
            return null;
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(JSON_SUFFIX)) {
            normalized += JSON_SUFFIX;
        }
        return normalized;
    }

    private static String uniqueFileName(String name, Set<String> usedFileNames) {
        String base = slug(name);
        String candidate = base + JSON_SUFFIX;
        int suffix = 2;
        while (usedFileNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "-" + suffix + JSON_SUFFIX;
            suffix++;
        }
        usedFileNames.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    private static String slug(String name) {
        String slug = name == null ? "" : name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "profile" : slug;
    }

    private static String nameFromFileName(String fileName) {
        String name = normalizeProfileFileName(fileName);
        if (name == null) {
            return "Profile";
        }
        name = name.substring(0, name.length() - JSON_SUFFIX.length()).replace('-', ' ').replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!word.isBlank()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    builder.append(word.substring(1));
                }
            }
        }
        return builder.length() == 0 ? "Profile" : builder.toString();
    }

    private static Profile parseProfile(JsonObject json, String fileName, Profile defaults, RepairLog repairs) {
        Profile profile = defaults.copy();
        profile.fileName = normalizeProfileFileName(fileName);
        String fallbackName = profile.name == null || profile.name.isBlank() ? nameFromFileName(profile.fileName)
                : profile.name;

        if (json == null) {
            repair(repairs, "profile", "null json", "default profile");
            if (profile.name == null || profile.name.isBlank()) {
                profile.name = fallbackName;
            }
            return profile;
        }

        profile.version = readInt(json, "version", defaults.version, repairs);
        profile.name = readString(json, "name", fallbackName, repairs);
        profile.showOnlyWithFirework = readBoolean(json, "showOnlyWithFirework", defaults.showOnlyWithFirework,
                repairs);
        profile.showInThirdPerson = readBoolean(json, "showInThirdPerson", defaults.showInThirdPerson, repairs);
        profile.targetUpMinecraft = readFloat(json, "targetUpMinecraft", defaults.targetUpMinecraft, repairs);
        profile.targetDownMinecraft = readFloat(json, "targetDownMinecraft", defaults.targetDownMinecraft, repairs);
        profile.toleranceDegrees = readFloat(json, "toleranceDegrees", defaults.toleranceDegrees, repairs);
        profile.maxOffsetPixels = readInt(json, "maxOffsetPixels", defaults.maxOffsetPixels, repairs);
        profile.offsetPerDegree = readFloat(json, "offsetPerDegree", defaults.offsetPerDegree, repairs);
        profile.lineLengthPixels = readInt(json, "lineLengthPixels", defaults.lineLengthPixels, repairs);
        profile.lineWidthPixels = readInt(json, "lineWidthPixels", defaults.lineWidthPixels, repairs);
        profile.lineColorRgb = readInt(json, "lineColorRgb", defaults.lineColorRgb, repairs);
        profile.linePrideEnabled = readBoolean(json, "linePrideEnabled", defaults.linePrideEnabled, repairs);
        profile.linePrideFlag = readString(json, "linePrideFlag", defaults.linePrideFlag, repairs);
        profile.amplitudeHelperEnabled = readBoolean(json, "amplitudeHelperEnabled", defaults.amplitudeHelperEnabled,
                repairs);
        profile.amplitudeTriggerMode = readInt(json, "amplitudeTriggerMode", defaults.amplitudeTriggerMode, repairs);
        profile.amplitudeDownBlocks = readInt(json, "amplitudeDownBlocks", defaults.amplitudeDownBlocks, repairs);
        profile.amplitudeUpBlocks = readInt(json, "amplitudeUpBlocks", defaults.amplitudeUpBlocks, repairs);
        profile.amplitudeToleranceBlocks = readInt(json, "amplitudeToleranceBlocks", defaults.amplitudeToleranceBlocks,
                repairs);
        profile.amplitudeDownVelocity = readFloat(json, "amplitudeDownVelocity", defaults.amplitudeDownVelocity,
                repairs);
        profile.amplitudeUpVelocity = readFloat(json, "amplitudeUpVelocity", defaults.amplitudeUpVelocity, repairs);
        profile.amplitudeCueColorRgb = readInt(json, "amplitudeCueColorRgb", defaults.amplitudeCueColorRgb, repairs);
        profile.amplitudeCuePrideEnabled = readBoolean(json, "amplitudeCuePrideEnabled",
                defaults.amplitudeCuePrideEnabled, repairs);
        profile.amplitudeCuePrideFlag = readString(json, "amplitudeCuePrideFlag", defaults.amplitudeCuePrideFlag,
                repairs);
        return profile;
    }

    private static String readString(JsonObject json, String field, String fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            repair(repairs, field, element, fallback);
            return fallback;
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            repair(repairs, field, value, fallback);
            return fallback;
        }
        return value;
    }

    private static boolean readBoolean(JsonObject json, String field, boolean fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            repair(repairs, field, element, fallback);
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static boolean readOptionalBoolean(JsonObject json, String field, boolean fallback, RepairLog repairs) {
        return json.has(field) ? readBoolean(json, field, fallback, repairs) : fallback;
    }

    private static int readInt(JsonObject json, String field, int fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            repair(repairs, field, element, fallback);
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            repair(repairs, field, element, fallback);
            return fallback;
        }
    }

    private static int readOptionalInt(JsonObject json, String field, int fallback, RepairLog repairs) {
        return json.has(field) ? readInt(json, field, fallback, repairs) : fallback;
    }

    private static float readFloat(JsonObject json, String field, float fallback, RepairLog repairs) {
        JsonElement element = json.get(field);
        if (element == null || element.isJsonNull()) {
            repair(repairs, field, "missing", fallback);
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            repair(repairs, field, element, fallback);
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (NumberFormatException e) {
            repair(repairs, field, element, fallback);
            return fallback;
        }
    }

    private static float readOptionalFloat(JsonObject json, String field, float fallback, RepairLog repairs) {
        return json.has(field) ? readFloat(json, field, fallback, repairs) : fallback;
    }

    private static int readLegacyInt(JsonObject json, String field, int fallback, int min, int max,
            RepairLog repairs) {
        int value = readOptionalInt(json, field, fallback, repairs);
        int repaired = Math.max(min, Math.min(max, value));
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    private static float readLegacyFloat(JsonObject json, String field, float fallback, float min, float max,
            RepairLog repairs) {
        float value = readOptionalFloat(json, field, fallback, repairs);
        float repaired = Float.isFinite(value) ? clamp(value, min, max) : fallback;
        if (Float.compare(repaired, value) != 0) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    private static int readLegacyColor(JsonObject json, String field, int fallback, RepairLog repairs) {
        int value = readOptionalInt(json, field, fallback, repairs);
        int repaired = value & 0x00FFFFFF;
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static int repairInt(RepairLog repairs, String field, int value, int fallback, int min, int max) {
        int repaired = value < min || value > max ? Math.max(min, Math.min(max, fallback)) : value;
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static float repairFloat(RepairLog repairs, String field, float value, float fallback, float min, float max) {
        float repaired = value;
        if (!Float.isFinite(repaired) || repaired < min || repaired > max) {
            repaired = clamp(fallback, min, max);
        }
        if (Float.compare(repaired, value) != 0) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static int repairColor(RepairLog repairs, String field, int value, int fallback) {
        int repaired = value < 0 || value > 0x00FFFFFF ? fallback & 0x00FFFFFF : value;
        if (repaired != value) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static String repairPrideFlag(RepairLog repairs, String field, String value, String fallback) {
        String repaired = PrideFlag.isValidId(value) ? PrideFlag.sanitizeId(value) : PrideFlag.sanitizeId(fallback);
        if (value == null || !repaired.equals(value)) {
            repair(repairs, field, value, repaired);
        }
        return repaired;
    }

    static void repair(RepairLog repairs, String field, Object oldValue, Object newValue) {
        if (repairs != null) {
            repairs.add(field + ": " + oldValue + " -> " + newValue);
        }
    }

    private static void writeJsonAtomic(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
        moveAtomic(tmp, path);
    }

    private static void writeBytesAtomic(Path path, byte[] value) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, value);
        moveAtomic(tmp, path);
    }

    private static void moveAtomic(Path tmp, Path path) throws IOException {
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class RepairLog {
        private final String source;
        private final List<String> repairs = new ArrayList<>();

        private RepairLog(String source) {
            this.source = source;
        }

        private void add(String repair) {
            repairs.add(repair);
        }

        private void log() {
            if (!repairs.isEmpty()) {
                LOGGER.warn("Repaired {}: {}", source, String.join("; ", repairs));
            }
        }
    }

    private record BundledProfileResource(String fileName, byte[] contents) {
    }

    private record ProfileLoadResult(List<Profile> profiles, boolean foundProfileFiles) {
    }

    private record ProfileFileDiff(boolean needsWrite, boolean settingsChanged) {
    }

}
