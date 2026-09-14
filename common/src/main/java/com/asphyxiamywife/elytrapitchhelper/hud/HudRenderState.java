package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.config.PrideFlag;

import java.util.Arrays;

public record HudRenderState(Color line, Color cue, Color warning) {
    public static HudRenderState from(Config config) {
        return new HudRenderState(
                Color.from(config.line().colorRgb(), config.line().prideEnabled(),
                        config.line().prideFlag(), config.line().customPrideColors()),
                Color.from(config.amplitude().cueColorRgb(), config.amplitude().cuePrideEnabled(),
                        config.amplitude().cuePrideFlag(), config.amplitude().customPrideColors()),
                Color.from(config.voidWarning().warningColorRgb(), config.voidWarning().warningPrideEnabled(),
                        config.voidWarning().warningPrideFlag(), config.voidWarning().customPrideColors()));
    }

    public static final class Color {
        private static final int[] NO_STRIPES = new int[0];

        private final int rgb;
        private final int[] stripeColors;

        private Color(int rgb, int[] stripeColors) {
            this.rgb = rgb & 0x00FFFFFF;
            this.stripeColors = copyStripeColors(stripeColors);
        }

        static Color from(int rgb, boolean prideEnabled, String prideFlag, int[] customPrideColors) {
            return new Color(rgb, prideEnabled ? PrideFlag.colorsFor(prideFlag, customPrideColors) : NO_STRIPES);
        }

        public int rgb() {
            return rgb;
        }

        public boolean striped() {
            return stripeColors.length > 0;
        }

        public int segmentCount() {
            return striped() ? stripeColors.length : 1;
        }

        public int colorAt(double position) {
            if (!striped()) {
                return rgb;
            }
            return PrideFlag.colorAt(stripeColors, position);
        }

        int[] stripeColorsForTests() {
            return Arrays.copyOf(stripeColors, stripeColors.length);
        }

        private static int[] copyStripeColors(int[] colors) {
            if (colors == null || colors.length == 0) {
                return NO_STRIPES;
            }

            int[] copy = Arrays.copyOf(colors, colors.length);
            for (int i = 0; i < copy.length; i++) {
                copy[i] &= 0x00FFFFFF;
            }
            return copy;
        }
    }
}
