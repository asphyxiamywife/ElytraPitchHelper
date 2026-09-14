package com.asphyxiamywife.elytrapitchhelper.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.function.DoublePredicate;

final class ValueScroll {
    private static final double STEP = 1.0;
    static final int MAX_REPLAYS = 32;

    private double travelled;

    static boolean isAdjusting(Minecraft client) {
        return client != null
                && (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                        || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
    }

    int steps(double scrollX, double scrollY) {
        double delta = scrollY != 0.0 ? scrollY : scrollX;
        if (!Double.isFinite(delta)) {
            return 0;
        }
        travelled += delta;
        int steps = (int) (travelled / STEP);
        travelled -= steps * STEP;
        if (Math.abs(travelled) >= STEP) {
            travelled = Math.copySign(Math.nextDown(STEP), travelled);
        }
        return steps;
    }

    static boolean replay(int steps, DoublePredicate dispatch) {
        double direction = Math.signum(steps);
        int replays = replayCount(steps);
        boolean handled = false;
        for (int i = 0; i < replays; i++) {
            handled |= dispatch.test(direction);
        }
        return handled;
    }

    static int replayCount(int steps) {
        return (int) Math.min(Math.abs((long) steps), MAX_REPLAYS);
    }

    void reset() {
        travelled = 0.0;
    }
}
