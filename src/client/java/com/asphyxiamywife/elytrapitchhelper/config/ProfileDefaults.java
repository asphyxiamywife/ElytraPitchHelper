package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

final class ProfileDefaults {
    static final String DEFAULT_PROFILE_FILE_NAME = "default.json";

    private static final String BUNDLED_PROFILE_DIRECTORY = "assets/elytrapitchhelper/profiles";
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);
    private static final Object LOCK = new Object();
    private static volatile List<Profile> cachedDefaultProfiles;
    private static volatile List<BundledProfileResource> cachedBundledProfileResources;

    private ProfileDefaults() {
    }

    static List<Profile> profiles() {
        return copyProfiles(cachedDefaultProfiles());
    }

    static Profile template() {
        return cachedDefaultProfiles().get(0).copy();
    }

    static String basedOn(String fileName) {
        String normalized = ProfileFileNames.normalize(fileName);
        return DEFAULT_PROFILE_FILE_NAME.equalsIgnoreCase(normalized) ? "Bundled default" : "Custom";
    }

    static List<Profile> copyProfiles(List<Profile> source) {
        if (source == null) {
            return null;
        }
        List<Profile> copy = new ArrayList<>();
        for (Profile profile : source) {
            copy.add(profile == null ? null : profile.copy());
        }
        return copy;
    }

    static void copyBundledProfilesToConfig(Path profileDirectory) {
        List<BundledProfileResource> resources = bundledProfileResources();
        if (resources.isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(profileDirectory);
            for (BundledProfileResource resource : resources) {
                String fileName = ProfileFileNames.normalize(resource.fileName());
                if (fileName != null) {
                    ConfigFiles.writeBytesAtomic(profileDirectory.resolve(fileName), resource.contents());
                    LOGGER.info("Copied bundled Elytra Pitch Helper profile {}", fileName);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to copy bundled profiles into {}", profileDirectory, e);
        }
    }

    private static List<Profile> cachedDefaultProfiles() {
        List<Profile> profiles = cachedDefaultProfiles;
        if (profiles == null) {
            synchronized (LOCK) {
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

    private static List<Profile> readBundledProfiles() {
        List<Profile> profiles = new ArrayList<>();
        Profile bundledDefaults = null;
        for (BundledProfileResource resource : bundledProfileResources()) {
            try {
                JsonObject root = ConfigFiles.GSON.fromJson(new String(resource.contents(), StandardCharsets.UTF_8),
                        JsonObject.class);
                RepairLog repairs = new RepairLog("bundled profile " + resource.fileName());
                Profile defaults = bundledDefaults == null ? new Profile() : bundledDefaults;
                Profile profile = ProfileJson.parse(root, resource.fileName(), defaults, repairs);
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

    private static List<BundledProfileResource> bundledProfileResources() {
        List<BundledProfileResource> cachedResources = cachedBundledProfileResources;
        if (cachedResources != null) {
            return cachedResources;
        }

        synchronized (LOCK) {
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
        loadFabricBundledProfileResources(resources, seenFileNames);
        loadClasspathBundledProfileResources(resources, seenFileNames);
        resources.sort(Comparator.<BundledProfileResource>comparingInt(
                resource -> defaultProfileOrder(resource.fileName()))
                .thenComparing(resource -> resource.fileName().toLowerCase(Locale.ROOT)));
        return List.copyOf(resources);
    }

    private static void loadFabricBundledProfileResources(List<BundledProfileResource> resources,
            Set<String> seenFileNames) {
        try {
            FabricLoader.getInstance().getModContainer(ModConstants.MOD_ID).ifPresent(container -> {
                for (Path root : container.getRootPaths()) {
                    loadBundledProfileDirectory(root.resolve(BUNDLED_PROFILE_DIRECTORY), resources, seenFileNames);
                }
            });
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("Fabric Loader is unavailable while reading bundled profiles", e);
        }
    }

    private static void loadClasspathBundledProfileResources(List<BundledProfileResource> resources,
            Set<String> seenFileNames) {
        ClassLoader classLoader = ProfileDefaults.class.getClassLoader();
        if (classLoader == null) {
            return;
        }

        try {
            for (URL resource : java.util.Collections.list(classLoader.getResources(BUNDLED_PROFILE_DIRECTORY))) {
                if ("file".equals(resource.getProtocol())) {
                    loadBundledProfileDirectory(Paths.get(resource.toURI()), resources, seenFileNames);
                }
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.debug("Failed to read bundled profiles from the classpath", e);
        }
    }

    private static void loadBundledProfileDirectory(Path profileDirectory, List<BundledProfileResource> resources,
            Set<String> seenFileNames) {
        if (!Files.isDirectory(profileDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.list(profileDirectory)) {
            for (Path path : paths.filter(ConfigFiles::isJsonFile).toList()) {
                String fileName = ProfileFileNames.normalize(path.getFileName().toString());
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

    private static int defaultProfileOrder(String fileName) {
        return DEFAULT_PROFILE_FILE_NAME.equals(fileName.toLowerCase(Locale.ROOT)) ? 0 : 100;
    }

    private record BundledProfileResource(String fileName, byte[] contents) {
    }
}
