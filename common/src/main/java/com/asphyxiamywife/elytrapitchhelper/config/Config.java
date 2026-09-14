package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public int activeProfileIndex = 0;
    public String activeProfileFile;
    public int profileSortMode = PROFILE_SORT_CREATED;
    public CommandPaletteUsageSettings commandPaletteUsage = new CommandPaletteUsageSettings();
    public SectionCollapseSettings sectionCollapse = new SectionCollapseSettings();
    public boolean usedSettingsSearch = false;
    public transient List<Profile> profiles;
    transient boolean skipNextProfileSave;
    transient boolean profilesNormalized;
    transient ProfileMetadataStore profileMetadata = new ProfileMetadataStore();
    transient StoreMetadata storeMetadata = StoreMetadata.EMPTY;
    transient LocalCommitBaselines.Token localBaselineToken;
    transient ConfigDocument editBaseline;
    transient LoadedFileState writtenMain;
    transient Map<String, LoadedFileState> writtenProfiles = new LinkedHashMap<>();
    transient AccessMode accessMode = Writable.INSTANCE;

    static final int CURRENT_VERSION = 2;

    public static Path getConfigPath(ConfigFileSystem fileSystem) {
        return ConfigPaths.configPath(fileSystem);
    }

    public static Path getConfigDirectory(ConfigFileSystem fileSystem) {
        return ConfigPaths.configDirectory(fileSystem);
    }

    public static Path getProfileDirectory(ConfigFileSystem fileSystem) {
        return ConfigPaths.profileDirectory(fileSystem);
    }

    public static Path getProfileMetadataPath(ConfigFileSystem fileSystem) {
        return ConfigPaths.profileMetadataPath(fileSystem);
    }

    public static Config load(ConfigFileSystem fileSystem) {
        return fromLoadResult(ConfigStorage.load(fileSystem, true));
    }

    public static Config reloadFromDisk(ConfigFileSystem fileSystem) {
        return fromLoadResult(ConfigStorage.load(fileSystem, false));
    }

    public static java.util.Optional<Config> tryReloadFromDisk(ConfigFileSystem fileSystem) {
        return ConfigStorage.tryReload(fileSystem).map(Config::fromLoadResult);
    }


    public void save(ConfigFileSystem fileSystem) {
        requireWritable();
        ConfigStorage.save(fileSystem, this);
    }

    public void saveOverwritingExternalChanges(
            ConfigFileSystem fileSystem, ConflictBaseline approvedConflictBaseline) {
        requireWritable();
        acceptConflictBaseline(fileSystem, approvedConflictBaseline);
        ConfigStorage.save(fileSystem, this);
    }

    public void saveDeletingProfileFiles(
            ConfigFileSystem fileSystem, List<String> deletedProfileFiles) {
        requireWritable();
        ConfigStorage.save(fileSystem, this, Set.of(), deletedProfileFiles);
    }

    public void saveDeletingProfileFiles(ConfigFileSystem fileSystem,
            List<String> deletedProfileFiles, ConflictBaseline approvedConflictBaseline) {
        requireWritable();
        acceptConflictBaseline(fileSystem, approvedConflictBaseline);
        ConfigStorage.save(fileSystem, this, Set.of(), deletedProfileFiles);
    }

    public Config copy() {
        return ConfigCopy.copyOf(this);
    }

    public boolean isReadOnly() {
        return accessMode instanceof ReadOnly;
    }

    public String readOnlyWarning() {
        return accessMode instanceof ReadOnly readOnly ? readOnly.warning() : "";
    }

    void enterReadOnlyMode(String warning) {
        accessMode = new ReadOnly(warning);
    }

    void inheritAccessModeFrom(Config current) {
        accessMode = current.accessMode;
    }

    private void requireWritable() {
        if (isReadOnly()) {
            throw new NewerConfigVersionException("Config is read-only because newer data was found: "
                    + readOnlyWarning());
        }
    }

    public void restoreFrom(Config snapshot) {
        ConfigCopy.copyFrom(this, snapshot);
    }

    public boolean prepareHistoryRestoreFrom(Config actionSource, Config current) {
        Config source = actionSource.copy();
        Config target = copy();
        Config latest = current.copy();
        Set<String> affected = new java.util.HashSet<>();
        for (Profile profile : source.profiles) affected.add(profile.fileName());
        for (Profile profile : target.profiles) affected.add(profile.fileName());
        for (String file : affected) {
            int sourceIndex = source.profileIndexByFileName(file);
            int targetIndex = target.profileIndexByFileName(file);
            Profile before = sourceIndex < 0 ? null : source.profile(sourceIndex);
            Profile after = targetIndex < 0 ? null : target.profile(targetIndex);
            if (java.util.Objects.equals(before, after)) continue;
            boolean exists = latest.profileIndexByFileName(file) >= 0;
            if (before == null && exists) return false;
            if (before != null && after != null && !exists) return false;
            if (before != null && exists && source.localBaselineToken != null
                    && source.localBaselineToken.owner.wasProfileDeleted(source.localBaselineToken, file)) {
                return false;
            }
        }
        ConfigDraftMerge.mergeHistory(source.document(), target.document(), latest.document()).applyTo(this);
        prepareSnapshotRestoreFrom(current);
        return true;
    }

    public void prepareSnapshotRestoreFrom(Config current) {
        if (current != null) {
            StoreMetadata rebased = storeMetadata.withMainConfig(current.mainConfigFileState());
            for (Profile profile : profiles) {
                if (current.profileIndexByFileName(profile.fileName()) >= 0) {
                    rebased = rebased.withProfileFrom(current.storeMetadata, profile.fileName());
                }
            }
            storeMetadata = rebased;
            localBaselineToken = current.localBaselineToken;
            editBaseline = current.document();
        }
    }

    public void preparePersistenceRebaseFrom(Config committed, List<String> deletedProfileFiles) {
        if (committed == null) {
            return;
        }
        prepareSnapshotRestoreFrom(committed);
        for (String deletedProfileFile : deletedProfileFiles) {
            storeMetadata = storeMetadata.withProfileFrom(committed.storeMetadata, deletedProfileFile);
        }
        skipNextProfileSave = committed.skipNextProfileSave;
    }

    public boolean hasSameState(Config other) {
        return other != null && document().equals(other.document());
    }

    public boolean hasSamePersistedSnapshot(Config other) {
        return hasSameState(other)
                && profileMetadata.equals(other.profileMetadata)
                && storeMetadata.equals(other.storeMetadata)
                && accessMode.equals(other.accessMode);
    }

    public boolean hasSameQueuedSaveRequest(Config other) {
        return hasSameState(other)
                && hasSameMainConfigFileMetadata(other)
                && storeMetadata.equals(other.storeMetadata)
                && profileMetadata.equals(other.profileMetadata);
    }

    public boolean hasSameProfileMetadata(Config other) {
        return other != null && profileMetadata.equals(other.profileMetadata);
    }

    public boolean hasSameMainConfigFileMetadata(Config other) {
        return other != null
                && accessMode.equals(other.accessMode)
                && mainConfigFileState().equals(other.mainConfigFileState());
    }

    public void syncSavedProfileMetadataFrom(Config savedConfig) {
        ConfigProfileManager.syncSavedProfileMetadataFrom(this, savedConfig);
        if (savedConfig != null) {
            storeMetadata = savedConfig.storeMetadata;
            localBaselineToken = savedConfig.localBaselineToken;
        }
    }

    public void acceptMainConfigStateFromDisk(Config diskConfig) {
        ConfigProfileManager.acceptMainConfigStateFromDisk(this, diskConfig);
    }

    public static ConflictBaseline captureConflictBaseline(
            ConfigFileSystem fileSystem, List<Path> paths) {
        Map<Path, LoadedFileState> states = new LinkedHashMap<>();
        try {
            for (Path path : paths) {
                Path normalized = path.toAbsolutePath().normalize();
                try {
                    BasicFileAttributes attributes = fileSystem.attributes(normalized);
                    states.put(normalized, new LoadedFileState(true, true,
                            attributes.lastModifiedTime().toMillis(),
                            ConfigFiles.fileFingerprint(fileSystem, normalized)));
                } catch (NoSuchFileException ignored) {
                    states.put(normalized, new LoadedFileState(true, false, 0L, null));
                }
            }
        } catch (IOException | SecurityException failure) {
            throw new ConfigSaveException("Failed to capture conflicting config files", failure);
        }
        return new ConflictBaseline(Map.copyOf(states));
    }

    public void acceptConflictBaseline(ConfigFileSystem fileSystem, ConflictBaseline baseline) {
        if (baseline == null) {
            return;
        }
        Path mainConfigPath = getConfigPath(fileSystem).toAbsolutePath().normalize();
        Path profileDirectory = getProfileDirectory(fileSystem).toAbsolutePath().normalize();
        Set<String> approvedProfiles = new java.util.HashSet<>();
        for (Map.Entry<Path, LoadedFileState> entry : baseline.states.entrySet()) {
            Path path = entry.getKey();
            LoadedFileState state = entry.getValue();
            if (path.equals(mainConfigPath)) {
                markMainConfigFileLoaded(state.exists(), state.modifiedAtMillis(), state.fingerprint());
            } else if (path.getParent() != null && path.getParent().equals(profileDirectory)) {
                String file = path.getFileName().toString();
                restoreProfileFileState(file, state);
                approvedProfiles.add(ProfileFileNames.comparisonKey(file));
            }
        }
        if (localBaselineToken != null && !approvedProfiles.isEmpty()) {
            localBaselineToken.owner.approved(this, approvedProfiles);
        }
    }

    public static final class ConflictBaseline {
        private final Map<Path, LoadedFileState> states;

        private ConflictBaseline(Map<Path, LoadedFileState> states) {
            this.states = states;
        }
    }

    void markMainConfigFileLoaded(boolean exists, long modifiedAtMillis, String fingerprint) {
        storeMetadata = storeMetadata.withMainConfig(new LoadedFileState(
                true,
                exists,
                exists ? Math.max(0L, modifiedAtMillis) : 0L,
                exists && validFingerprint(fingerprint) ? fingerprint : null));
    }

    LoadedFileState mainConfigFileState() {
        return storeMetadata.mainConfig();
    }

    void restoreMainConfigFileState(LoadedFileState state) {
        storeMetadata = storeMetadata.withMainConfig(state);
    }

    LoadedFileState profileFileState(String fileName) {
        return storeMetadata.profile(fileName);
    }

    void restoreProfileFileState(String fileName, LoadedFileState state) {
        storeMetadata = storeMetadata.withProfile(fileName, state);
    }

    public ConfigDocument document() {
        return ConfigDocument.from(this);
    }

    ConfigState state() {
        return new ConfigState(document(), storeMetadata, profileMetadata, skipNextProfileSave);
    }

    void restoreState(ConfigState state) {
        if (state == null) {
            return;
        }
        state.document().applyTo(this);
        storeMetadata = state.metadata();
        profileMetadata = state.profileMetadata();
        skipNextProfileSave = state.skipNextProfileSave();
        ConfigProfileManager.ensureProfiles(this);
    }

    void copyRuntimeStateFrom(Config source) {
        if (source == null) {
            return;
        }
        profileMetadata = source.profileMetadata == null
                ? new ProfileMetadataStore() : source.profileMetadata.copy();
        storeMetadata = source.storeMetadata;
        localBaselineToken = source.localBaselineToken;
        editBaseline = source.editBaseline;
        writtenMain = source.writtenMain;
        writtenProfiles = new LinkedHashMap<>(source.writtenProfiles);
        accessMode = source.accessMode;
        skipNextProfileSave = source.skipNextProfileSave;
        profilesNormalized = false;
    }

    private static Config fromLoadResult(LoadResult result) {
        Config config = new Config();
        config.restoreState(result.state());
        if (result instanceof LoadResult.ReadOnly readOnly) {
            config.enterReadOnlyMode(readOnly.reason());
        }
        return config;
    }

    private static boolean validFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() != 64) {
            return false;
        }
        for (int i = 0; i < fingerprint.length(); i++) {
            char c = fingerprint.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    public void resetActiveProfileToDefaults() {
        ConfigProfileManager.resetActiveProfileToDefaults(this);
    }

    public int profileCount() {
        return ConfigProfileManager.profileCount(this);
    }

    public int clampProfileIndex(int index) {
        return ConfigProfileManager.clampIndex(this, index);
    }

    public String profileName(int index) {
        return ConfigProfileManager.profileName(this, index);
    }

    public Profile profile(int index) {
        return ConfigProfileManager.profile(this, index);
    }

    public Profile activeProfile() {
        return profile(activeProfileIndex);
    }

    public VisibilitySettings visibility() {
        return activeProfile().visibility();
    }

    public PitchSettings pitch() {
        return activeProfile().pitch();
    }

    public LineSettings line() {
        return activeProfile().line();
    }

    public AmplitudeSettings amplitude() {
        return activeProfile().amplitude();
    }

    public VoidWarningSettings voidWarning() {
        return activeProfile().voidWarning();
    }

    public CommandPaletteAppearanceSettings commandPaletteAppearance() {
        return activeProfile().commandPaletteAppearance();
    }

    public DiagnosticsSettings diagnostics() {
        return activeProfile().diagnostics();
    }

    public boolean replaceProfile(int index, Profile profile) {
        return ConfigProfileManager.replaceProfile(this, index, profile);
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

    public Path getProfilePath(ConfigFileSystem fileSystem, int index) {
        return ConfigProfileManager.profilePath(fileSystem, this, index);
    }

    public int profileIndexByFileName(String fileName) {
        return ConfigProfileManager.profileIndexByFileName(this, fileName);
    }

    public boolean hasSameProfileState(Config other, String fileName) {
        return ConfigProfileManager.hasSameProfileState(this, other, fileName);
    }

    public void restoreProfileStateFrom(Config snapshot, String fileName) {
        ConfigProfileManager.restoreProfileStateFrom(this, snapshot, fileName);
    }

    public void acceptProfileStatesFromDisk(Config diskConfig, Set<String> fileNames) {
        ConfigProfileManager.acceptProfileStatesFromDisk(this, diskConfig, fileNames);
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

    public Profile createProfile() {
        return ConfigProfileManager.createProfile(this);
    }

    public Profile duplicateProfile(int index) {
        return ConfigProfileManager.duplicateProfile(this, index);
    }

    public void resetProfileToDefaults(int index) {
        ConfigProfileManager.resetProfileToDefaults(this, index);
    }

    public static Profile defaultProfileTemplate() {
        return ProfileDefaults.template();
    }

    public boolean deleteProfile(ConfigFileSystem fileSystem, int index) {
        requireWritable();
        return ConfigProfileManager.deleteProfile(fileSystem, this, index);
    }

    public StagedProfileDelete stageDeleteProfile(int index) {
        return ConfigProfileManager.stageDeleteProfile(this, index);
    }

    public void restoreStagedProfileDelete(StagedProfileDelete deleted) {
        ConfigProfileManager.restoreStagedProfileDelete(this, deleted);
    }

    public void deleteProfileFiles(ConfigFileSystem fileSystem, List<String> fileNames) {
        requireWritable();
        ProfilePersistence.deleteProfileFilesSafely(fileSystem, fileNames, this);
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


    static String defaultProfileBasedOn(String fileName) {
        return ProfileDefaults.basedOn(fileName);
    }

    private sealed interface AccessMode permits Writable, ReadOnly {
    }

    private enum Writable implements AccessMode {
        INSTANCE
    }

    private record ReadOnly(String warning) implements AccessMode {
        private ReadOnly {
            warning = warning == null ? "" : warning;
        }
    }
}
