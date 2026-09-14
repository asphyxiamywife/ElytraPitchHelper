package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.SectionCollapseSettings;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ProfileEditorPlan {
    private ProfileEditorPlan() {}

    static List<RowPlan> plan(Profile profile, String query, SectionCollapseSettings collapse,
            Function<String, String> translator) {
        return plan(profile, query, collapse, translator, null);
    }

    static List<RowPlan> plan(Profile profile, String query, SectionCollapseSettings collapse,
            Function<String, String> translator, SectionAnimation animation) {
        boolean searching = SettingsSearch.isActive(query);
        List<RowPlan> plan = new ArrayList<>();
        for (ConfigCategory section : ConfigCategory.values()) {
            List<SettingSpec<?>> specs = SettingsRegistry.visible(section, profile);
            if (specs.isEmpty()) {
                continue;
            }
            Map<String, MatchKind> kinds = classify(specs, query, searching, translator);
            if (kinds.isEmpty()) {
                continue;
            }
            plan.add(new RowPlan(section, null, MatchKind.DIRECT));
            boolean showsChildren = expanded(section, query, collapse)
                    || (animation != null && animation.showsChildren(section));
            if (!showsChildren) {
                continue;
            }
            for (SettingSpec<?> spec : specs) {
                MatchKind kind = kinds.get(spec.id());
                if (kind != null) {
                    plan.add(new RowPlan(section, spec.id(), kind));
                }
            }
        }
        return plan;
    }

    private static Map<String, MatchKind> classify(List<SettingSpec<?>> specs, String query,
            boolean searching, Function<String, String> translator) {
        Map<String, MatchKind> kinds = new LinkedHashMap<>();
        if (!searching) {
            for (SettingSpec<?> spec : specs) {
                kinds.put(spec.id(), MatchKind.DIRECT);
            }
            return kinds;
        }
        for (SettingSpec<?> spec : specs) {
            SettingsSearch.Entry entry = new SettingsSearch.Entry(spec);
            if (SettingsSearch.matchesLabel(entry, query, translator)) {
                kinds.put(spec.id(), MatchKind.DIRECT);
            } else if (SettingsSearch.matches(entry, query, translator)) {
                kinds.put(spec.id(), MatchKind.CONTEXT);
            }
        }
        for (String id : List.copyOf(kinds.keySet())) {
            SettingSpec<?> spec = SettingsRegistry.byId(id);
            while (spec != null && spec.parent() != null) {
                spec = SettingsRegistry.byId(spec.parent());
                if (spec == null || kinds.containsKey(spec.id())) {
                    break;
                }
                kinds.put(spec.id(), MatchKind.CONTEXT);
            }
        }
        return kinds;
    }

    static int rowCount(Profile profile, String query, SectionCollapseSettings collapse,
            Function<String, String> translator, SectionAnimation animation) {
        return plan(profile, query, collapse, translator, animation).size();
    }

    static int rowCount(Profile profile, String query, SectionCollapseSettings collapse,
            Function<String, String> translator) {
        return rowCount(profile, query, collapse, translator, null);
    }

    static int contentHeight(Profile profile, String query, SectionCollapseSettings collapse,
            Function<String, String> translator, SectionAnimation animation, int rowHeight) {
        boolean searching = SettingsSearch.isActive(query);
        double height = 0.0;
        for (RowPlan planned : plan(profile, query, collapse, translator, animation)) {
            if (planned.settingId() == null) {
                height += rowHeight;
                continue;
            }
            float progress = searching || animation == null
                    ? 1.0f
                    : animation.progress(planned.section());
            height += rowHeight * SectionAnimation.heightFactor(progress);
        }
        return (int) Math.ceil(height);
    }

    static int headerRowIndex(Profile profile, String query, SectionCollapseSettings collapse,
            ConfigCategory target, Function<String, String> translator) {
        List<RowPlan> plan = plan(profile, query, collapse, translator);
        for (int index = 0; index < plan.size(); index++) {
            RowPlan row = plan.get(index);
            if (row.settingId() == null && row.section() == target) {
                return index;
            }
        }
        return -1;
    }

    private static boolean expanded(ConfigCategory section, String query, SectionCollapseSettings collapse) {
        return SettingsSearch.isActive(query) || !collapse.isCollapsed(section);
    }

    enum MatchKind {
        DIRECT,
        CONTEXT
    }

    record RowPlan(ConfigCategory section, String settingId, MatchKind kind) {
    }

}
