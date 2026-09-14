package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

final class EditorSearchController {
    private final Supplier<Config> config;
    private final SectionAnimation sectionAnimation;
    private final ToIntFunction<ConfigCategory> rowCount;
    private final Runnable savePreference;
    private ConfigCategory category = ConfigCategory.GENERAL;
    private String query = "";
    private SettingsSearch.Entry pendingSetting;
    private ConfigCategory pendingSection;
    private EditBox settingsSearchBox;
    private boolean suppressSearchShortcutCharacter;

    EditorSearchController(Supplier<Config> config, SectionAnimation sectionAnimation,
            ToIntFunction<ConfigCategory> rowCount, Runnable savePreference) {
        this.config = config;
        this.sectionAnimation = sectionAnimation;
        this.rowCount = rowCount;
        this.savePreference = savePreference;
    }

    ConfigCategory category() {
        return category;
    }

    void setCategory(ConfigCategory category) {
        this.category = category;
    }

    String query() {
        return query;
    }

    void setQuery(String query) {
        this.query = query == null ? "" : query;
    }

    EditBox searchBox() {
        return settingsSearchBox;
    }

    void setSearchBox(EditBox searchBox) {
        settingsSearchBox = searchBox;
    }

    void setPendingSetting(SettingsSearch.Entry entry) {
        pendingSetting = entry;
    }

    boolean isPendingSetting(SettingsSearch.Entry entry) {
        return pendingSetting != null && pendingSetting.equals(entry);
    }

    ConfigCategory takePendingSection() {
        ConfigCategory section = pendingSection;
        pendingSection = null;
        return section;
    }

    void widgetsRebuilt() {
        settingsSearchBox = null;
        suppressSearchShortcutCharacter = false;
    }

    boolean consumeShortcutCharacter(int codepoint) {
        boolean suppress = suppressSearchShortcutCharacter;
        suppressSearchShortcutCharacter = false;
        return suppress && codepoint == '/';
    }

    void jumpToCategory(ConfigCategory category) {
        this.category = category;
        revealSection(category);
        pendingSection = category;
        pendingSetting = null;
        query = "";
    }

    boolean jumpToSetting(SettingsSearch.Entry entry) {
        category = entry.category();
        revealSection(category);
        boolean opensScreen = entry.paletteOpenMode() == SettingsSearch.PaletteOpenMode.OPEN_SCREEN;
        pendingSetting = opensScreen ? null : entry;
        query = "";
        return opensScreen;
    }

    void revealSection(ConfigCategory section) {
        if (!config.get().sectionCollapse.isCollapsed(section)) {
            return;
        }

        config.get().sectionCollapse = config.get().sectionCollapse.toggled(section);
        sectionAnimation.setExpanded(section, true, rowCount.applyAsInt(section));
        savePreference.run();
    }

    boolean handleShortcut(KeyEvent event, GuiEventListener focused,
            java.util.function.Consumer<GuiEventListener> focus) {
        if (settingsSearchBox == null) {
            return false;
        }

        int modifiers = event.modifiers();
        boolean findShortcut = ScreenShortcuts.isPrimary(event) && event.key() == GLFW.GLFW_KEY_F;
        boolean slashShortcut = event.key() == GLFW.GLFW_KEY_SLASH
                && (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER | GLFW.GLFW_MOD_ALT)) == 0
                && focused != settingsSearchBox;
        if (!findShortcut && !slashShortcut) {
            return false;
        }

        focus.accept(settingsSearchBox);
        settingsSearchBox.setFocused(true);
        settingsSearchBox.moveCursorToEnd(false);
        settingsSearchBox.setHighlightPos(0);
        suppressSearchShortcutCharacter = slashShortcut;
        return true;
    }

}
