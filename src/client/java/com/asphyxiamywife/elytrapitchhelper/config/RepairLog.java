package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

final class RepairLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private final String source;
    private final List<String> repairs = new ArrayList<>();

    RepairLog(String source) {
        this.source = source;
    }

    void add(String repair) {
        repairs.add(repair);
    }

    void log() {
        if (!repairs.isEmpty()) {
            LOGGER.warn("Repaired {}: {}", source, String.join("; ", repairs));
        }
    }
}
