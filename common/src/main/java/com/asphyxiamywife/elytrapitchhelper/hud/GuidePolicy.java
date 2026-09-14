package com.asphyxiamywife.elytrapitchhelper.hud;

import com.asphyxiamywife.elytrapitchhelper.config.Profile;
import com.asphyxiamywife.elytrapitchhelper.util.MathUtil;

final class GuidePolicy {
    private static final float MIN_VISIBLE_ALPHA = 0.01f;

    private GuidePolicy() {}

    static float cueAmountForLeg(AmplitudeCue cue, AmplitudeLeg leg) {
        return cue.leg == leg ? cue.amount : 0.0f;
    }

    static float flashForLeg(AmplitudeCue cue, AmplitudeLeg leg, float currentFlash) {
        return cue.flashLeg == leg ? currentFlash : 0.0f;
    }

    static boolean isGuideVisible(Profile profile, float pitch, AmplitudeLeg leg) {
        return isGuideVisible(profile, pitch, leg, false);
    }

    static boolean isGuideVisible(Profile profile, float pitch, AmplitudeLeg leg,
            boolean forceShow) {
        float targetPitch = leg == AmplitudeLeg.DESCENDING
                ? profile.pitch().targetDownMinecraft()
                : profile.pitch().targetUpMinecraft();
        float effectiveTolerance = forceShow && profile.voidWarning().toleranceOverride()
                ? profile.voidWarning().customToleranceDegrees()
                : profile.pitch().toleranceDegrees();
        float alpha = computeAlpha(effectiveTolerance, Math.abs(targetPitch - pitch));
        if (forceShow && !profile.voidWarning().toleranceOverride()) {
            alpha = Math.max(alpha, 0.6f);
        }
        return alpha > MIN_VISIBLE_ALPHA;
    }

    static float computeAlpha(float toleranceDegrees, float diff) {
        if (toleranceDegrees <= 0.0f) {
            return diff == 0.0f ? 1.0f : 0.0f;
        }
        if (diff > toleranceDegrees) {
            return 0.0f;
        }
        return 0.15f + 0.85f * (1.0f - (diff / toleranceDegrees));
    }

    static int offset(Profile profile, float pitch, float targetPitch, boolean forceShow) {
        int maxOffset = forceShow && profile.voidWarning().maxOffsetOverride()
                ? profile.voidWarning().customMaxOffsetPixels()
                : profile.pitch().maxOffsetPixels();
        return (int) Math.round(MathUtil.clamp((targetPitch - pitch) * profile.pitch().offsetPerDegree(),
                -maxOffset, maxOffset));
    }
}
