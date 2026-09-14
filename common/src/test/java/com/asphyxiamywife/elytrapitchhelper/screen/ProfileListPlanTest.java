package com.asphyxiamywife.elytrapitchhelper.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileListPlanTest {
    @Test
    void aClosedPlanIsExactlyItsProfiles() {
        ProfileListPlan plan = ProfileListPlan.closed(5);
        assertFalse(plan.stripOpen());
        assertEquals(5, plan.slotCount());
        assertEquals(-1, plan.stripSlot());
        for (int index = 0; index < 5; index++) {
            assertEquals(index, plan.profileAtSlot(index));
            assertEquals(index, plan.slotOfProfile(index));
        }
    }

    @Test
    void theStripFollowsTheSelectedProfile() {
        for (int selected : new int[] {0, 2, 4}) {
            ProfileListPlan plan = ProfileListPlan.of(5, selected);
            assertTrue(plan.stripOpen());
            assertEquals(6, plan.slotCount());
            assertEquals(selected + 1, plan.stripSlot());
            assertTrue(plan.isStripSlot(selected + 1));
            assertEquals(-1, plan.profileAtSlot(selected + 1));
        }
    }

    @Test
    void theStripPushesOnlyTheProfilesBelowIt() {
        ProfileListPlan plan = ProfileListPlan.of(5, 2);
        assertEquals(0, plan.slotOfProfile(0));
        assertEquals(2, plan.slotOfProfile(2));
        assertEquals(4, plan.slotOfProfile(3));
        assertEquals(5, plan.slotOfProfile(4));

        assertEquals(0, plan.profileAtSlot(0));
        assertEquals(2, plan.profileAtSlot(2));
        assertEquals(3, plan.profileAtSlot(4));
        assertEquals(4, plan.profileAtSlot(5));
    }

    @Test
    void slotsAndProfilesRoundTrip() {
        for (int selected = -1; selected < 6; selected++) {
            ProfileListPlan plan = ProfileListPlan.of(6, selected);
            for (int index = 0; index < 6; index++) {
                int profile = index;
                assertEquals(profile, plan.profileAtSlot(plan.slotOfProfile(profile)),
                        () -> "profile " + profile + " does not survive the round trip");
            }
        }
    }

    @Test
    void anOutOfRangeSelectionClosesTheStrip() {
        for (int selected : new int[] {-1, 3, 99}) {
            ProfileListPlan plan = ProfileListPlan.of(3, selected);
            assertFalse(plan.stripOpen());
            assertEquals(3, plan.slotCount());
            assertEquals(-1, plan.selectedIndex());
        }
    }

    @Test
    void slotsOffTheEndsHoldNothing() {
        ProfileListPlan plan = ProfileListPlan.of(3, 1);
        assertEquals(-1, plan.profileAtSlot(-1));
        assertEquals(-1, plan.profileAtSlot(plan.slotCount()));
        assertEquals(-1, plan.slotOfProfile(-1));
        assertEquals(-1, plan.slotOfProfile(3));
    }

    @Test
    void anEmptyListHasNoSlots() {
        ProfileListPlan plan = ProfileListPlan.of(0, 0);
        assertFalse(plan.stripOpen());
        assertEquals(0, plan.slotCount());
    }
}
