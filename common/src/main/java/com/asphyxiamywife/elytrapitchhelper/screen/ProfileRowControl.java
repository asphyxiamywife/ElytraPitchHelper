package com.asphyxiamywife.elytrapitchhelper.screen;

enum ProfileRowControl {
    ACTIVE,
    NAME,
    ACTION;

    record Pending(String profileFile, ProfileRowControl control, int caret) {
        static final int NO_CARET = -1;

        static Pending of(String profileFile, ProfileRowControl control) {
            return new Pending(profileFile, control, NO_CARET);
        }

        boolean matches(String otherFile, ProfileRowControl otherControl) {
            return control == otherControl && profileFile != null && profileFile.equals(otherFile);
        }
    }
}
