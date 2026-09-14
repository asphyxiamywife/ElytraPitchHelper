package com.asphyxiamywife.elytrapitchhelper.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConfigConflictDiffTest {
    @Test
    void marksDiskAndInGameSidesOfAReplacement() {
        List<ConfigConflictDiff.Line> diff = ConfigConflictDiff.unified(
                List.of("{", "  enabled: false", "}"),
                List.of("{", "  enabled: true", "}"));

        assertEquals(List.of(
                new ConfigConflictDiff.Line(ConfigConflictDiff.Kind.CONTEXT, "{"),
                new ConfigConflictDiff.Line(ConfigConflictDiff.Kind.DISK, "  enabled: false"),
                new ConfigConflictDiff.Line(ConfigConflictDiff.Kind.MINE, "  enabled: true"),
                new ConfigConflictDiff.Line(ConfigConflictDiff.Kind.CONTEXT, "}")), diff);
    }

    @Test
    void collapsesLongRunsOfUnchangedJson() {
        List<ConfigConflictDiff.Line> diff = ConfigConflictDiff.unified(
                List.of("0", "1", "2", "3", "4", "5", "disk", "7"),
                List.of("0", "1", "2", "3", "4", "5", "mine", "7"));

        assertEquals(ConfigConflictDiff.Kind.OMITTED, diff.get(2).kind());
        assertEquals("2", diff.get(2).text());
        assertEquals(ConfigConflictDiff.Kind.DISK, diff.get(5).kind());
        assertEquals(ConfigConflictDiff.Kind.MINE, diff.get(6).kind());
    }

    @Test
    void representsAnInGameDeletionAsDiskOnlyLines() {
        List<ConfigConflictDiff.Line> diff = ConfigConflictDiff.unified(
                List.of("{", "  value: 1", "}"), List.of());

        assertEquals(List.of(
                ConfigConflictDiff.Kind.DISK,
                ConfigConflictDiff.Kind.DISK,
                ConfigConflictDiff.Kind.DISK),
                diff.stream().map(ConfigConflictDiff.Line::kind).toList());
    }

    @Test
    void largeDiffUsesBoundedReplacementFallbackAndPreservesCommonEdges() {
        List<String> disk = largeDocument("disk", 400);
        List<String> mine = largeDocument("mine", 700);

        List<ConfigConflictDiff.Line> diff = ConfigConflictDiff.unified(disk, mine);

        assertEquals(ConfigConflictDiff.Kind.CONTEXT, diff.get(0).kind());
        assertEquals("start", diff.get(0).text());
        assertEquals(1_100, diff.stream().filter(line -> line.kind() == ConfigConflictDiff.Kind.DISK).count());
        assertEquals(1_100, diff.stream().filter(line -> line.kind() == ConfigConflictDiff.Kind.MINE).count());
        assertEquals(2, diff.stream().filter(line -> line.kind() == ConfigConflictDiff.Kind.CONTEXT).count(),
                "the bounded fallback treats the changed middle as a replacement");
        assertEquals(ConfigConflictDiff.Kind.CONTEXT, diff.get(diff.size() - 1).kind());
        assertEquals("end", diff.get(diff.size() - 1).text());
    }

    private static List<String> largeDocument(String side, int sharedMiddleIndex) {
        return IntStream.range(0, 1_102)
                .mapToObj(index -> index == 0 ? "start"
                        : index == 1_101 ? "end"
                        : index == sharedMiddleIndex ? "shared-middle"
                        : side + index)
                .toList();
    }
}
