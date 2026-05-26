package com.asphyxiamywife.elytrapitchhelper.config;

public record StagedProfileDelete(Profile profile, int index, boolean wasActive) {
    public StagedProfileDelete {
        profile = profile == null ? null : profile.copy();
    }

    public String fileName() {
        return profile == null ? null : profile.fileName;
    }
}
