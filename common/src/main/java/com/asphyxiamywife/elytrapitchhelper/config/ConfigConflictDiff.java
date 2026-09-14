package com.asphyxiamywife.elytrapitchhelper.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConfigConflictDiff {
    private static final int CONTEXT_LINES = 2;
    private static final long MAX_LCS_CELLS = 1_000_000L;

    private ConfigConflictDiff() {
    }

    public static List<FileDiff> capture(ConfigFileSystem fileSystem, Config config,
            List<Path> paths, Set<String> deletedProfileFiles) {
        return paths.stream()
                .map(path -> captureFile(fileSystem, config, path, deletedProfileFiles))
                .toList();
    }

    private static FileDiff captureFile(ConfigFileSystem fileSystem, Config config, Path path,
            Set<String> deletedProfileFiles) {
        String fileName = path.getFileName().toString();
        try {
            List<String> disk = fileSystem.exists(path, false)
                    ? lines(new String(fileSystem.read(path), StandardCharsets.UTF_8))
                    : List.of();
            List<String> mine = intendedContents(fileSystem, config, path, fileName,
                    deletedProfileFiles);
            return new FileDiff(fileName, unified(disk, mine), null);
        } catch (IOException | RuntimeException failure) {
            String message = failure.getMessage();
            return new FileDiff(fileName, List.of(),
                    message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
        }
    }

    private static List<String> intendedContents(ConfigFileSystem fileSystem, Config config,
            Path path, String fileName, Set<String> deletedProfileFiles) {
        if (path.equals(Config.getConfigPath(fileSystem))) {
            return lines(ConfigFiles.GSON.toJson(config));
        }
        if (deletedProfileFiles.stream()
                .anyMatch(deleted -> Objects.equals(ProfileFileNames.comparisonKey(deleted),
                        ProfileFileNames.comparisonKey(fileName)))) {
            return List.of();
        }
        int profileIndex = config.profileIndexByFileName(fileName);
        if (profileIndex < 0) {
            return List.of();
        }
        return lines(ConfigFiles.GSON.toJson(config.profile(profileIndex)));
    }

    private static List<String> lines(String text) {
        return text.lines().toList();
    }

    static List<Line> unified(List<String> disk, List<String> mine) {
        long cells = ((long) disk.size() + 1L) * ((long) mine.size() + 1L);
        if (cells > MAX_LCS_CELLS) {
            return linearFallback(disk, mine);
        }

        int[][] suffix = new int[disk.size() + 1][mine.size() + 1];
        for (int diskIndex = disk.size() - 1; diskIndex >= 0; diskIndex--) {
            for (int mineIndex = mine.size() - 1; mineIndex >= 0; mineIndex--) {
                suffix[diskIndex][mineIndex] = disk.get(diskIndex).equals(mine.get(mineIndex))
                        ? suffix[diskIndex + 1][mineIndex + 1] + 1
                        : Math.max(suffix[diskIndex + 1][mineIndex], suffix[diskIndex][mineIndex + 1]);
            }
        }

        List<Line> complete = new ArrayList<>();
        int diskIndex = 0;
        int mineIndex = 0;
        while (diskIndex < disk.size() || mineIndex < mine.size()) {
            if (diskIndex < disk.size() && mineIndex < mine.size()
                    && disk.get(diskIndex).equals(mine.get(mineIndex))) {
                complete.add(new Line(Kind.CONTEXT, disk.get(diskIndex)));
                diskIndex++;
                mineIndex++;
            } else if (mineIndex < mine.size()
                    && (diskIndex == disk.size()
                    || suffix[diskIndex][mineIndex + 1] > suffix[diskIndex + 1][mineIndex])) {
                complete.add(new Line(Kind.MINE, mine.get(mineIndex++)));
            } else {
                complete.add(new Line(Kind.DISK, disk.get(diskIndex++)));
            }
        }
        return withCollapsedContext(complete);
    }

    private static List<Line> linearFallback(List<String> disk, List<String> mine) {
        int prefix = 0;
        int sharedLimit = Math.min(disk.size(), mine.size());
        while (prefix < sharedLimit && disk.get(prefix).equals(mine.get(prefix))) {
            prefix++;
        }

        int suffix = 0;
        int remainingDisk = disk.size() - prefix;
        int remainingMine = mine.size() - prefix;
        while (suffix < Math.min(remainingDisk, remainingMine)
                && disk.get(disk.size() - suffix - 1).equals(mine.get(mine.size() - suffix - 1))) {
            suffix++;
        }

        List<Line> complete = new ArrayList<>(disk.size() + mine.size());
        for (int i = 0; i < prefix; i++) {
            complete.add(new Line(Kind.CONTEXT, disk.get(i)));
        }
        for (int i = prefix; i < disk.size() - suffix; i++) {
            complete.add(new Line(Kind.DISK, disk.get(i)));
        }
        for (int i = prefix; i < mine.size() - suffix; i++) {
            complete.add(new Line(Kind.MINE, mine.get(i)));
        }
        for (int i = disk.size() - suffix; i < disk.size(); i++) {
            complete.add(new Line(Kind.CONTEXT, disk.get(i)));
        }
        return withCollapsedContext(complete);
    }

    private static List<Line> withCollapsedContext(List<Line> complete) {
        List<Line> visible = new ArrayList<>();
        int index = 0;
        while (index < complete.size()) {
            if (complete.get(index).kind() != Kind.CONTEXT) {
                visible.add(complete.get(index++));
                continue;
            }
            int runStart = index;
            while (index < complete.size() && complete.get(index).kind() == Kind.CONTEXT) {
                index++;
            }
            int runLength = index - runStart;
            int leading = Math.min(CONTEXT_LINES, runLength);
            int trailing = index == complete.size()
                    ? 0 : Math.min(CONTEXT_LINES, runLength - leading);
            for (int i = 0; i < leading; i++) {
                visible.add(complete.get(runStart + i));
            }
            int hidden = runLength - leading - trailing;
            if (hidden > 0) {
                visible.add(new Line(Kind.OMITTED, Integer.toString(hidden)));
            }
            for (int i = index - trailing; i < index; i++) {
                visible.add(complete.get(i));
            }
        }
        return List.copyOf(visible);
    }

    public enum Kind {
        CONTEXT, DISK, MINE, OMITTED
    }

    public record Line(Kind kind, String text) {
    }

    public record FileDiff(String fileName, List<Line> lines, String error) {
        public FileDiff {
            lines = List.copyOf(lines);
        }
    }
}
