package com.asphyxiamywife.elytrapitchhelper.screen;

record ProfileListPlan(int profileCount, int selectedIndex, boolean stripOpen) {
    static ProfileListPlan of(int profileCount, int selectedIndex) {
        int safeCount = Math.max(0, profileCount);
        boolean open = selectedIndex >= 0 && selectedIndex < safeCount;
        return new ProfileListPlan(safeCount, open ? selectedIndex : -1, open);
    }

    static ProfileListPlan closed(int profileCount) {
        return of(profileCount, -1);
    }

    int slotCount() {
        return profileCount + (stripOpen ? 1 : 0);
    }

    int stripSlot() {
        return stripOpen ? selectedIndex + 1 : -1;
    }

    boolean isStripSlot(int slot) {
        return stripOpen && slot == selectedIndex + 1;
    }

    int profileAtSlot(int slot) {
        if (slot < 0 || slot >= slotCount() || isStripSlot(slot)) {
            return -1;
        }
        return stripOpen && slot > selectedIndex ? slot - 1 : slot;
    }

    int slotOfProfile(int profileIndex) {
        if (profileIndex < 0 || profileIndex >= profileCount) {
            return -1;
        }
        return stripOpen && profileIndex > selectedIndex ? profileIndex + 1 : profileIndex;
    }
}
