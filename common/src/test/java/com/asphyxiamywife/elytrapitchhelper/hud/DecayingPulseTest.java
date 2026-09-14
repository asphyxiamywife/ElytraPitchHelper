package com.asphyxiamywife.elytrapitchhelper.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class DecayingPulseTest {
    @Test
    void untriggeredPulseIsExpiredWithNegativeClock() {
        DecayingPulse pulse = new DecayingPulse(520L, () -> -1_000L);

        assertTrue(pulse.expired());
    }

    @Test
    void pulseExpiresWhenClockMovesBackwards() {
        long[] now = {100L};
        DecayingPulse pulse = new DecayingPulse(520L, () -> now[0]);
        pulse.trigger();
        now[0] = 99L;

        assertTrue(pulse.expired());
    }
}
