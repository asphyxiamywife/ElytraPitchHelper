package com.asphyxiamywife.elytrapitchhelper.config;

import java.nio.file.Path;
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

        if (cfg.profileSortMode == Config.PROFILE_SORT_MODIFIED) {
            cfg.profilesNormalized = false;
            sortProfiles(cfg, cfg.activeProfileFile);
        }
    }

    static void resetToDefaults(Config cfg) {
        ConfigCopy.copyFrom(cfg, new Config());
        cfg.profiles = ProfileDefaults.profiles();
        cfg.profilesNormalized = false;
        cfg.activeProfileIndex = 0;
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName;
        bindActiveProfile(cfg);
    }

    static void resetActiveProfileToDefaults(Config cfg) {
        ensureProfiles(cfg);
        Profile active = cfg.profiles.get(cfg.activeProfileIndex);
        String name = active.name;
        String fileName = active.fileName;
        active.copyFrom(ProfileDefaults.template());
        active.name = name;
        active.fileName = fileName;
        cfg.profileMetadata.profile(active.fileName).markModified(System.currentTimeMillis(), "Default");
        bindActiveProfile(cfg);
    }

    static int profileCount(Config cfg) {
        ensureProfiles(cfg);
        return cfg.profiles.size();
    }

    static String profileName(Config cfg, int index) {
        ensureProfiles(cfg);
        return cfg.profiles.get(clampIndex(cfg, index)).name;
    }

    static Profile profile(Config cfg, int index) {
        ensureProfiles(cfg);
        return cfg.profiles.get(clampIndex(cfg, index));
    }

    static long profileLastModifiedAtMillis(Config cfg, int index) {
        ensureProfiles(cfg);
        return ProfilePersistence.lastModifiedAtMillis(cfg.profileMetadata,
                cfg.profiles.get(clampIndex(cfg, index)).fileName);
    }

    static String profileBasedOn(Config cfg, int index) {
        ensureProfiles(cfg);
        return ProfilePersistence.basedOn(cfg.profileMetadata,
                cfg.profiles.get(clampIndex(cfg, index)).fileName);
    }

    static void setProfileName(Config cfg, int index, String name) {
        ensureProfiles(cfg);
        int clampedIndex = clampIndex(cfg, index);
        cfg.profiles.get(clampedIndex).name = name == null ? "" : name;
        cfg.profilesNormalized = false;
    }

    static boolean isActiveProfile(Config cfg, int index) {
        ensureProfiles(cfg);
        return clampIndex(cfg, index) == cfg.activeProfileIndex;
    }

    static String activeProfileName(Config cfg) {
        ensureProfiles(cfg);
        return cfg.profiles.get(cfg.activeProfileIndex).name;
    }

    static Path profilePath(Config cfg, int index) {
        ensureProfiles(cfg);
        return ProfilePersistence.profilePath(cfg.profiles.get(clampIndex(cfg, index)));
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
                && ConfigFiles.GSON.toJsonTree(cfg.profiles.get(index))
                        .equals(ConfigFiles.GSON.toJsonTree(other.profiles.get(otherIndex)));
    }

    static boolean hasSameState(Config cfg, Config other) {
        if (other == null) {
            return false;
        }
        ensureProfiles(cfg);
        ensureProfiles(other);
        if (!ConfigFiles.GSON.toJsonTree(cfg).equals(ConfigFiles.GSON.toJsonTree(other))
                || cfg.profiles.size() != other.profiles.size()) {
            return false;
        }
        for (int i = 0; i < cfg.profiles.size(); i++) {
            Profile profile = cfg.profiles.get(i);
            Profile otherProfile = other.profiles.get(i);
            if (!java.util.Objects.equals(profile.fileName, otherProfile.fileName)
                    || !ConfigFiles.GSON.toJsonTree(profile)
                            .equals(ConfigFiles.GSON.toJsonTree(otherProfile))) {
                return false;
            }
        }
        return true;
    }

    static void prepareSnapshotRestoreFrom(Config snapshot, Config current) {
        if (current == null) {
            return;
        }
        ensureProfiles(snapshot);
        ensureProfiles(current);
        for (Profile profile : snapshot.profiles) {
            ProfileMetadata restoredMetadata = snapshot.profileMetadata.profile(profile.fileName);
            int currentIndex = indexOfProfileFileWithoutEnsure(current, profile.fileName);
            if (currentIndex >= 0) {
                restoredMetadata.syncLoadedFileFrom(current.profileMetadata.profile(profile.fileName));
            }
        }
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

        cfg.profiles.get(index).copyFrom(snapshot.profiles.get(snapshotIndex));
        ProfileMetadata currentMetadata = cfg.profileMetadata.profile(fileName);
        currentMetadata.restoreFromKeepingLoadedFile(snapshot.profileMetadata.profile(fileName));
        if (index == cfg.activeProfileIndex) {
            bindActiveProfile(cfg);
        }
    }

    static void cycleProfileSortMode(Config cfg) {
        ensureProfiles(cfg);
        String activeFile = cfg.profiles.get(cfg.activeProfileIndex).fileName;
        cfg.profileSortMode = Config.nextProfileSortMode(cfg.profileSortMode);
        sortProfiles(cfg, activeFile);
    }

    static void cycleProfileSortModeBackward(Config cfg) {
        ensureProfiles(cfg);
        String activeFile = cfg.profiles.get(cfg.activeProfileIndex).fileName;
        cfg.profileSortMode = Config.previousProfileSortMode(cfg.profileSortMode);
        sortProfiles(cfg, activeFile);
    }

    static void selectProfile(Config cfg, int index) {
        ensureProfiles(cfg);
        bindActiveProfileIfNeeded(cfg);
        cfg.activeProfileIndex = clampIndex(cfg, index);
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName;
        bindActiveProfile(cfg);
    }

    static void applyProfileIfActive(Config cfg, int index) {
        ensureProfiles(cfg);
        if (clampIndex(cfg, index) == cfg.activeProfileIndex) {
            bindActiveProfile(cfg);
        }
    }

    static Profile createProfile(Config cfg) {
        ensureProfiles(cfg);
        bindActiveProfileIfNeeded(cfg);

        Set<String> usedFileNames = ProfileNameAllocator.usedFileNames(cfg.profiles);
        String name = ProfileNameAllocator.uniqueName(cfg.profiles, "New Profile");
        Profile profile = ProfileDefaults.template();
        profile.name = name;
        profile.fileName = ProfileFileNames.unique(name, usedFileNames);
        cfg.profileMetadata.profile(profile.fileName).markCreated(System.currentTimeMillis(), "Default");
        cfg.profiles.add(profile);
        cfg.profilesNormalized = false;
        return profile;
    }

    static Profile duplicateProfile(Config cfg, int index) {
        ensureProfiles(cfg);
        bindActiveProfileIfNeeded(cfg);

        int clampedIndex = clampIndex(cfg, index);
        Profile original = cfg.profiles.get(clampedIndex);
        Profile duplicate = original.copy();
        duplicate.name = ProfileNameAllocator.uniqueCopyName(cfg.profiles, original.name);
        duplicate.fileName = ProfileFileNames.unique(duplicate.name,
                ProfileNameAllocator.usedFileNames(cfg.profiles));
        cfg.profileMetadata.profile(duplicate.fileName).markCreated(System.currentTimeMillis(), original.name);
        cfg.profiles.add(clampedIndex + 1, duplicate);
        if (cfg.activeProfileIndex > clampedIndex) {
            cfg.activeProfileIndex++;
        }
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName;
        cfg.profilesNormalized = false;
        return duplicate;
    }

    static void resetProfileToDefaults(Config cfg, int index) {
        ensureProfiles(cfg);
        int clampedIndex = clampIndex(cfg, index);
        Profile profile = cfg.profiles.get(clampedIndex);
        String name = profile.name;
        String fileName = profile.fileName;
        profile.copyFrom(ProfileDefaults.template());
        profile.name = name;
        profile.fileName = fileName;
        cfg.profileMetadata.profile(profile.fileName).markModified(System.currentTimeMillis(), "Default");
        if (clampedIndex == cfg.activeProfileIndex) {
            bindActiveProfile(cfg);
        }
    }

    static boolean deleteActiveProfile(Config cfg) {
        return deleteProfile(cfg, cfg.activeProfileIndex);
    }

    static boolean deleteProfile(Config cfg, int index) {
        StagedProfileDelete deleted = stageDeleteProfile(cfg, index);
        if (deleted == null) {
            return false;
        }

        ProfilePersistence.deleteProfileFile(deleted.fileName());
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
            cfg.activeProfileIndex = Math.max(0, Math.min(clampedIndex, cfg.profiles.size() - 1));
            bindActiveProfile(cfg);
        } else if (cfg.activeProfileIndex > clampedIndex) {
            cfg.activeProfileIndex--;
        }
        cfg.activeProfileFile = cfg.profiles.get(cfg.activeProfileIndex).fileName;
        cfg.profilesNormalized = false;
        return new StagedProfileDelete(removed, clampedIndex, wasActive);
    }

    static void restoreStagedProfileDelete(Config cfg, StagedProfileDelete deleted) {
        if (deleted == null || deleted.profile() == null) {
            return;
        }

        ensureProfiles(cfg);
        String selectedFile = deleted.wasActive() ? deleted.fileName() : cfg.activeProfileFile;
        int insertIndex = Math.max(0, Math.min(deleted.index(), cfg.profiles.size()));
        cfg.profiles.add(insertIndex, deleted.profile().copy());
        cfg.profilesNormalized = false;
        sortProfiles(cfg, selectedFile);
        if (deleted.wasActive()) {
            bindActiveProfile(cfg);
        }
    }

    static void loadProfiles(Config cfg, boolean createMissingProfiles) {
        ProfilePersistence.LoadResult result = ProfilePersistence.loadProfiles(createMissingProfiles,
                cfg.profileMetadata);
        cfg.profiles = result.profiles();
        cfg.skipNextProfileSave = result.skipNextProfileSave();
        cfg.profilesNormalized = false;
        ensureProfiles(cfg);
    }

    static void saveProfiles(Config cfg) {
        if (ProfilePersistence.saveProfiles(cfg.profiles, cfg.profileMetadata)
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

    static void bindActiveProfile(Config cfg) {
        ensureProfiles(cfg);
        Profile active = cfg.profiles.get(cfg.activeProfileIndex);
        active.bindTo(cfg);
        cfg.activeProfileFile = active.fileName;
    }

    static void bindActiveProfileIfNeeded(Config cfg) {
        ensureProfiles(cfg);
        if (!isBoundToActiveProfile(cfg)) {
            cfg.profiles.get(cfg.activeProfileIndex).copyFrom(cfg);
        }
        bindActiveProfile(cfg);
    }

    static boolean isBoundToActiveProfile(Config cfg) {
        ensureProfiles(cfg);
        return hasActiveProfileBinding(cfg);
    }

    static boolean hasActiveProfileBinding(Config cfg) {
        if (cfg.profiles == null || cfg.profiles.isEmpty()) {
            return false;
        }
        int activeIndex = ProfileOrdering.clampIndex(cfg.profiles, cfg.activeProfileIndex);
        if (activeIndex < 0 || activeIndex >= cfg.profiles.size()) {
            return false;
        }
        Profile active = cfg.profiles.get(activeIndex);
        if (active == null) {
            return false;
        }
        active.ensureValueObjects();
        return cfg.visibility == active.visibility
                && cfg.pitch == active.pitch
                && cfg.line == active.line
                && cfg.amplitude == active.amplitude
                && cfg.voidWarning == active.voidWarning;
    }

    static int clampIndex(Config cfg, int index) {
        ensureProfilesWithoutIndexClamp(cfg);
        return ProfileOrdering.clampIndex(cfg.profiles, index);
    }

    static void saveProfileMetadata(Config cfg) {
        if (cfg.profileMetadata == null) {
            cfg.profileMetadata = new ProfileMetadataStore();
        }
        ProfilePersistence.saveProfileMetadata(cfg.profileMetadata, cfg.profiles);
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

    private static void ensureProfilesWithoutIndexClamp(Config cfg) {
        if (cfg.profiles == null || cfg.profiles.isEmpty()) {
            cfg.profiles = ProfileDefaults.profiles();
            cfg.profilesNormalized = false;
        }
    }
}
