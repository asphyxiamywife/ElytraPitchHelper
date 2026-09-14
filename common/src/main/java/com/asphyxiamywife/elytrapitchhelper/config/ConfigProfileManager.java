package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class ConfigProfileManager {
    private ConfigProfileManager() {
    }

    static void syncSavedProfileMetadataFrom(Config cfg, Config savedConfig) {
        if (savedConfig == null) {
            return;
        }

        ensureProfiles(cfg);
        ensureProfiles(savedConfig);
        cfg.profileMetadata = savedConfig.profileMetadata == null ? new ProfileMetadataStore()
                : savedConfig.profileMetadata.copy();
        cfg.skipNextProfileSave = savedConfig.skipNextProfileSave;

        if (cfg.profileSortMode == Config.PROFILE_SORT_MODIFIED) {
            cfg.profilesNormalized = false;
            sortProfiles(cfg, cfg.activeProfileFile);
        }
    }

    static void resetActiveProfileToDefaults(Config cfg) {
        ensureProfiles(cfg);
        Profile active = cfg.profiles.get(cfg.activeProfileIndex);
        String name = active.name();
        String fileName = active.fileName();
        active = ProfileDefaults.template().withName(name).withFileName(fileName);
        cfg.profiles.set(cfg.activeProfileIndex, active);
        cfg.profileMetadata.markModified(fileName, System.currentTimeMillis(), "Default");
        cfg.profilesNormalized = false;
    }

    static int profileCount(Config cfg) {
        ensureProfiles(cfg);
        return cfg.profiles.size();
    }

    static String profileName(Config cfg, int index) {
        ensureProfiles(cfg);
        return cfg.profiles.get(clampIndex(cfg, index)).name();
    }

    static Profile profile(Config cfg, int index) {
        ensureProfiles(cfg);
        return cfg.profiles.get(clampIndex(cfg, index));
    }

    static long profileLastModifiedAtMillis(Config cfg, int index) {
        ensureProfiles(cfg);
        return ProfilePersistence.lastModifiedAtMillis(cfg.profileMetadata,
                cfg.profiles.get(clampIndex(cfg, index)).fileName());
    }

    static String profileBasedOn(Config cfg, int index) {
        ensureProfiles(cfg);
        return ProfilePersistence.basedOn(cfg.profileMetadata,
                cfg.profiles.get(clampIndex(cfg, index)).fileName());
    }

    static void setProfileName(Config cfg, int index, String name) {
        ensureProfiles(cfg);
        int clampedIndex = clampIndex(cfg, index);
        cfg.profiles.set(clampedIndex,
                cfg.profiles.get(clampedIndex).withName(name == null ? "" : name));
        cfg.profilesNormalized = false;
    }

    static boolean isActiveProfile(Config cfg, int index) {
        ensureProfiles(cfg);
        return clampIndex(cfg, index) == cfg.activeProfileIndex;
    }

    static String activeProfileName(Config cfg) {
        ensureProfiles(cfg);
        return cfg.profiles.get(cfg.activeProfileIndex).name();
    }

    static Path profilePath(ConfigFileSystem fileSystem, Config cfg, int index) {
        ensureProfiles(cfg);
        return ProfilePersistence.profilePath(fileSystem, cfg.profiles.get(clampIndex(cfg, index)));
    }

    static int profileIndexByFileName(Config cfg, String fileName) {
        ensureProfiles(cfg);
        return indexOfProfileFileWithoutEnsure(cfg, fileName);
    }

    static boolean hasSameProfileState(Config cfg, Config other, String fileName) {
        if (other == null || fileName == null) {
            return false;
        }
        ensureProfiles(cfg);
        ensureProfiles(other);
        int index = indexOfProfileFileWithoutEnsure(cfg, fileName);
        int otherIndex = indexOfProfileFileWithoutEnsure(other, fileName);
        return index >= 0 && otherIndex >= 0
                && cfg.profiles.get(index).equals(other.profiles.get(otherIndex));
    }

    static void restoreProfileStateFrom(Config cfg, Config snapshot, String fileName) {
        if (snapshot == null || fileName == null) {
            return;
        }
        ensureProfiles(cfg);
        ensureProfiles(snapshot);
        int index = indexOfProfileFileWithoutEnsure(cfg, fileName);
        int snapshotIndex = indexOfProfileFileWithoutEnsure(snapshot, fileName);
        if (index < 0 || snapshotIndex < 0) {
            return;
        }

        cfg.profiles.set(index, snapshot.profiles.get(snapshotIndex));
        cfg.profileMetadata.restoreProfileFrom(fileName, snapshot.profileMetadata);
    }

    static void acceptProfileStatesFromDisk(Config cfg, Config diskConfig, Set<String> fileNames) {
        if (diskConfig == null || fileNames == null || fileNames.isEmpty()) {
            return;
        }
        ensureProfiles(cfg);
        ensureProfiles(diskConfig);
        String selectedFile = cfg.activeProfileFile;
        int selectedIndex = cfg.activeProfileIndex;

        for (String fileName : fileNames) {
            int currentIndex = indexOfProfileFileByComparisonKeyWithoutEnsure(cfg, fileName);
            int diskIndex = indexOfProfileFileByComparisonKeyWithoutEnsure(diskConfig, fileName);
            if (currentIndex < 0 && diskIndex >= 0) {
                Profile diskProfile = diskConfig.profiles.get(diskIndex);
                cfg.profiles.add(diskProfile);
                cfg.profileMetadata.copyProfileFrom(diskProfile.fileName(), diskConfig.profileMetadata);
                cfg.storeMetadata = cfg.storeMetadata.withProfileFrom(
                        diskConfig.storeMetadata, diskProfile.fileName());
                if (sameProfileFile(diskProfile.fileName(), diskConfig.activeProfileFile)) {
                    selectedFile = diskProfile.fileName();
                }
            } else if (currentIndex >= 0 && diskIndex >= 0) {
                Profile diskProfile = diskConfig.profiles.get(diskIndex);
                boolean replacedSelectedProfile = sameProfileFile(
                        cfg.profiles.get(currentIndex).fileName(), selectedFile);
                cfg.profiles.set(currentIndex, diskProfile);
                cfg.profileMetadata.copyProfileFrom(diskProfile.fileName(), diskConfig.profileMetadata);
                cfg.storeMetadata = cfg.storeMetadata.withProfileFrom(
                        diskConfig.storeMetadata, diskProfile.fileName());
                if (replacedSelectedProfile) {
                    selectedFile = diskProfile.fileName();
                }
            } else if (currentIndex >= 0) {
                boolean removedSelectedProfile = sameProfileFile(fileName, selectedFile);
                cfg.profiles.remove(currentIndex);
                cfg.storeMetadata = cfg.storeMetadata.withProfileFrom(diskConfig.storeMetadata, fileName);
                if (removedSelectedProfile) {
                    selectedFile = diskConfig.activeProfileFile;
                }
            }
        }

        if (cfg.profiles.isEmpty()) {
            cfg.profiles = ProfileDefaults.copyProfiles(diskConfig.profiles);
            cfg.profileMetadata = diskConfig.profileMetadata.copy();
            cfg.storeMetadata = diskConfig.storeMetadata;
            selectedFile = diskConfig.activeProfileFile;
            selectedIndex = diskConfig.activeProfileIndex;
        }

        cfg.profilesNormalized = false;
        applySelection(cfg, ProfileOrdering.normalize(cfg.profiles, selectedIndex, selectedFile,
                cfg.profileSortMode, cfg.profileMetadata));
        cfg.profilesNormalized = true;
    }

    static void acceptMainConfigStateFromDisk(Config cfg, Config diskConfig) {
        if (diskConfig == null) {
            return;
        }
        ensureProfiles(cfg);
        ensureProfiles(diskConfig);

        cfg.version = diskConfig.version;
        cfg.enabled = diskConfig.enabled;
        cfg.profileSortMode = diskConfig.profileSortMode;
        cfg.commandPaletteUsage = diskConfig.commandPaletteUsage == null
                ? new CommandPaletteUsageSettings() : diskConfig.commandPaletteUsage;
        cfg.sectionCollapse = diskConfig.sectionCollapse == null
                ? new SectionCollapseSettings() : diskConfig.sectionCollapse;
        cfg.usedSettingsSearch = diskConfig.usedSettingsSearch;
        cfg.profilesNormalized = false;
        applySelection(cfg, ProfileOrdering.normalize(cfg.profiles, diskConfig.activeProfileIndex,
                diskConfig.activeProfileFile, cfg.profileSortMode, cfg.profileMetadata));
        cfg.profilesNormalized = true;
        cfg.restoreMainConfigFileState(diskConfig.mainConfigFileState());
    }

    static void cycleProfileSortMode(Config cfg) {
        ensureProfiles(cfg);
        String activeFile = cfg.profiles.get(cfg.activeProfileIndex).fileName();
        cfg.profileSortMode = Config.nextProfileSortMode(cfg.profileSortMode);
        sortProfiles(cfg, activeFile);
    }

    static void cycleProfileSortModeBackward(Config cfg) {
        ensureProfiles(cfg);
        String activeFile = cfg.profiles.get(cfg.activeProfileIndex).fileName();
        cfg.profileSortMode = Config.previousProfileSortMode(cfg.profileSortMode);
        sortProfiles(cfg, activeFile);
    }

    static void selectProfile(Config cfg, int index) {
        ensureProfiles(cfg);
        cfg.activeProfileIndex = clampIndex(cfg, index);
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName();
    }

    private static String allocateProfileFile(Config config, String name, Set<String> usedFiles) {
        return config.localBaselineToken == null ? ProfileFileNames.unique(name, usedFiles)
                : config.localBaselineToken.owner.allocateProfileFile(config, name, usedFiles);
    }

    static Profile createProfile(Config cfg) {
        ensureProfiles(cfg);
        Set<String> usedFileNames = ProfileNameAllocator.usedFileNames(cfg.profiles);
        String name = ProfileNameAllocator.uniqueName(cfg.profiles, "New Profile");
        Profile profile = ProfileDefaults.template()
                .withName(name)
                .withFileName(allocateProfileFile(cfg, name, usedFileNames));
        cfg.profileMetadata.markCreated(profile.fileName(), System.currentTimeMillis(), "Default");
        cfg.profiles.add(profile);
        cfg.profilesNormalized = false;
        return profile;
    }

    static Profile duplicateProfile(Config cfg, int index) {
        ensureProfiles(cfg);
        int clampedIndex = clampIndex(cfg, index);
        Profile original = cfg.profiles.get(clampedIndex);
        String duplicateName = ProfileNameAllocator.uniqueCopyName(cfg.profiles, original.name());
        Profile duplicate = original.withName(duplicateName).withFileName(
                allocateProfileFile(cfg, duplicateName, ProfileNameAllocator.usedFileNames(cfg.profiles)));
        cfg.profileMetadata.markCreated(
                duplicate.fileName(), System.currentTimeMillis(), original.name());
        cfg.profiles.add(clampedIndex + 1, duplicate);
        if (cfg.activeProfileIndex > clampedIndex) {
            cfg.activeProfileIndex++;
        }
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName();
        cfg.profilesNormalized = false;
        return duplicate;
    }

    static void resetProfileToDefaults(Config cfg, int index) {
        ensureProfiles(cfg);
        int clampedIndex = clampIndex(cfg, index);
        Profile profile = cfg.profiles.get(clampedIndex);
        String name = profile.name();
        String fileName = profile.fileName();
        profile = ProfileDefaults.template().withName(name).withFileName(fileName);
        cfg.profiles.set(clampedIndex, profile);
        cfg.profileMetadata.markModified(fileName, System.currentTimeMillis(), "Default");
        cfg.profilesNormalized = false;
    }

    static boolean deleteProfile(ConfigFileSystem fileSystem, Config cfg, int index) {
        StagedProfileDelete deleted = stageDeleteProfile(cfg, index);
        if (deleted == null) {
            return false;
        }

        try {
            ProfilePersistence.deleteProfileFilesSafely(
                    fileSystem, List.of(deleted.fileName()), cfg);
        } catch (RuntimeException e) {
            restoreStagedProfileDelete(cfg, deleted);
            throw e;
        }
        return true;
    }

    static StagedProfileDelete stageDeleteProfile(Config cfg, int index) {
        ensureProfiles(cfg);
        if (cfg.profiles.size() <= 1) {
            return null;
        }

        int clampedIndex = clampIndex(cfg, index);
        Profile removed = cfg.profiles.remove(clampedIndex);
        boolean wasActive = cfg.activeProfileIndex == clampedIndex;

        if (wasActive) {
            cfg.activeProfileIndex = MathUtil.clamp(clampedIndex, 0, cfg.profiles.size() - 1);
        } else if (cfg.activeProfileIndex > clampedIndex) {
            cfg.activeProfileIndex--;
        }
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName();
        cfg.profilesNormalized = false;
        return new StagedProfileDelete(removed, clampedIndex, wasActive);
    }

    static void restoreStagedProfileDelete(Config cfg, StagedProfileDelete deleted) {
        if (deleted == null || deleted.profile() == null) {
            return;
        }

        ensureProfiles(cfg);
        String selectedFile = deleted.wasActive() ? deleted.fileName() : cfg.activeProfileFile;
        int insertIndex = MathUtil.clamp(deleted.index(), 0, cfg.profiles.size());
        cfg.profiles.add(insertIndex, deleted.profile());
        cfg.profilesNormalized = false;
        sortProfiles(cfg, selectedFile);
    }


    static void loadProfiles(
            ConfigFileSystem fileSystem, Config cfg, boolean createMissingProfiles) {
        ProfilePersistence.LoadResult result =
                ProfilePersistence.loadProfiles(fileSystem, createMissingProfiles, cfg);
        cfg.profiles = result.profiles();
        cfg.skipNextProfileSave = result.skipNextProfileSave();
        cfg.profilesNormalized = false;
        ensureProfiles(cfg);
    }

    static void saveProfiles(ConfigFileSystem fileSystem, Config cfg,
            Set<Path> approvedConflictPaths) {
        if (ProfilePersistence.saveProfiles(fileSystem, cfg, approvedConflictPaths)
                && cfg.profileSortMode == Config.PROFILE_SORT_MODIFIED) {
            cfg.profilesNormalized = false;
            ensureProfiles(cfg);
        }
    }

    static void ensureProfiles(Config cfg) {
        if (cfg.profilesNormalized && cfg.profiles != null && !cfg.profiles.isEmpty()) {
            return;
        }
        ProfileOrdering.Selection selection = ProfileOrdering.normalize(cfg.profiles, cfg.activeProfileIndex,
                cfg.activeProfileFile, cfg.profileSortMode, cfg.profileMetadata);
        applySelection(cfg, selection);
        cfg.profilesNormalized = true;
    }

    static boolean replaceProfile(Config cfg, int index, Profile profile) {
        ensureProfiles(cfg);
        boolean managedDraft = cfg.localBaselineToken != null;
        if (managedDraft && (index < 0 || index >= cfg.profiles.size())) {
            return false;
        }
        int clampedIndex = clampIndex(cfg, index);
        Profile current = cfg.profiles.get(clampedIndex);
        Profile replacement = profile == null ? ProfileDefaults.template() : profile;
        if (managedDraft && replacement.fileName() != null
                && !sameProfileFile(current.fileName(), replacement.fileName())) {
            return false;
        }
        if (managedDraft || replacement.fileName() == null) {
            replacement = replacement.withFileName(current.fileName());
        }
        cfg.profiles.set(clampedIndex, replacement);
        if (clampedIndex == cfg.activeProfileIndex) {
            cfg.activeProfileFile = replacement.fileName();
        }
        cfg.profilesNormalized = false;
        return true;
    }

    static int clampIndex(Config cfg, int index) {
        ensureProfilesWithoutIndexClamp(cfg);
        return ProfileOrdering.clampIndex(cfg.profiles, index);
    }


    static void saveProfileMetadata(ConfigFileSystem fileSystem, Config cfg) {
        if (cfg.profileMetadata == null) {
            cfg.profileMetadata = new ProfileMetadataStore();
        }
        ProfilePersistence.saveProfileMetadata(fileSystem, cfg);
    }

    private static void sortProfiles(Config cfg, String selectedFile) {
        ensureProfilesWithoutIndexClamp(cfg);
        applySelection(cfg, ProfileOrdering.sort(cfg.profiles, cfg.activeProfileIndex, selectedFile,
                cfg.profileSortMode, cfg.profileMetadata));
        cfg.profilesNormalized = true;
    }

    private static void applySelection(Config cfg, ProfileOrdering.Selection selection) {
        cfg.profiles = selection.profiles();
        cfg.activeProfileIndex = selection.activeIndex();
        cfg.activeProfileFile = selection.activeFile();
    }

    private static int indexOfProfileFileWithoutEnsure(Config cfg, String fileName) {
        return ProfileOrdering.indexOfFile(cfg.profiles, fileName);
    }

    private static int indexOfProfileFileByComparisonKeyWithoutEnsure(Config cfg, String fileName) {
        String fileNameKey = ProfileFileNames.comparisonKey(fileName);
        if (fileNameKey == null) {
            return -1;
        }
        for (int i = 0; i < cfg.profiles.size(); i++) {
            if (fileNameKey.equals(ProfileFileNames.comparisonKey(cfg.profiles.get(i).fileName()))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameProfileFile(String first, String second) {
        String firstKey = ProfileFileNames.comparisonKey(first);
        return firstKey != null && firstKey.equals(ProfileFileNames.comparisonKey(second));
    }

    private static void ensureProfilesWithoutIndexClamp(Config cfg) {
        if (cfg.profiles == null || cfg.profiles.isEmpty()) {
            cfg.profiles = ProfileDefaults.profiles();
            cfg.profilesNormalized = false;
        }
    }
}
