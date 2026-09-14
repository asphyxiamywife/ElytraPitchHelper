package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.screen.ConfigCategory;

import java.util.LinkedHashSet;
import java.util.Set;

public record SectionCollapseSettings(Set<String> collapsed) {

    public SectionCollapseSettings() {
        this(defaultCollapsed());
    }

    public SectionCollapseSettings {
        collapsed = collapsed == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(collapsed));
    }

    private static Set<String> defaultCollapsed() {
        Set<String> names = new LinkedHashSet<>();
        for (ConfigCategory section : ConfigCategory.values()) {
            names.add(section.name());
        }
        return names;
    }

    public boolean isCollapsed(ConfigCategory section) {
        return collapsed.contains(section.name());
    }

    public SectionCollapseSettings toggled(ConfigCategory section) {
        Set<String> next = new LinkedHashSet<>(collapsed);
        if (!next.remove(section.name())) {
            next.add(section.name());
        }
        return new SectionCollapseSettings(next);
    }

    public SectionCollapseSettings expandedAll() {
        return new SectionCollapseSettings(Set.of());
    }

    public SectionCollapseSettings sanitized(RepairLog repairs) {
        Set<String> known = new LinkedHashSet<>();
        for (String name : collapsed) {
            if (isKnownSection(name)) {
                known.add(name);
            } else {
                ConfigRepair.repair(repairs, "sectionCollapse.collapsed." + name, name, "ignored");
            }
        }
        return known.size() == collapsed.size() ? this : new SectionCollapseSettings(known);
    }

    private static boolean isKnownSection(String name) {
        for (ConfigCategory section : ConfigCategory.values()) {
            if (section.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
