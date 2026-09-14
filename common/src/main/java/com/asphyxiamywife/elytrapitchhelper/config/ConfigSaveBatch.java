package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ConfigSaveBatch {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);
    private static final int FORMAT_VERSION = 2;
    private static final String DIRECTORY_PREFIX = "save-batch-";
    private static final String ACTIVE_LOCK = "active.lock";
    private static final String JOURNAL = "journal.json";
    private static final String ROLLBACK_JOURNAL = "rollback.json";
    private static final String FINALIZED = "finalized";
    private static final byte[] COMMITTED = "committed\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROLLED_BACK = "rolled-back\n".getBytes(StandardCharsets.UTF_8);
    private static final ThreadLocal<ConfigSaveBatch> CURRENT = new ThreadLocal<>();

    private final ConfigFileSystem fileSystem;
    private final Path configDirectory;
    private final Path directory;
    private final List<Entry> entries;
    private final FileChannel activeChannel;
    private final FileLock activeLock;
    private final Set<Path> mutatedTargets = new LinkedHashSet<>();
    private final List<Runnable> afterCommitActions = new ArrayList<>();
    private int stagedDeleteIndex;
    private boolean finished;

    private ConfigSaveBatch(ConfigFileSystem fileSystem, Path configDirectory, Path directory,
            List<Entry> entries, FileChannel activeChannel, FileLock activeLock) {
        this.fileSystem = fileSystem;
        this.configDirectory = configDirectory;
        this.directory = directory;
        this.entries = entries;
        this.activeChannel = activeChannel;
        this.activeLock = activeLock;
    }

    static ConfigSaveBatch begin(ConfigFileSystem fileSystem, List<Path> targets) {
        if (CURRENT.get() != null) {
            throw new ConfigSaveException("A config save batch is already active on this thread");
        }
        if (!recoverIncomplete(fileSystem)) {
            throw new ConfigSaveException("Cannot save while an earlier config save batch awaits recovery");
        }
        Path configDirectory = Config.getConfigDirectory(fileSystem).toAbsolutePath().normalize();
        Path internalDirectory = configDirectory.resolve(".internal");
        Path directory = null;
        FileChannel activeChannel = null;
        FileLock activeLock = null;
        try {
            fileSystem.createDirectories(internalDirectory);
            directory = fileSystem.createTempDirectory(internalDirectory, DIRECTORY_PREFIX);
            activeChannel = FileChannel.open(directory.resolve(ACTIVE_LOCK),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            activeLock = activeChannel.lock();

            Set<Path> uniqueTargets = new LinkedHashSet<>();
            for (Path target : targets) {
                Path normalized = target.toAbsolutePath().normalize();
                if (!normalized.startsWith(configDirectory)) {
                    throw new IOException("Config save target escapes config directory: " + target);
                }
                uniqueTargets.add(normalized);
            }

            List<Entry> entries = new ArrayList<>();
            int backupIndex = 0;
            for (Path target : uniqueTargets) {
                String relative = configDirectory.relativize(target).toString();
                if (!fileSystem.exists(target, true)) {
                    entries.add(new Entry(relative, Kind.MISSING, null, null, null));
                    continue;
                }
                if (fileSystem.isSymbolicLink(target)) {
                    entries.add(new Entry(relative, Kind.SYMLINK, null,
                            fileSystem.readSymbolicLink(target).toString(), null));
                    continue;
                }
                if (!fileSystem.isRegularFile(target, true)) {
                    throw new IOException("Config save target is not a regular file: " + target);
                }
                String backupName = "original-" + backupIndex++ + ".bin";
                Path backup = directory.resolve(backupName);
                fileSystem.writeDurable(backup, backup, fileSystem.read(target));
                entries.add(new Entry(relative, Kind.REGULAR, backupName, null, null));
            }
            writeJournal(fileSystem, directory.resolve(JOURNAL), entries, false);
            fileSystem.fsync(directory);
            fileSystem.fsync(internalDirectory);
            ConfigSaveBatch batch = new ConfigSaveBatch(fileSystem, configDirectory, directory,
                    entries, activeChannel, activeLock);
            CURRENT.set(batch);
            return batch;
        } catch (IOException | RuntimeException failure) {
            close(activeLock, activeChannel, failure);
            cleanup(fileSystem, directory, failure);
            if (failure instanceof ConfigSaveException saveFailure) {
                throw saveFailure;
            }
            throw new ConfigSaveException("Failed to prepare config save batch", failure);
        }
    }

    void commit() {
        if (finished) {
            return;
        }
        try {
            writeFinalizedMarker(fileSystem, directory, COMMITTED);
            finished = true;
            CURRENT.remove();
            runAfterCommitActions();
        } catch (IOException failure) {
            throw new ConfigSaveException("Failed to commit config save batch", failure);
        } finally {
            if (finished) {
                close(activeLock, activeChannel, null);
            }
        }
        cleanupAfterCompletion();
    }

    void rollback(Throwable originalFailure) {
        rollback(originalFailure, List.of());
    }

    void rollback(Throwable originalFailure, List<Path> preservedTargets) {
        if (finished) {
            return;
        }
        Set<Path> preserved = preservedTargets.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .collect(Collectors.toUnmodifiableSet());
        CURRENT.remove();
        List<Entry> rollbackEntries = entries.stream()
                .filter(entry -> {
                    Path target = resolveTarget(configDirectory, entry.relativeTarget());
                    return mutatedTargets.contains(target) && !preserved.contains(target);
                })
                .toList();
        IOException rollbackFailure;
        try {
            writeJournal(fileSystem, directory.resolve(ROLLBACK_JOURNAL), rollbackEntries, false);
            fileSystem.fsync(directory);
            rollbackFailure = restoreEntries(
                    fileSystem, configDirectory, directory, rollbackEntries);
        } catch (IOException journalFailure) {
            rollbackFailure = journalFailure;
        }
        if (rollbackFailure == null) {
            try {
                writeFinalizedMarker(fileSystem, directory, ROLLED_BACK);
            } catch (IOException markerFailure) {
                rollbackFailure = markerFailure;
            }
        }
        finished = rollbackFailure == null;
        close(activeLock, activeChannel, rollbackFailure == null ? originalFailure : rollbackFailure);
        if (rollbackFailure == null) {
            cleanupAfterCompletion();
        } else {
            originalFailure.addSuppressed(rollbackFailure);
            LOGGER.error("Could not completely roll back config save batch {}; startup will retry",
                    directory, rollbackFailure);
        }
    }

    static boolean recoverIncomplete(ConfigFileSystem fileSystem) {
        Path configDirectory = Config.getConfigDirectory(fileSystem).toAbsolutePath().normalize();
        Path internalDirectory = configDirectory.resolve(".internal");
        if (!fileSystem.isDirectory(internalDirectory)) {
            return true;
        }
        boolean recovered = true;
        try {
            for (Path directory : fileSystem.list(internalDirectory)) {
                String name = directory.getFileName().toString();
                if (!name.startsWith(DIRECTORY_PREFIX)
                        || fileSystem.isSymbolicLink(directory)
                        || !fileSystem.isDirectory(directory)
                        || !directory.toAbsolutePath().normalize().startsWith(internalDirectory)) {
                    continue;
                }
                recovered &= recoverDirectory(fileSystem, configDirectory, directory);
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not inspect {} for interrupted config save batches", internalDirectory, failure);
            return false;
        }
        return recovered;
    }

    void abandonForTests() {
        if (!finished) {
            finished = true;
            CURRENT.remove();
            close(activeLock, activeChannel, null);
        }
    }

    static void targetMutated(Path target) {
        ConfigSaveBatch batch = CURRENT.get();
        if (batch == null) {
            return;
        }
        Path normalized = target.toAbsolutePath().normalize();
        for (Entry entry : batch.entries) {
            if (resolveTarget(batch.configDirectory, entry.relativeTarget()).equals(normalized)) {
                batch.mutatedTargets.add(normalized);
                return;
            }
        }
    }

    static boolean isActive() {
        return CURRENT.get() != null;
    }

    static Path stageDelete(ConfigFileSystem fileSystem, Path target) throws IOException {
        ConfigSaveBatch batch = CURRENT.get();
        if (batch == null || batch.fileSystem != fileSystem) {
            throw new IOException("Profile deletion requires an active config save batch");
        }
        Path normalized = target.toAbsolutePath().normalize();
        boolean recorded = batch.entries.stream()
                .map(entry -> resolveTarget(batch.configDirectory, entry.relativeTarget()))
                .anyMatch(normalized::equals);
        if (!recorded) {
            throw new IOException("Config save batch does not cover deletion target " + target);
        }

        Path staged = batch.directory.resolve("deleted-" + batch.stagedDeleteIndex++ + ".bin");
        for (int i = 0; i < batch.entries.size(); i++) {
            Entry entry = batch.entries.get(i);
            if (resolveTarget(batch.configDirectory, entry.relativeTarget()).equals(normalized)) {
                batch.entries.set(i, new Entry(entry.relativeTarget(), entry.kind(), entry.backup(),
                        entry.linkTarget(), staged.getFileName().toString()));
                break;
            }
        }
        writeJournal(fileSystem, batch.directory.resolve(JOURNAL), batch.entries, true);
        boolean moved = false;
        try {
            moveAtomic(fileSystem, target, staged);
            moved = true;
            batch.mutatedTargets.add(normalized);
            fileSystem.fsync(target.getParent());
            fileSystem.fsync(batch.directory);
            return staged;
        } catch (IOException failure) {
            if (moved) {
                try {
                    restoreStagedDelete(fileSystem, staged, target);
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
    }

    static void restoreStagedDelete(
            ConfigFileSystem fileSystem, Path staged, Path target) throws IOException {
        ConfigSaveBatch batch = CURRENT.get();
        if (batch == null || batch.fileSystem != fileSystem) {
            throw new IOException("Profile deletion restore requires an active config save batch");
        }
        restoreDeletedFile(fileSystem, staged, target);
        batch.mutatedTargets.remove(target.toAbsolutePath().normalize());
    }

    private static void restoreDeletedFile(
            ConfigFileSystem fileSystem, Path staged, Path target) throws IOException {
        if (fileSystem.isSymbolicLink(staged)) {
            Path linkTarget = fileSystem.readSymbolicLink(staged);
            if (!fileSystem.isSymbolicLink(target)
                    || !fileSystem.readSymbolicLink(target).equals(linkTarget)) {
                fileSystem.createSymbolicLink(target, linkTarget);
            }
        } else if (!fileSystem.isRegularFile(target, true)
                || !Arrays.equals(fileSystem.read(staged), fileSystem.read(target))) {
            fileSystem.createHardLink(target, staged);
        }
        fileSystem.fsync(target.getParent());
        fileSystem.delete(staged);
        fileSystem.fsync(staged.getParent());
    }

    static void afterCommit(Runnable action) {
        ConfigSaveBatch batch = CURRENT.get();
        if (batch == null) {
            action.run();
        } else {
            batch.afterCommitActions.add(action);
        }
    }

    private void runAfterCommitActions() {
        for (Runnable action : afterCommitActions) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not complete post-commit config maintenance", failure);
            }
        }
        afterCommitActions.clear();
    }

    private static void moveAtomic(
            ConfigFileSystem fileSystem, Path source, Path target) throws IOException {
        try {
            fileSystem.moveAtomic(source, target);
        } catch (AtomicMoveNotSupportedException ignored) {
            fileSystem.move(source, target);
        }
    }

    private static boolean recoverDirectory(
            ConfigFileSystem fileSystem, Path configDirectory, Path directory) {
        FileChannel channel = null;
        FileLock lock = null;
        try {
            channel = FileChannel.open(directory.resolve(ACTIVE_LOCK),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException activeHere) {
                return false;
            }
            if (lock == null) {
                return false;
            }
            Path finalized = directory.resolve(FINALIZED);
            if (fileSystem.exists(finalized, true)
                    && validFinalizedMarker(fileSystem.read(finalized))) {
                close(lock, channel, null);
                lock = null;
                channel = null;
                cleanup(fileSystem, directory, null);
                return true;
            }
            if (!fileSystem.exists(directory.resolve(JOURNAL), true)) {
                LOGGER.warn("Removing config save batch {} that was interrupted before it recorded anything",
                        directory);
                close(lock, channel, null);
                lock = null;
                channel = null;
                cleanup(fileSystem, directory, null);
                return true;
            }
            Path rollbackJournal = directory.resolve(ROLLBACK_JOURNAL);
            Path journal = fileSystem.exists(rollbackJournal, true)
                    ? rollbackJournal : directory.resolve(JOURNAL);
            List<Entry> entries = readJournal(fileSystem, configDirectory, directory, journal);
            IOException failure = restoreEntries(
                    fileSystem, configDirectory, directory, entries);
            if (failure != null) {
                throw failure;
            }
            writeFinalizedMarker(fileSystem, directory, ROLLED_BACK);
            LOGGER.warn("Recovered interrupted config save batch {}", directory);
            close(lock, channel, null);
            lock = null;
            channel = null;
            cleanup(fileSystem, directory, null);
            return true;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not recover interrupted config save batch {}; preserving it for retry",
                    directory, failure);
            return false;
        } finally {
            close(lock, channel, null);
        }
    }

    private void cleanupAfterCompletion() {
        cleanup(fileSystem, directory, null);
    }

    private static IOException restoreEntries(ConfigFileSystem fileSystem, Path configDirectory,
            Path directory, List<Entry> entries) {
        IOException firstFailure = null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            Path target = resolveTarget(configDirectory, entry.relativeTarget());
            try {
                restoreEntry(fileSystem, directory, target, entry);
            } catch (IOException failure) {
                if (firstFailure == null) {
                    firstFailure = new IOException("Failed to restore config save target " + target, failure);
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        return firstFailure;
    }

    private static void restoreEntry(ConfigFileSystem fileSystem, Path directory,
            Path target, Entry entry) throws IOException {
        if (entry.stagedDelete() != null) {
            Path staged = resolveStagedDelete(directory, entry.stagedDelete());
            if (fileSystem.exists(staged, true)) {
                restoreDeletedFile(fileSystem, staged, target);
            }
            return;
        }
        switch (entry.kind()) {
            case MISSING -> {
                if (fileSystem.exists(target, true)) {
                    fileSystem.delete(target);
                    fileSystem.fsync(target.getParent());
                }
            }
            case SYMLINK -> {
                Path linkTarget = Path.of(entry.linkTarget());
                if (fileSystem.isSymbolicLink(target)
                        && fileSystem.readSymbolicLink(target).equals(linkTarget)) {
                    return;
                }
                if (fileSystem.exists(target, true)) {
                    fileSystem.delete(target);
                }
                fileSystem.createDirectories(target.getParent());
                fileSystem.createSymbolicLink(target, linkTarget);
                fileSystem.fsync(target.getParent());
            }
            case REGULAR -> {
                Path backup = resolveBackup(directory, entry.backup());
                byte[] original = fileSystem.read(backup);
                if (fileSystem.isRegularFile(target, true)
                        && Arrays.equals(original, fileSystem.read(target))) {
                    return;
                }
                fileSystem.writeAtomic(target, original);
            }
        }
    }

    private static void writeJournal(
            ConfigFileSystem fileSystem, Path path, List<Entry> entries, boolean replace) throws IOException {
        JsonObject journal = new JsonObject();
        journal.addProperty("formatVersion", FORMAT_VERSION);
        JsonArray serializedEntries = new JsonArray();
        for (Entry entry : entries) {
            JsonObject serialized = new JsonObject();
            serialized.addProperty("target", entry.relativeTarget());
            serialized.addProperty("kind", entry.kind().name());
            if (entry.backup() != null) {
                serialized.addProperty("backup", entry.backup());
            }
            if (entry.linkTarget() != null) {
                serialized.addProperty("linkTarget", entry.linkTarget());
            }
            if (entry.stagedDelete() != null) {
                serialized.addProperty("stagedDelete", entry.stagedDelete());
            }
            serializedEntries.add(serialized);
        }
        journal.add("entries", serializedEntries);
        byte[] contents = ConfigFiles.jsonBytes(journal);
        if (replace) {
            fileSystem.writeAtomic(path, contents);
        } else {
            fileSystem.writeDurable(path, path, contents);
        }
    }

    private static void writeFinalizedMarker(
            ConfigFileSystem fileSystem, Path directory, byte[] contents) throws IOException {
        Path marker = directory.resolve(FINALIZED);
        fileSystem.writeAtomic(marker, contents);
        fileSystem.fsync(directory);
    }

    private static boolean validFinalizedMarker(byte[] contents) {
        return Arrays.equals(contents, COMMITTED) || Arrays.equals(contents, ROLLED_BACK);
    }

    private static List<Entry> readJournal(
            ConfigFileSystem fileSystem, Path configDirectory, Path directory, Path journalPath) throws IOException {
        JsonObject journal;
        try {
            journal = ConfigJsonFiles.parse(fileSystem.read(journalPath), JsonObject.class);
            int version = journal == null ? 0 : journal.get("formatVersion").getAsInt();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported config save batch journal");
            }
            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : journal.getAsJsonArray("entries")) {
                JsonObject serialized = element.getAsJsonObject();
                Entry entry = new Entry(
                        serialized.get("target").getAsString(),
                        Kind.valueOf(serialized.get("kind").getAsString()),
                        optionalString(serialized, "backup"),
                        optionalString(serialized, "linkTarget"),
                        optionalString(serialized, "stagedDelete"));
                resolveTarget(configDirectory, entry.relativeTarget());
                if (entry.kind() == Kind.REGULAR) {
                    resolveBackup(directory, entry.backup());
                }
                if (entry.stagedDelete() != null) {
                    resolveStagedDelete(directory, entry.stagedDelete());
                }
                entries.add(entry);
            }
            return List.copyOf(entries);
        } catch (RuntimeException failure) {
            throw new IOException("Malformed config save batch journal " + journalPath, failure);
        }
    }

    private static String optionalString(JsonObject object, String field) {
        return object.has(field) ? object.get(field).getAsString() : null;
    }

    private static Path resolveTarget(Path configDirectory, String relative) {
        Path relativePath = Path.of(relative);
        Path target = configDirectory.resolve(relativePath).toAbsolutePath().normalize();
        if (relativePath.isAbsolute() || !target.startsWith(configDirectory) || target.equals(configDirectory)) {
            throw new IllegalArgumentException("Config save batch target escapes config directory: " + relative);
        }
        return target;
    }

    private static Path resolveStagedDelete(Path directory, String name) {
        if (!name.matches("deleted-\\d+\\.bin")) {
            throw new IllegalArgumentException("Invalid staged deletion name");
        }
        return directory.resolve(name);
    }

    private static Path resolveBackup(Path directory, String backupName) {
        if (backupName == null || !backupName.matches("original-\\d+\\.bin")) {
            throw new IllegalArgumentException("Missing config save batch backup name");
        }
        Path backup = directory.resolve(backupName).toAbsolutePath().normalize();
        if (!backup.startsWith(directory.toAbsolutePath().normalize()) || backup.equals(directory)) {
            throw new IllegalArgumentException("Config save batch backup escapes its directory: " + backupName);
        }
        return backup;
    }

    private static void cleanup(ConfigFileSystem fileSystem, Path directory, Throwable owner) {
        if (directory == null || !fileSystem.isDirectory(directory) || fileSystem.isSymbolicLink(directory)) {
            return;
        }
        try {
            Path finalized = directory.resolve(FINALIZED);
            for (Path child : fileSystem.list(directory)) {
                if (child.equals(finalized)) {
                    continue;
                }
                if (fileSystem.isDirectory(child) && !fileSystem.isSymbolicLink(child)) {
                    throw new IOException("Unexpected directory in config save batch: " + child);
                }
                fileSystem.delete(child);
            }
            if (fileSystem.exists(finalized, true)) {
                fileSystem.delete(finalized);
            }
            fileSystem.delete(directory);
            fileSystem.fsync(directory.getParent());
        } catch (IOException failure) {
            if (owner != null) {
                owner.addSuppressed(failure);
            } else {
                LOGGER.warn("Could not clean completed config save batch {}", directory, failure);
            }
        }
    }

    private static void close(FileLock lock, FileChannel channel, Throwable owner) {
        IOException failure = null;
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException closeFailure) {
                failure = closeFailure;
            }
        }
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure != null) {
            if (owner != null) {
                owner.addSuppressed(failure);
            } else {
                LOGGER.warn("Could not close config save batch lock", failure);
            }
        }
    }

    private enum Kind {
        MISSING,
        REGULAR,
        SYMLINK
    }

    private record Entry(String relativeTarget, Kind kind, String backup, String linkTarget,
            String stagedDelete) {
    }
}
