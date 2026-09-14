package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingSpec;
import com.asphyxiamywife.elytrapitchhelper.config.schema.SettingsRegistry;
import com.asphyxiamywife.elytrapitchhelper.screen.widget.SliderRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ProfileEditorFocusTest {
    @Test
    void restoredFocusUsesTheReplacementSettingAndWaitsForInitialFocus() throws Exception {
        AtomicReference<AbstractWidget> focused = new AtomicReference<>();
        AtomicReference<AbstractWidget> queued = new AtomicReference<>();
        ProfileEditorPanel.Host host = (ProfileEditorPanel.Host) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ProfileEditorPanel.Host.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "focusedWidget" -> focused.get();
                    case "focusAfterRebuild" -> {
                        queued.set((AbstractWidget) args[0]);
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected host call: " + method.getName());
                });
        SliderRow original = slider();
        ProfileEditorPanel before = panel(host, original);
        focused.set(original);
        String setting = before.focusedSetting();
        assertEquals("amplitude_up_velocity", setting);

        SliderRow replacement = slider();
        ProfileEditorPanel rebuilt = panel(host, replacement);
        rebuilt.restoreSettingFocus(setting);
        assertSame(replacement, queued.get());
        assertSame(original, focused.get(), "Focus must wait until Screen finishes rebuilding");

        queued.set(null);
        replacement.visible = false;
        rebuilt.restoreSettingFocus(setting);
        assertNull(queued.get(), "A setting hidden by search or a folded section must not take focus");
        replacement.visible = true;
        replacement.active = false;
        rebuilt.restoreSettingFocus(setting);
        assertNull(queued.get(), "A disabled setting must not take focus");
        replacement.active = true;
        rebuilt.restoreSettingFocus("missing_setting");
        assertNull(queued.get());
    }

    private static SliderRow slider() {
        return new SliderRow(Component.literal("Reset speed"), null, 0, 0.5, 0, 10, 0.05,
                Double::toString, value -> {}, "amplitude_up_velocity", null);
    }

    @SuppressWarnings("unchecked")
    private static ProfileEditorPanel panel(ProfileEditorPanel.Host host, AbstractWidget widget) throws Exception {
        ProfileEditorPanel panel = new ProfileEditorPanel(host);
        Class<?> rowClass = Class.forName(ProfileEditorPanel.class.getName() + "$Row");
        var constructor = rowClass.getDeclaredConstructor(ConfigCategory.class, SettingSpec.class,
                AbstractWidget.class, int.class, boolean.class);
        constructor.setAccessible(true);
        Field rows = ProfileEditorPanel.class.getDeclaredField("rows");
        rows.setAccessible(true);
        ((List<Object>) rows.get(panel)).add(constructor.newInstance(ConfigCategory.AMPLITUDE,
                SettingsRegistry.byId("amplitude_up_velocity"), widget, 1, true));
        return panel;
    }
}
