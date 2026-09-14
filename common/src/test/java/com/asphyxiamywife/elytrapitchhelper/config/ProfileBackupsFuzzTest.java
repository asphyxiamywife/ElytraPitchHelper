package com.asphyxiamywife.elytrapitchhelper.config;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileBackupsFuzzTest {
    @FuzzTest(maxDuration = "30s")
    void backupNameOrderingIsTotalAndFormatAware(FuzzedDataProvider data) {
        String first = backupName(data);
        String second = backupName(data);
        String third = backupName(data);

        int firstSecond = sign(ProfileBackups.compareBackupFileNames(first, second));
        int secondFirst = sign(ProfileBackups.compareBackupFileNames(second, first));
        int secondThird = sign(ProfileBackups.compareBackupFileNames(second, third));
        int firstThird = sign(ProfileBackups.compareBackupFileNames(first, third));

        assertEquals(0, ProfileBackups.compareBackupFileNames(first, first));
        assertEquals(-firstSecond, secondFirst);
        if (firstSecond <= 0 && secondThird <= 0) {
            assertTrue(firstThird <= 0);
        }
        if (firstSecond >= 0 && secondThird >= 0) {
            assertTrue(firstThird >= 0);
        }

        String timestamp = timestamp(data);
        int lowerSequence = data.consumeInt(1, 999);
        int higherSequence = data.consumeInt(lowerSequence + 1, 1_000);
        String lower = sequencedName(timestamp, lowerSequence, data.consumeBoolean());
        String higher = sequencedName(timestamp, higherSequence, data.consumeBoolean());
        assertTrue(ProfileBackups.compareBackupFileNames(lower, higher) < 0);

        String nextTimestamp = timestamp.substring(0, timestamp.length() - 3) + "999";
        if (timestamp.compareTo(nextTimestamp) < 0) {
            assertTrue(ProfileBackups.compareBackupFileNames(
                    sequencedName(timestamp, 1_000, data.consumeBoolean()),
                    sequencedName(nextTimestamp, 1, data.consumeBoolean())) < 0);
        }
    }

    private static String backupName(FuzzedDataProvider data) {
        return switch (data.consumeInt(0, 2)) {
            case 0 -> data.consumeString(96);
            case 1 -> sequencedName(timestamp(data), data.consumeInt(1, 10_000), false);
            default -> sequencedName(timestamp(data), data.consumeInt(1, 10_000), true);
        };
    }

    private static String timestamp(FuzzedDataProvider data) {
        return String.format(Locale.ROOT, "%08d-%06d-%03d",
                data.consumeInt(0, 99_999_999),
                data.consumeInt(0, 999_999),
                data.consumeInt(0, 999));
    }

    private static String sequencedName(String timestamp, int sequence, boolean modern) {
        if (modern) {
            return ProfileBackups.backupFileName(timestamp, sequence);
        }
        return sequence == 1 ? timestamp + ".json" : timestamp + "-" + sequence + ".json";
    }

    private static int sign(int value) {
        return Integer.compare(value, 0);
    }
}
