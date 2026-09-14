package com.asphyxiamywife.elytrapitchhelper.config;

public record StagedProfileDelete(Profile profile, int index, boolean wasActive) {
    public StagedProfileDelete {
    }

    public String fileName() {
        return profile == null ? null : profile.fileName();
    }
}
