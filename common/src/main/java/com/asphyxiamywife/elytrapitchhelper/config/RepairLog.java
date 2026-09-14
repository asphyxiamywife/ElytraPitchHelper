package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.ModConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class RepairLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID);

    private final String source;
    private final List<String> repairs = new ArrayList<>();

    public RepairLog(String source) {
        this.source = escapeControls(source);
    }

    public void add(String repair) {
        repairs.add(escapeControls(repair));
    }

    public void log() {
        if (!repairs.isEmpty()) {
            LOGGER.warn("Repaired {}: {}", source, String.join("; ", repairs));
        }
    }

    static String escapeControls(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = null;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            int type = Character.getType(character);
            boolean unsafe = Character.isISOControl(character)
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR;
            if (!unsafe) {
                if (escaped != null) {
                    escaped.append(character);
                }
                continue;
            }
            if (escaped == null) {
                escaped = new StringBuilder(value.length() + 8);
                escaped.append(value, 0, i);
            }
            escaped.append("\\u");
            String hex = Integer.toHexString(character);
            for (int padding = hex.length(); padding < 4; padding++) {
                escaped.append('0');
            }
            escaped.append(hex);
        }
        return escaped == null ? value : escaped.toString();
    }
}
