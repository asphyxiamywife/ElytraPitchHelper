package com.asphyxiamywife.elytrapitchhelper.hud;

record ColumnScan(int supportY, int provedEmptyThroughY, int lookups) {
    static final int NO_SUPPORT = Integer.MIN_VALUE;

    boolean foundSupport() {
        return supportY != NO_SUPPORT;
    }
}
