package com.asphyxiamywife.elytrapitchhelper.config;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ConfigDraftMerge {
    private ConfigDraftMerge() {}

    static ConfigDocument merge(ConfigDocument base, ConfigDocument draft, ConfigDocument current) {
        return merge(base, draft, current, false);
    }

    static ConfigDocument mergeHistory(ConfigDocument base, ConfigDocument draft, ConfigDocument current) {
        return merge(base, draft, current, true);
    }

    private static ConfigDocument merge(ConfigDocument base, ConfigDocument draft, ConfigDocument current,
            boolean preserveUnchangedDeletions) {
        var folded = new LinkedHashSet<>(current.sectionCollapse().collapsed());
        for (String key : base.sectionCollapse().collapsed()) {
            if (!draft.sectionCollapse().collapsed().contains(key)) folded.remove(key);
        }
        for (String key : draft.sectionCollapse().collapsed()) {
            if (!base.sectionCollapse().collapsed().contains(key)) folded.add(key);
        }
        List<Profile> profiles = mergeProfiles(base.profiles(), draft.profiles(), current.profiles(), preserveUnchangedDeletions);
        String selected = changed(base.activeProfileFile(), draft.activeProfileFile(), current.activeProfileFile());
        int index = changed(base.activeProfileIndex(), draft.activeProfileIndex(), current.activeProfileIndex());
        for (int i = 0; i < profiles.size(); i++) {
            if (Objects.equals(key(profiles.get(i)), ProfileFileNames.comparisonKey(selected))) {
                index = i;
                break;
            }
        }
        return new ConfigDocument(
                changed(base.version(), draft.version(), current.version()),
                changed(base.enabled(), draft.enabled(), current.enabled()), index, selected,
                changed(base.profileSortMode(), draft.profileSortMode(), current.profileSortMode()),
                new CommandPaletteUsageSettings(mergeMap(base.commandPaletteUsage().actions(),
                        draft.commandPaletteUsage().actions(), current.commandPaletteUsage().actions())),
                base.sectionCollapse().equals(draft.sectionCollapse())
                        ? current.sectionCollapse() : new SectionCollapseSettings(folded),
                changed(base.usedSettingsSearch(), draft.usedSettingsSearch(), current.usedSettingsSearch()), profiles);
    }

    private static List<Profile> mergeProfiles(List<Profile> base, List<Profile> draft, List<Profile> current,
            boolean preserveUnchangedDeletions) {
        Map<String, Profile> before = byFile(base);
        Map<String, Profile> ours = byFile(draft);
        Map<String, Profile> result = byFile(current);
        for (String file : before.keySet()) {
            if (!ours.containsKey(file)) result.remove(file);
        }
        for (Profile profile : draft) {
            String file = key(profile);
            Profile original = before.get(file);
            Profile latest = result.get(file);
            if (preserveUnchangedDeletions && latest == null && profile.equals(original)) {
                continue;
            }
            if (original == null || latest == null) {
                result.put(file, profile);
            } else {
                Profile merged = latest.withName(changed(original.name(), profile.name(), latest.name()))
                        .withVersion(changed(original.version(), profile.version(), latest.version()));
                for (SettingSpec<?> spec : SettingsRegistry.serialized()) {
                    merged = mergeSetting(spec, original, profile, merged);
                }
                merged = merged.withVoidWarning(merged.voidWarning().withDimensionYOverrides(
                        mergeMap(original.voidWarning().dimensionYOverrides(),
                                profile.voidWarning().dimensionYOverrides(), latest.voidWarning().dimensionYOverrides())));
                result.put(file, merged);
            }
        }
        List<String> oldOrder = base.stream().map(ConfigDraftMerge::key).filter(ours::containsKey).toList();
        List<String> newOrder = draft.stream().map(ConfigDraftMerge::key).filter(before::containsKey).toList();
        if (oldOrder.equals(newOrder)) return new ArrayList<>(result.values());
        List<Profile> ordered = new ArrayList<>();
        for (Profile profile : draft) {
            Profile merged = result.remove(key(profile));
            if (merged != null) ordered.add(merged);
        }
        ordered.addAll(result.values());
        return ordered;
    }

    private static Map<String, Profile> byFile(List<Profile> profiles) {
        Map<String, Profile> result = new LinkedHashMap<>();
        for (Profile profile : profiles) result.put(key(profile), profile);
        return result;
    }

    private static String key(Profile profile) {
        return ProfileFileNames.comparisonKey(profile.fileName());
    }

    private static <T> Profile mergeSetting(SettingSpec<T> spec, Profile base, Profile draft, Profile current) {
        T before = spec.get().apply(base);
        T value = spec.get().apply(draft);
        return Objects.deepEquals(before, value) ? current : spec.apply(current, value);
    }

    private static <T> T changed(T base, T draft, T current) {
        return Objects.deepEquals(base, draft) ? current : draft;
    }

    private static <K, V> Map<K, V> mergeMap(Map<K, V> base, Map<K, V> draft, Map<K, V> current) {
        Map<K, V> result = new LinkedHashMap<>(current);
        for (K key : base.keySet()) {
            if (!draft.containsKey(key)) result.remove(key);
        }
        for (var entry : draft.entrySet()) {
            if (!base.containsKey(entry.getKey()) || !Objects.deepEquals(base.get(entry.getKey()), entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
