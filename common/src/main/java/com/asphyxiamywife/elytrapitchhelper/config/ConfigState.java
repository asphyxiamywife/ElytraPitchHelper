package com.asphyxiamywife.elytrapitchhelper.config;

record ConfigState(
        ConfigDocument document,
        StoreMetadata metadata,
        ProfileMetadataStore profileMetadata,
        boolean skipNextProfileSave) {
    ConfigState {
        document = document == null
                ? new ConfigDocument(Config.CURRENT_VERSION, true, 0, null,
                        Config.PROFILE_SORT_CREATED, new CommandPaletteUsageSettings(),
                        new SectionCollapseSettings(), false, null)
                : document;
        metadata = metadata == null ? StoreMetadata.EMPTY : metadata;
        profileMetadata = profileMetadata == null
                ? new ProfileMetadataStore() : profileMetadata.copy();
    }

    ConfigState(ConfigDocument document, StoreMetadata metadata) {
        this(document, metadata, null, false);
    }

    @Override
    public ProfileMetadataStore profileMetadata() {
        return profileMetadata.copy();
    }
}
