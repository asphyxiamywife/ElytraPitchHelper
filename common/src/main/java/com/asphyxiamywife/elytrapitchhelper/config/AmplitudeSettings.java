package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public record AmplitudeSettings(
        boolean enabled,
        int triggerMode,
        int downBlocks,
        int upBlocks,
        int toleranceBlocks,
        float downVelocity,
        float upVelocity,
        boolean repeatDiveCue,
        boolean repeatClimbCue,
        boolean motionGlyphsEnabled,
        int pullUpGlyphMillis,
        int releaseDownGlyphMillis,
        int cueColorRgb,
        boolean cuePrideEnabled,
        String cuePrideFlag,
        int[] customPrideColors) {

    public AmplitudeSettings() {
        this(ProfileDefaults.template().amplitude());
    }

    private AmplitudeSettings(AmplitudeSettings defaults) {
        this(defaults.enabled, defaults.triggerMode, defaults.downBlocks, defaults.upBlocks,
                defaults.toleranceBlocks, defaults.downVelocity, defaults.upVelocity,
                defaults.repeatDiveCue, defaults.repeatClimbCue, defaults.motionGlyphsEnabled,
                defaults.pullUpGlyphMillis, defaults.releaseDownGlyphMillis, defaults.cueColorRgb,
                defaults.cuePrideEnabled, defaults.cuePrideFlag, defaults.customPrideColors);
    }

    public AmplitudeSettings {
        cuePrideFlag = cuePrideFlag == null ? PrideFlag.defaultId() : cuePrideFlag;
        customPrideColors = copyColors(customPrideColors);
    }

    @Override
    public int[] customPrideColors() {
        return copyColors(customPrideColors);
    }

    public AmplitudeSettings withEnabled(boolean value) {
        return edit(builder -> builder.enabled = value);
    }

    public AmplitudeSettings withTriggerMode(int value) {
        return edit(builder -> builder.triggerMode = value);
    }

    public AmplitudeSettings withDownBlocks(int value) {
        return edit(builder -> builder.downBlocks = value);
    }

    public AmplitudeSettings withUpBlocks(int value) {
        return edit(builder -> builder.upBlocks = value);
    }

    public AmplitudeSettings withToleranceBlocks(int value) {
        return edit(builder -> builder.toleranceBlocks = value);
    }

    public AmplitudeSettings withDownVelocity(float value) {
        return edit(builder -> builder.downVelocity = value);
    }

    public AmplitudeSettings withUpVelocity(float value) {
        return edit(builder -> builder.upVelocity = value);
    }

    public AmplitudeSettings withRepeatDiveCue(boolean value) {
        return edit(builder -> builder.repeatDiveCue = value);
    }

    public AmplitudeSettings withRepeatClimbCue(boolean value) {
        return edit(builder -> builder.repeatClimbCue = value);
    }

    public AmplitudeSettings withMotionGlyphsEnabled(boolean value) {
        return edit(builder -> builder.motionGlyphsEnabled = value);
    }

    public AmplitudeSettings withPullUpGlyphMillis(int value) {
        return edit(builder -> builder.pullUpGlyphMillis = value);
    }

    public AmplitudeSettings withReleaseDownGlyphMillis(int value) {
        return edit(builder -> builder.releaseDownGlyphMillis = value);
    }

    public AmplitudeSettings withCueColorRgb(int value) {
        return edit(builder -> builder.cueColorRgb = value);
    }

    public AmplitudeSettings withCuePrideEnabled(boolean value) {
        return edit(builder -> builder.cuePrideEnabled = value);
    }

    public AmplitudeSettings withCuePrideFlag(String value) {
        return edit(builder -> builder.cuePrideFlag = value);
    }

    public AmplitudeSettings withCustomPrideColors(int[] value) {
        return edit(builder -> builder.customPrideColors = value);
    }

    public AmplitudeSettings withCuePride(boolean enabledValue, String flag, int[] colors) {
        return edit(builder -> {
            builder.cuePrideEnabled = enabledValue;
            builder.cuePrideFlag = flag;
            builder.customPrideColors = colors;
        });
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AmplitudeSettings that
                && enabled == that.enabled
                && triggerMode == that.triggerMode
                && downBlocks == that.downBlocks
                && upBlocks == that.upBlocks
                && toleranceBlocks == that.toleranceBlocks
                && Float.compare(downVelocity, that.downVelocity) == 0
                && Float.compare(upVelocity, that.upVelocity) == 0
                && repeatDiveCue == that.repeatDiveCue
                && repeatClimbCue == that.repeatClimbCue
                && motionGlyphsEnabled == that.motionGlyphsEnabled
                && pullUpGlyphMillis == that.pullUpGlyphMillis
                && releaseDownGlyphMillis == that.releaseDownGlyphMillis
                && cueColorRgb == that.cueColorRgb
                && cuePrideEnabled == that.cuePrideEnabled
                && Objects.equals(cuePrideFlag, that.cuePrideFlag)
                && Arrays.equals(customPrideColors, that.customPrideColors);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(enabled, triggerMode, downBlocks, upBlocks, toleranceBlocks,
                downVelocity, upVelocity, repeatDiveCue, repeatClimbCue, motionGlyphsEnabled,
                pullUpGlyphMillis, releaseDownGlyphMillis, cueColorRgb,
                cuePrideEnabled, cuePrideFlag);
        return 31 * result + Arrays.hashCode(customPrideColors);
    }

    private AmplitudeSettings edit(Consumer<Builder> mutation) {
        Builder builder = new Builder(this);
        mutation.accept(builder);
        return builder.build();
    }

    private static final class Builder {
        private boolean enabled;
        private int triggerMode;
        private int downBlocks;
        private int upBlocks;
        private int toleranceBlocks;
        private float downVelocity;
        private float upVelocity;
        private boolean repeatDiveCue;
        private boolean repeatClimbCue;
        private boolean motionGlyphsEnabled;
        private int pullUpGlyphMillis;
        private int releaseDownGlyphMillis;
        private int cueColorRgb;
        private boolean cuePrideEnabled;
        private String cuePrideFlag;
        private int[] customPrideColors;

        private Builder(AmplitudeSettings from) {
            enabled = from.enabled;
            triggerMode = from.triggerMode;
            downBlocks = from.downBlocks;
            upBlocks = from.upBlocks;
            toleranceBlocks = from.toleranceBlocks;
            downVelocity = from.downVelocity;
            upVelocity = from.upVelocity;
            repeatDiveCue = from.repeatDiveCue;
            repeatClimbCue = from.repeatClimbCue;
            motionGlyphsEnabled = from.motionGlyphsEnabled;
            pullUpGlyphMillis = from.pullUpGlyphMillis;
            releaseDownGlyphMillis = from.releaseDownGlyphMillis;
            cueColorRgb = from.cueColorRgb;
            cuePrideEnabled = from.cuePrideEnabled;
            cuePrideFlag = from.cuePrideFlag;
            customPrideColors = from.customPrideColors;
        }

        private AmplitudeSettings build() {
            return new AmplitudeSettings(
                    enabled, triggerMode, downBlocks,
                    upBlocks, toleranceBlocks, downVelocity,
                    upVelocity, repeatDiveCue, repeatClimbCue,
                    motionGlyphsEnabled, pullUpGlyphMillis, releaseDownGlyphMillis,
                    cueColorRgb, cuePrideEnabled, cuePrideFlag,
                    customPrideColors);
        }
    }

    private static int[] copyColors(int[] colors) {
        int[] source = colors == null ? PrideFlag.defaultCustomColors() : colors;
        return Arrays.copyOf(source, source.length);
    }
}
