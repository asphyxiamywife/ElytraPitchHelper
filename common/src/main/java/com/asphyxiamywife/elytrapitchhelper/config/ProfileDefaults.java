package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.platform.PlatformServices;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        return cachedDefaultProfiles().get(0);
    }

    static String basedOn(String fileName) {
        String normalized = ProfileFileNames.normalize(fileName);
        return DEFAULT_PROFILE_FILE_NAME.equals(normalized) ? "Bundled default" : "Custom";
    }

    static List<Profile> copyProfiles(List<Profile> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source);
    }


    static void copyBundledProfilesToConfig(
            ConfigFileSystem fileSystem, Path profileDirectory) {
        List<BundledProfileResource> resources = bundledProfileResources();
        if (resources.isEmpty()) {
            return;
        }

        try {
            fileSystem.createDirectories(profileDirectory);
            for (BundledProfileResource resource : resources) {
                String fileName = ProfileFileNames.normalize(resource.fileName());
                if (fileName != null) {
                    ConfigFiles.writeBytesAtomic(
                            fileSystem, profileDirectory.resolve(fileName), resource.contents());
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
        if (profiles.isEmpty()) {
            throw new IllegalStateException("Bundled " + DEFAULT_PROFILE_FILE_NAME + " is missing or invalid");
        }
        return List.copyOf(profiles);
    }

    private static List<Profile> readBundledProfiles() {
        List<Profile> profiles = new ArrayList<>();
        Profile bundledDefaults = null;
        for (BundledProfileResource resource : bundledProfileResources()) {
            try {
                JsonObject root = ConfigFiles.GSON.fromJson(new String(resource.contents(), StandardCharsets.UTF_8),
                        JsonObject.class);
                RepairLog repairs = new RepairLog("bundled profile " + resource.fileName());
                boolean isDefault = DEFAULT_PROFILE_FILE_NAME.equals(resource.fileName());
                if (bundledDefaults == null && !isDefault) {
                    throw new IllegalStateException("Bundled profiles must be ordered after default.json");
                }
                Profile profile = isDefault
                        ? parseAuthoritativeDefault(root, resource.fileName())
                        : ProfileJson.parse(root, resource.fileName(), bundledDefaults, repairs);
                Profile sanitized = profile.sanitized(repairs, isDefault ? profile : bundledDefaults);
                if (isDefault && !profile.equals(sanitized)) {
                    throw new IllegalStateException("Bundled default profile contains invalid settings");
                }
                profile = sanitized;
                repairs.log();
                profiles.add(profile);
                if (isDefault) {
                    bundledDefaults = profile;
                }
            } catch (JsonParseException | IllegalStateException e) {
                LOGGER.warn("Failed to parse bundled profile {}, skipping it", resource.fileName(), e);
            }
        }
        return profiles;
    }

    private static Profile parseAuthoritativeDefault(JsonObject root, String fileName) {
        if (root == null || !root.has("version") || !root.has("name")) {
            throw new IllegalStateException("Bundled default profile must contain version and name");
        }
        for (SettingSpec<?> spec : SettingsRegistry.serialized()) {
            JsonObject section = root.has(spec.jsonSection()) && root.get(spec.jsonSection()).isJsonObject()
                    ? root.getAsJsonObject(spec.jsonSection()) : null;
            if (section == null || !section.has(spec.json().field())) {
                throw new IllegalStateException("Bundled default profile is missing "
                        + spec.jsonSection() + "." + spec.json().field());
            }
        }
        BundledDefault document = ConfigFiles.GSON.fromJson(root, BundledDefault.class);
        if (document == null || document.visibility() == null || document.pitch() == null
                || document.line() == null || document.amplitude() == null
                || document.voidWarning() == null || document.commandPaletteAppearance() == null
                || document.diagnostics() == null) {
            throw new IllegalStateException("Bundled default profile must contain every settings section");
        }
        return new Profile(document.version(), document.name(), document.visibility(), document.pitch(),
                document.line(), document.amplitude(), document.voidWarning(),
                document.commandPaletteAppearance(), document.diagnostics(),
                ProfileFileNames.normalize(fileName));
    }

    private record BundledDefault(
            int version,
            String name,
            VisibilitySettings visibility,
            PitchSettings pitch,
            LineSettings line,
            AmplitudeSettings amplitude,
            VoidWarningSettings voidWarning,
            CommandPaletteAppearanceSettings commandPaletteAppearance,
            DiagnosticsSettings diagnostics) {
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
        loadBundledDefaultResource(resources, seenFileNames);
        loadPlatformBundledProfileResources(resources, seenFileNames);
        loadClasspathBundledProfileResources(resources, seenFileNames);
        resources.sort(Comparator.<BundledProfileResource>comparingInt(
                resource -> defaultProfileOrder(resource.fileName()))
                .thenComparing(resource -> resource.fileName().toLowerCase(Locale.ROOT))
                .thenComparing(BundledProfileResource::fileName));
        return List.copyOf(resources);
    }

    private static void loadBundledDefaultResource(List<BundledProfileResource> resources,
            Set<String> seenFileNames) {
        String resourcePath = "/" + BUNDLED_PROFILE_DIRECTORY + "/" + DEFAULT_PROFILE_FILE_NAME;
        try (InputStream stream = ProfileDefaults.class.getResourceAsStream(resourcePath)) {
            if (stream != null && seenFileNames.add(DEFAULT_PROFILE_FILE_NAME)) {
                resources.add(new BundledProfileResource(DEFAULT_PROFILE_FILE_NAME, stream.readAllBytes()));
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to read bundled default profile {}", resourcePath, e);
        }
    }

    private static void loadPlatformBundledProfileResources(List<BundledProfileResource> resources,
            Set<String> seenFileNames) {
        try {
            for (Path root : PlatformServices.modRootPaths()) {
                loadBundledProfileDirectory(root.resolve(BUNDLED_PROFILE_DIRECTORY), resources, seenFileNames);
            }
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("Platform roots are unavailable while reading bundled profiles", e);
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
                loadClasspathBundledProfileResource(resource, resources, seenFileNames);
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.debug("Failed to read bundled profiles from the classpath", e);
        }
    }

    private static void loadClasspathBundledProfileResource(URL resource, List<BundledProfileResource> resources,
            Set<String> seenFileNames) throws URISyntaxException, IOException {
        URI uri = resource.toURI();
        if ("file".equals(resource.getProtocol())) {
            loadBundledProfileDirectory(Paths.get(uri), resources, seenFileNames);
            return;
        }
        if ("jar".equals(resource.getProtocol())) {
            loadJarBundledProfileDirectory(uri, resources, seenFileNames);
        }
    }

    private static void loadJarBundledProfileDirectory(URI uri, List<BundledProfileResource> resources,
            Set<String> seenFileNames) throws IOException {
        FileSystem fileSystem = null;
        boolean closeFileSystem = false;
        try {
            fileSystem = FileSystems.newFileSystem(uri, Map.of());
            closeFileSystem = true;
        } catch (FileSystemAlreadyExistsException ignored) {
            fileSystem = FileSystems.getFileSystem(uri);
        }

        try {
            loadBundledProfileDirectory(Paths.get(uri), resources, seenFileNames);
        } finally {
            if (closeFileSystem && fileSystem != null) {
                fileSystem.close();
            }
        }
    }

    private static boolean isBundledJsonFile(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                        .endsWith(ProfileFileNames.JSON_SUFFIX);
    }

    private static void loadBundledProfileDirectory(Path profileDirectory, List<BundledProfileResource> resources,
            Set<String> seenFileNames) {
        if (!Files.isDirectory(profileDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.list(profileDirectory)) {
            for (Path path : paths.filter(ProfileDefaults::isBundledJsonFile).toList()) {
                String fileName = ProfileFileNames.normalize(path.getFileName().toString());
                if (fileName != null && seenFileNames.add(fileName)) {
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
        return DEFAULT_PROFILE_FILE_NAME.equals(fileName) ? 0 : 100;
    }

    private record BundledProfileResource(String fileName, byte[] contents) {
    }
}
