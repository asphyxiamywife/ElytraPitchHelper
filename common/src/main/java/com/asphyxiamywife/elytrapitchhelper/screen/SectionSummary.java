package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.config.VoidWarningSettings;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class SectionSummary {
    private SectionSummary() {
    }

    static Component of(ConfigCategory section, Profile profile) {
        return switch (section) {
            case PITCH -> Component.literal(degrees(profile.pitch().targetUpMinecraft())
                    + " / " + degrees(profile.pitch().targetDownMinecraft()));
            case LINE -> Component.literal(profile.line().lengthPixels() + "×"
                    + profile.line().widthPixels() + " px");
            case AMPLITUDE -> profile.amplitude().enabled()
                    ? joined(onOff(true), triggerMode(profile.amplitude().triggerMode()))
                    : onOff(false);
            case VOID -> profile.voidWarning().enabled()
                    ? joined(onOff(true), voidMode(profile.voidWarning().mode()))
                    : onOff(false);
            case GENERAL, INTERFACE -> CommonComponents.EMPTY;
        };
    }

    private static String degrees(float value) {
        return Math.round(value) + "°";
    }

    private static Component joined(Component first, Component second) {
        return Component.empty().append(first).append(", ").append(second);
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on
                ? "screen.elytrapitchhelper.summary.on"
                : "screen.elytrapitchhelper.summary.off");
    }

    private static Component triggerMode(int mode) {
        if (mode == Config.AMPLITUDE_TRIGGER_HEIGHT) {
            return Component.translatable("option.elytrapitchhelper.amplitude_trigger.height");
        }
        if (mode == Config.AMPLITUDE_TRIGGER_EITHER) {
            return Component.translatable("option.elytrapitchhelper.amplitude_trigger.either");
        }
        return Component.translatable("option.elytrapitchhelper.amplitude_trigger.velocity");
    }

    private static Component voidMode(int mode) {
        return Component.translatable(mode == VoidWarningSettings.MODE_SIMPLE_HEIGHT
                ? "option.elytrapitchhelper.void_mode.simple"
                : "option.elytrapitchhelper.void_mode.predicted");
    }
}
