package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.SectionCollapseSettings;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.screen.ProfileEditorPlan.MatchKind;
import com.asphyxiamywife.elytrapitchhelper.screen.ProfileEditorPlan.RowPlan;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.FlatButton;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SectionHeader;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.DropdownButton;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ProfileEditorPanel {
    private static final Function<String, String> TRANSLATOR = key -> Component.translatable(key).getString();
    private static final List<String> VOID_DIMENSION_IDS =
            List.of("void_dimension", "void_y_mode", "void_y_override");
    static final int SEARCH_TOP = 32;
    static final int HEADER_TOP = 6;
    private static final String ACTIVE_MARKER = "\u25CF ";
    private static final float CONTEXT_ALPHA = 0.55f;
    private static final double EDGE_FADE_FLOOR = 0.45;

    private final Host host;
    private final SettingBindings bindings;
    private final VoidDimensionControls voidControls;
    private final List<Row> rows = new ArrayList<>();
    private final List<Band> bands = new ArrayList<>();
    private final List<AbstractWidget> suppressed = new ArrayList<>();
    private Profile profile;
    private ProfileScrollBar scrollBar;
    private ConfigCategory pendingHeaderFocus;

    ProfileEditorPanel(Host host) {
        this.host = host;
        this.bindings = new SettingBindings(host, this::currentProfile, this::replaceProfile, this::syncFloatSliders);
        this.voidControls = new VoidDimensionControls(host, this::currentProfile, this::replaceProfile);
    }

    void init() {
        profile = host.config().profile(host.editingProfileIndex());
        buildRows();
        scrollPendingTargetIntoView();

        ProfileEditorLayout layout = host.editorLayout();
        host.setEditorScrollDirect(layout.scroll());

        addHeader(layout);
        addSearchBox(layout);
        scrollBar = ProfileScrollBar.forSettings(layout.scrollbarX(), layout.startY(),
                AbstractScrollArea.SCROLLBAR_WIDTH, layout.scrollbarHeight(),
                layout.contentHeight(), layout.scroll(), host::editorScrollbarDragging);
        host.addNavigationWidget(scrollBar);

        addFooter();
        applyRowLayout();
    }

    private void buildRows() {
        Map<String, AbstractWidget> voidDimensionWidgets = null;
        for (ConfigCategory section : ConfigCategory.values()) {
            List<SettingSpec<?>> specs = SettingsRegistry.visible(section, profile);
            if (specs.isEmpty()) {
                continue;
            }
            SectionHeader header = createHeader(section);
            host.addNavigationWidget(header);
            rows.add(Row.header(section, header));
            for (SettingSpec<?> spec : specs) {
                AbstractWidget widget;
                if (VOID_DIMENSION_IDS.contains(spec.id())) {
                    if (voidDimensionWidgets == null) {
                        voidDimensionWidgets = voidControls.createControls(profile);
                    }
                    widget = voidDimensionWidgets.get(spec.id());
                } else {
                    widget = bindings.createControl(spec, profile);
                }
                host.addWidget(widget);
                rows.add(Row.control(section, spec, widget, SettingsRegistry.depth(spec), widget.active));
            }
        }
    }

    private SectionHeader createHeader(ConfigCategory section) {
        SectionHeader header = new SectionHeader(0, 0, ProfileEditorLayout.MAX_ROW_WIDTH,
                ConfigScreen.CONTROL_HEIGHT, Component.translatable(section.translationKey), host.font(),
                () -> host.toggleSection(section));
        header.setSummary(SectionSummary.of(section, profile));
        header.setCollapsed(host.sectionCollapse().isCollapsed(section));
        header.setSearchRevealed(SettingsSearch.isActive(host.settingsSearchQuery()));
        return header;
    }

    private void addHeader(ProfileEditorLayout layout) {
        int y = HEADER_TOP;
        int x = layout.startX();
        int bandWidth = layout.rowWidth();
        int side = MathUtil.clamp((bandWidth - ConfigScreen.CONTROL_GAP * 2) / 4, 36, 76);
        int nameWidth = Math.max(36, bandWidth - side * 2 - ConfigScreen.CONTROL_GAP * 2);

        host.addNavigationWidget(host.controls().tooltip(FlatButton.of(
                fitted(Component.translatable("screen.elytrapitchhelper.profile.open_profiles"), side - 8),
                x, y, side, ConfigScreen.CONTROL_HEIGHT, button -> host.openProfileList()),
                "tooltip.elytrapitchhelper.profile.open_profiles"));

        addProfileDropdown(x + side + ConfigScreen.CONTROL_GAP, y, nameWidth);

        host.addWidget(host.controls().tooltip(FlatButton.of(enabledLabel(side - 8),
                x + bandWidth - side, y, side, ConfigScreen.CONTROL_HEIGHT,
                button -> host.toggleEnabled()), "tooltip.elytrapitchhelper.enabled"));
    }

    private void addProfileDropdown(int x, int y, int width) {
        Config config = host.config();
        List<String> profileFiles = new ArrayList<>();
        for (int index = 0; index < config.profileCount(); index++) {
            profileFiles.add(config.profile(index).fileName());
        }
        if (profileFiles.isEmpty()) {
            return;
        }
        int nameLimit = Math.max(0, width - 26);
        DropdownButton<String> dropdown = new DropdownButton<>(x, y, width, ConfigScreen.CONTROL_HEIGHT,
                null, profileFiles, config.profile(host.editingProfileIndex()).fileName(),
                file -> profileLabel(file, nameLimit), host::switchEditingProfile);
        dropdown.setRowStyle(0);
        dropdown.centerValue();
        host.addNavigationWidget(host.controls().tooltip(dropdown, "tooltip.elytrapitchhelper.profile.switch_editing"));
    }

    private Component profileLabel(String profileFile, int maxWidth) {
        Config config = host.config();
        int index = config.profileIndexByFileName(profileFile);
        if (index < 0) {
            return Component.empty();
        }
        String marker = config.isActiveProfile(index) ? ACTIVE_MARKER : "";
        int nameWidth = Math.max(0, maxWidth - host.font().width(marker));
        return Component.literal(marker
                + ScreenText.truncate(host.font(), config.profileName(index), nameWidth));
    }

    private Component enabledLabel(int maxWidth) {
        Component state = host.config().enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        Component named = Component.translatable("screen.elytrapitchhelper.config.enabled_toggle", state);
        return host.font().width(named) <= maxWidth ? named : state;
    }

    private void addFooter() {
        int footerY = host.screenHeight() - ConfigScreen.FOOTER_OFFSET;
        if (host.editingProfileIsActive()) {
            int doneWidth = Math.min(120, host.screenWidth() - 40);
            host.addNavigationWidget(FlatButton.of(CommonComponents.GUI_DONE,
                    (host.screenWidth() - doneWidth) / 2, footerY, doneWidth,
                    ConfigScreen.CONTROL_HEIGHT, button -> host.finishEditing()));
            return;
        }
        int buttonWidth = Math.min(132, (host.screenWidth() - 46) / 2);
        int footerX = (host.screenWidth() - buttonWidth * 2 - ConfigScreen.CONTROL_GAP) / 2;
        host.addWidget(host.controls().tooltip(FlatButton.of(
                Component.translatable("screen.elytrapitchhelper.profile.make_active"),
                footerX, footerY, buttonWidth, ConfigScreen.CONTROL_HEIGHT,
                button -> host.makeEditingProfileActive()).quiet(),
                "tooltip.elytrapitchhelper.profile.make_active"));
        host.addNavigationWidget(FlatButton.of(CommonComponents.GUI_DONE,
                footerX + buttonWidth + ConfigScreen.CONTROL_GAP, footerY, buttonWidth,
                ConfigScreen.CONTROL_HEIGHT, button -> host.finishEditing()));
    }

    void applyRowLayout() {
        ProfileEditorLayout layout = host.editorLayout();
        boolean searching = SettingsSearch.isActive(host.settingsSearchQuery());
        SectionAnimation animation = host.sectionAnimation();
        List<RowPlan> plan = currentPlan();

        Map<Object, Double> yByKey = new LinkedHashMap<>();
        Map<Object, MatchKind> kindByKey = new LinkedHashMap<>();
        Map<Object, Float> alphaByKey = new LinkedHashMap<>();
        double y = layout.scrolledTop();
        for (RowPlan planned : plan) {
            Object key = rowKey(planned.section(), planned.settingId());
            float progress = searching ? 1.0f : animation.progress(planned.section());
            boolean header = planned.settingId() == null;
            yByKey.put(key, y);
            kindByKey.put(key, planned.kind());
            alphaByKey.put(key, header ? 1.0f : SectionAnimation.rowAlpha(progress));
            y += header
                    ? ConfigScreen.ROW_HEIGHT
                    : ConfigScreen.ROW_HEIGHT * SectionAnimation.heightFactor(progress);
        }

        rebuildBands(plan, yByKey, alphaByKey, layout);

        for (Row row : rows) {
            AbstractWidget widget = row.widget();
            Object key = rowKey(row.section(), row.header() ? null : row.spec().id());
            Double rowY = yByKey.get(key);
            float rowAlpha = alphaByKey.getOrDefault(key, 0.0f);
            boolean onScreen = rowY != null && layout.drawsRow(rowY) && rowAlpha > 0.0f;
            widget.visible = onScreen;
            if (row.header() && widget instanceof SectionHeader header) {
                header.setSearchRevealed(searching);
            }
            if (row.header()) {
                widget.active = onScreen && row.activeWhenEnabled();
            } else {
                widget.active = onScreen && rowAlpha > 0.5f && row.activeWhenEnabled()
                        && SettingsRegistry.isEnabled(row.spec(), profile);
            }
            if (onScreen) {
                widget.setPosition(layout.rowX(row.depth()), (int) Math.round(rowY));
                widget.setSize(layout.rowWidth(row.depth()), ConfigScreen.CONTROL_HEIGHT);
                float matchAlpha = kindByKey.get(key) == MatchKind.CONTEXT ? CONTEXT_ALPHA : 1.0f;
                widget.setAlpha(rowAlpha * matchAlpha * edgeFade(layout, rowY));
                if (!row.header()) {
                    host.focusSettingControl(new SettingsSearch.Entry(row.spec()), widget);
                }
            } else if (widget instanceof DropdownButton<?> dropdown) {
                dropdown.close();
            }
        }
        if (pendingHeaderFocus != null) {
            for (Row row : rows) {
                if (row.header() && row.section() == pendingHeaderFocus && row.widget().visible) {
                    host.focusAfterRebuild(row.widget());
                    pendingHeaderFocus = null;
                    break;
                }
            }
        }
        if (scrollBar != null) {
            scrollBar.visible = layout.needsScrollbar();
            scrollBar.setContentHeight(layout.contentHeight());
            scrollBar.setSize(AbstractScrollArea.SCROLLBAR_WIDTH, layout.scrollbarHeight());
            scrollBar.setScrollPixels(layout.scroll());
        }
    }

    private void rebuildBands(List<RowPlan> plan, Map<Object, Double> yByKey,
            Map<Object, Float> alphaByKey, ProfileEditorLayout layout) {
        bands.clear();
        ConfigCategory openSection = null;
        int top = 0;
        int bottom = 0;
        for (RowPlan planned : plan) {
            Object key = rowKey(planned.section(), planned.settingId());
            Double rowY = yByKey.get(key);
            boolean header = planned.settingId() == null;
            if (rowY == null || !layout.drawsRow(rowY)
                    || alphaByKey.getOrDefault(key, 0.0f) <= 0.0f) {
                openSection = closeBand(openSection, top, bottom);
                continue;
            }

            int rowTop = (int) Math.round((double) rowY);
            if (header || planned.section() != openSection) {
                openSection = closeBand(openSection, top, bottom);
                top = rowTop;
            }
            bottom = rowTop + ConfigScreen.CONTROL_HEIGHT;
            if (header) {
                bands.add(new Band(rowTop, bottom, true));
                openSection = null;
            } else {
                openSection = planned.section();
            }
        }
        closeBand(openSection, top, bottom);
    }

    private ConfigCategory closeBand(ConfigCategory openSection, int top, int bottom) {
        if (openSection != null) {
            bands.add(new Band(top, bottom, false));
        }
        return null;
    }

    void extractRowsClipped(GuiGraphics context, int mouseX, int mouseY, float delta,
            ProfileEditorLayout layout) {
        context.enableScissor(layout.startX(), layout.startY(),
                layout.startX() + layout.rowWidth(), layout.viewportBottom());
        for (Row row : rows) {
            AbstractWidget widget = row.widget();
            if (!widget.visible) {
                continue;
            }
            widget.render(context, mouseX, mouseY, delta);
            widget.visible = false;
            suppressed.add(widget);
        }
        context.disableScissor();
    }

    boolean hideRowsOutside(double mouseY, ProfileEditorLayout layout) {
        if (layout.viewportContains(mouseY)) {
            return false;
        }
        for (Row row : rows) {
            AbstractWidget widget = row.widget();
            if (widget.visible) {
                widget.visible = false;
                suppressed.add(widget);
            }
        }
        return true;
    }

    void restoreRows() {
        for (AbstractWidget widget : suppressed) {
            widget.visible = true;
        }
        suppressed.clear();
    }

    void extractBackdrop(GuiGraphics context) {
        ProfileEditorLayout layout = host.editorLayout();
        int x = layout.rowX(0);
        int width = layout.rowWidth(0);
        for (int index = 0; index < bands.size(); index++) {
            Band band = bands.get(index);
            PanelBackdrop.paintBand(context, x, width,
                    layout.scrimTop(band.top(), index == 0),
                    layout.scrimBottom(band.bottom(), index == bands.size() - 1),
                    band.header());
        }
    }

    private static float edgeFade(ProfileEditorLayout layout, double rowY) {
        double fraction = layout.visibleFraction(rowY);
        return (float) (EDGE_FADE_FLOOR + (1.0 - EDGE_FADE_FLOOR) * fraction);
    }

    private record Band(int top, int bottom, boolean header) {
    }

    boolean moveRowFocus(int delta) {
        List<RowPlan> plan = currentPlan();
        AbstractWidget focused = host.focusedWidget();
        Map<Object, AbstractWidget> byKey = new LinkedHashMap<>();
        for (Row row : rows) {
            byKey.put(rowKey(row.section(), row.header() ? null : row.spec().id()), row.widget());
        }

        int current = -1;
        for (int index = 0; index < plan.size(); index++) {
            RowPlan planned = plan.get(index);
            if (byKey.get(rowKey(planned.section(), planned.settingId())) == focused) {
                current = index;
                break;
            }
        }
        if (current < 0) {
            return false;
        }

        int target = current + delta;
        if (target < 0 || target >= plan.size()) {
            return false;
        }
        host.scrollEditorRowIntoView(target);
        applyRowLayout();
        RowPlan planned = plan.get(target);
        AbstractWidget widget = byKey.get(rowKey(planned.section(), planned.settingId()));
        if (widget == null || !widget.visible) {
            return false;
        }
        host.focusWidget(widget);
        return true;
    }

    String focusedSetting() {
        AbstractWidget focused = host.focusedWidget();
        for (Row row : rows) {
            if (!row.header() && row.widget() == focused) {
                return row.spec().id();
            }
        }
        return null;
    }

    void restoreSettingFocus(String settingId) {
        if (settingId == null) {
            return;
        }
        for (Row row : rows) {
            if (!row.header() && row.spec().id().equals(settingId)
                    && row.widget().visible && row.widget().active) {
                host.focusAfterRebuild(row.widget());
                return;
            }
        }
    }

    private List<RowPlan> currentPlan() {
        return ProfileEditorPlan.plan(profile, host.settingsSearchQuery(), host.sectionCollapse(), TRANSLATOR,
                host.sectionAnimation());
    }

    void advanceAnimation(long nowMillis) {
        if (host.sectionAnimation().advance(nowMillis)) {
            applyRowLayout();
        }
    }

    private static Object rowKey(ConfigCategory section, String settingId) {
        return settingId == null ? section : settingId;
    }

    private void refreshEnabledStates() {
        for (Row row : rows) {
            if (!row.header() && row.widget().visible) {
                row.widget().active = row.activeWhenEnabled()
                        && SettingsRegistry.isEnabled(row.spec(), profile);
            }
        }
    }

    private void scrollPendingTargetIntoView() {
        String query = host.settingsSearchQuery();
        SectionCollapseSettings collapse = host.sectionCollapse();
        ConfigCategory section = host.takePendingSectionScroll();
        if (section != null) {
            int header = ProfileEditorPlan.headerRowIndex(profile, query, collapse, section, TRANSLATOR);
            if (header >= 0) {
                host.scrollEditorRowIntoView(header);
                pendingHeaderFocus = section;
            }
            return;
        }
        List<RowPlan> plan = ProfileEditorPlan.plan(profile, query, collapse, TRANSLATOR);
        for (int index = 0; index < plan.size(); index++) {
            RowPlan planned = plan.get(index);
            if (planned.settingId() == null) {
                continue;
            }
            SettingSpec<?> spec = SettingsRegistry.byId(planned.settingId());
            if (spec != null && host.isPendingSettingFocus(new SettingsSearch.Entry(spec))) {
                host.scrollEditorRowIntoView(index);
                return;
            }
        }
    }

    private void addSearchBox(ProfileEditorLayout layout) {
        EditBox searchBox = new MarqueeEditBox(host.font(), layout.startX(),
                SEARCH_TOP + layout.contentOffset(), layout.rowWidth(), ConfigScreen.CONTROL_HEIGHT,
                Component.translatable("screen.elytrapitchhelper.config.search"), null, false);
        searchBox.setMaxLength(128);
        searchBox.setHint(hintText(host.editorHint()));
        searchBox.setValue(host.settingsSearchQuery());
        searchBox.setResponder(query -> {
            host.setSettingsSearchQuery(query);
            if (SettingsSearch.isActive(query)) {
                host.recordSettingsSearchUse();
            }
            host.setEditorScrollDirect(0);
            applyRowLayout();
        });
        host.setSettingsSearchBox(searchBox);
        host.addNavigationWidget(searchBox);
    }

    private Component fitted(Component text, int maxWidth) {
        String value = text.getString();
        if (maxWidth <= 0 || host.font().width(value) <= maxWidth) {
            return text;
        }
        int ellipsis = host.font().width("...");
        return Component.literal(host.font().plainSubstrByWidth(value, Math.max(0, maxWidth - ellipsis))
                + "...");
    }

    private static Component hintText(EditorHints.Hint hint) {
        boolean mac = Util.getPlatform() == Util.OS.OSX;
        if (hint == EditorHints.Hint.SEARCH_SHORTCUT) {
            return Component.translatable(hint.translationKey, mac ? "\u2318F" : "Ctrl+F");
        }
        return Component.translatable("screen.elytrapitchhelper.config.tip",
                Component.translatable(hint.translationKey, mac ? "\u2318" : "Ctrl"));
    }

    private record Row(ConfigCategory section, SettingSpec<?> spec, AbstractWidget widget, int depth,
            boolean activeWhenEnabled) {
        static Row header(ConfigCategory section, AbstractWidget widget) {
            return new Row(section, null, widget, 0, widget.active);
        }

        static Row control(ConfigCategory section, SettingSpec<?> spec, AbstractWidget widget, int depth,
                boolean activeWhenEnabled) {
            return new Row(section, spec, widget, depth, activeWhenEnabled);
        }

        boolean header() {
            return spec == null;
        }
    }

    private void syncFloatSliders() {
        Profile current = currentProfile();
        for (Row row : rows) {
            if (row.spec() != null) {
                syncFloatSlider(current, row.spec(), row.widget());
            }
        }
    }

    static void syncFloatSlider(Profile current, SettingSpec<?> spec, AbstractWidget widget) {
        if (widget instanceof SliderRow slider
                && spec.control() instanceof SettingSpec.FloatSlider) {
            slider.setCurrent(((Number) spec.get().apply(current)).doubleValue());
        }
    }

    private Profile currentProfile() {
        int index = host.editingProfileIndex();
        if (index >= 0 && index < host.config().profileCount()) {
            profile = host.config().profile(index);
        }
        return profile;
    }

    private void replaceProfile(Profile replacement) {
        profile = replacement;
        host.replaceEditingProfile(replacement);
        refreshEnabledStates();
    }



    interface Host extends SettingBindings.Host, VoidDimensionControls.Host {
        int screenWidth();

        int screenHeight();

        Config config();

        int editingProfileIndex();

        ConfigCategory category();

        void setCategory(ConfigCategory category);

        void addWidget(AbstractWidget widget);

        void addNavigationWidget(AbstractWidget widget);

        String settingsSearchQuery();

        void setSettingsSearchQuery(String query);

        void setSettingsSearchBox(EditBox searchBox);

        EditorHints.Hint editorHint();

        void recordSettingsSearchUse();

        void focusSettingControl(SettingsSearch.Entry entry, AbstractWidget widget);

        boolean isPendingSettingFocus(SettingsSearch.Entry entry);

        ProfileEditorLayout editorLayout();

        void setEditorScrollDirect(int scroll);

        boolean editorScrollbarDragging();

        void scrollEditorRowIntoView(int rowIndex);

        AbstractWidget focusedWidget();

        void focusWidget(AbstractWidget widget);

        void focusAfterRebuild(AbstractWidget widget);

        ConfigCategory takePendingSectionScroll();

        SectionCollapseSettings sectionCollapse();

        SectionAnimation sectionAnimation();

        void toggleSection(ConfigCategory section);

        void replaceEditingProfile(Profile profile);

        void finishEditing();

        void openProfileList();

        void switchEditingProfile(String profileFile);

        boolean editingProfileIsActive();

        void makeEditingProfileActive();

        void toggleEnabled();
    }
}
