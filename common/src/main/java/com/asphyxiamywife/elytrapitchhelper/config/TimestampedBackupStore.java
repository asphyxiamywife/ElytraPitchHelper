package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TimestampedBackupStore {
    private static final int MAX_COLLISION_RETRIES = 1_024;
    static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
            "^(\\d{8}-\\d{6}-\\d{3})(?:~(\\d+)|-(\\d+))?\\.json$");

    static final Comparator<Path> ORDER =
            (left, right) -> compareFileNames(left.getFileName().toString(), right.getFileName().toString());

    private final int maxBackups;

    TimestampedBackupStore(int maxBackups) {
        this.maxBackups = maxBackups;
    }
    void saveRawOrThrow(
            ConfigFileSystem fileSystem, Path directory, Path source) throws IOException {
        saveBytes(fileSystem, directory, fileSystem.read(source));
    }


    synchronized void saveBytes(ConfigFileSystem fileSystem, Path directory, byte[] contents) throws IOException {
        fileSystem.createDirectories(directory);
        Path backup = directory.resolve(nextFileName(fileSystem, directory, Instant.now()));
        ConfigFiles.writeBytesAtomic(fileSystem, backup, contents);
        rotate(fileSystem, directory);
    }

    String nextFileName(ConfigFileSystem fileSystem, Path directory, Instant now) throws IOException {
        String timestamp = TIMESTAMP_FORMAT.format(now);
        long sequence = 0L;
        for (Path existing : fileSystem.list(directory)) {
            if (!ConfigFiles.isJsonFile(fileSystem, existing)) {
                continue;
            }
            FileOrder order = fileOrder(existing.getFileName().toString());
            if (order == null) {
                continue;
            }
            int comparison = order.timestamp().compareTo(timestamp);
            if (comparison > 0) {
                timestamp = order.timestamp();
                sequence = order.sequence();
            } else if (comparison == 0) {
                sequence = Math.max(sequence, order.sequence());
            }
        }
        if (sequence == Long.MAX_VALUE) {
            throw new IOException("Backup sequence space is exhausted for " + timestamp);
        }
        sequence++;
        String candidate = fileName(timestamp, sequence);
        int collisions = 0;
        while (fileSystem.exists(directory.resolve(candidate), false)) {
            if (++collisions > MAX_COLLISION_RETRIES || sequence >= Long.MAX_VALUE - 1L) {
                throw new IOException("Could not allocate a unique backup name in " + directory);
            }
            candidate = fileName(timestamp, ++sequence);
        }
        return candidate;
    }


    synchronized void rotate(ConfigFileSystem fileSystem, Path directory) throws IOException {
        List<Path> backups = fileSystem.list(directory).stream()
                .filter(path -> ConfigFiles.isJsonFile(fileSystem, path))
                .sorted(ORDER)
                .toList();
        for (int i = 0; i < backups.size() - maxBackups; i++) {
            fileSystem.delete(backups.get(i));
        }
    }
    static Path latest(ConfigFileSystem fileSystem, Path directory) throws IOException {
        if (!fileSystem.isDirectory(directory)) {
            return null;
        }
        return fileSystem.list(directory).stream()
                .filter(path -> ConfigFiles.isJsonFile(fileSystem, path)).max(ORDER).orElse(null);
    }

    static String fileName(String timestamp, long sequence) {
        if (sequence < 1L) {
            throw new IllegalArgumentException("Backup sequence must be positive");
        }
        return timestamp + "~" + String.format(Locale.ROOT, "%06d", sequence)
                + ProfileFileNames.JSON_SUFFIX;
    }

    static int compareFileNames(String leftName, String rightName) {
        FileOrder leftOrder = fileOrder(leftName);
        FileOrder rightOrder = fileOrder(rightName);
        if (leftOrder == null || rightOrder == null) {
            if (leftOrder == null && rightOrder == null) {
                return leftName.compareTo(rightName);
            }
            return leftOrder == null ? -1 : 1;
        }
        int timestampComparison = leftOrder.timestamp().compareTo(rightOrder.timestamp());
        if (timestampComparison != 0) {
            return timestampComparison;
        }
        int sequenceComparison = Long.compare(leftOrder.sequence(), rightOrder.sequence());
        if (sequenceComparison != 0) {
            return sequenceComparison;
        }
        return leftName.compareTo(rightName);
    }

    static FileOrder fileOrder(String fileName) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        String sequenceText = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        try {
            long sequence = sequenceText == null ? 1L : Long.parseLong(sequenceText);
            return new FileOrder(matcher.group(1), sequence);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    record FileOrder(String timestamp, long sequence) {
    }
}
