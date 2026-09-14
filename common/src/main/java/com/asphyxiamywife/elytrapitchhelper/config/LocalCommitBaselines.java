package com.asphyxiamywife.elytrapitchhelper.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class LocalCommitBaselines {
    private static final LoadedFileState DELETED_IDENTITY = new LoadedFileState(true, true, 0L, null);
    private final Map<Token, Boolean> tokens = new WeakHashMap<>();
    private final Set<String> occupiedProfileFiles = new HashSet<>();

    public synchronized void attach(Config config) {
        Token token = new Token(this, config.storeMetadata);
        tokens.put(token, Boolean.TRUE);
        config.localBaselineToken = token;
        config.editBaseline = config.document();
    }

    public synchronized void publishedProfiles(Config persisted, Config effective) {
        Set<String> persistedFiles = new HashSet<>();
        for (Profile profile : persisted.profiles) {
            persistedFiles.add(ProfileFileNames.comparisonKey(profile.fileName()));
        }
        occupiedProfileFiles.clear();
        occupiedProfileFiles.addAll(persistedFiles);
        for (Profile profile : effective.profiles) {
            occupiedProfileFiles.add(ProfileFileNames.comparisonKey(profile.fileName()));
        }
        for (Token token : tokens.keySet()) {
            token.reservedProfileFiles.removeAll(persistedFiles);
        }
    }

    synchronized String allocateProfileFile(Config draft, String name, Set<String> usedFiles) {
        Set<String> occupied = new HashSet<>(usedFiles);
        occupied.addAll(occupiedProfileFiles);
        for (Token token : tokens.keySet()) {
            occupied.addAll(token.reservedProfileFiles);
        }
        String file = ProfileFileNames.unique(name, occupied);
        draft.localBaselineToken.reservedProfileFiles.add(ProfileFileNames.comparisonKey(file));
        return file;
    }

    public synchronized Config prepareSubmission(Config draft, Config current) {
        Config prepared = reconcile(draft);
        if (current != null && draft.editBaseline != null && draft.localBaselineToken != null
                && draft.localBaselineToken.owner == this) {
            ConfigDraftMerge.merge(draft.editBaseline, prepared.document(), current.document()).applyTo(prepared);
            prepared.inheritAccessModeFrom(current);
        }
        return prepared;
    }

    public synchronized void accepted(Config draft) {
        if (draft.localBaselineToken != null && draft.localBaselineToken.owner == this) {
            draft.editBaseline = draft.copy().document();
        }
    }

    public synchronized Config reconcile(Config draft) {
        Config reconciled = draft.copy();
        Token token = draft.localBaselineToken;
        if (token != null && token.owner == this) {
            reconciled.storeMetadata = advance(reconciled.storeMetadata, token.original, token.latest);
            if (token.suppressionConsumed) {
                reconciled.skipNextProfileSave = false;
            }
            for (String file : token.deletedProfiles) {
                if (draft.storeMetadata.profile(file).equals(token.original.profile(file))) {
                    reconciled.storeMetadata = reconciled.storeMetadata.withProfile(file, DELETED_IDENTITY);
                }
            }
        }
        attach(reconciled);
        if (token != null && token.owner == this) {
            reconciled.localBaselineToken.deletedProfiles.addAll(token.deletedProfiles);
            reconciled.localBaselineToken.reservedProfileFiles.addAll(token.reservedProfileFiles);
        }
        return reconciled;
    }

    synchronized boolean wasProfileDeleted(Token token, String file) {
        return token.deletedProfiles.contains(ProfileFileNames.comparisonKey(file));
    }

    synchronized void approved(Config config, Set<String> profileFiles) {
        Token previous = config.localBaselineToken;
        ConfigDocument editBaseline = config.editBaseline;
        attach(config);
        config.editBaseline = editBaseline;
        Token approved = config.localBaselineToken;
        approved.latest = advance(config.storeMetadata, previous.original, previous.latest);
        approved.suppressionConsumed = previous.suppressionConsumed;
        approved.deletedProfiles.addAll(previous.deletedProfiles);
        approved.reservedProfileFiles.addAll(previous.reservedProfileFiles);
        approved.deletedProfiles.removeAll(profileFiles);
    }

    public synchronized void committed(Config before, Config after) {
        StoreMetadata written = before.storeMetadata;
        if (after.writtenMain != null) {
            written = written.withMainConfig(after.writtenMain);
        }
        for (var entry : after.writtenProfiles.entrySet()) {
            written = written.withProfile(entry.getKey(), entry.getValue());
        }
        for (Token token : tokens.keySet()) {
            for (var entry : after.writtenProfiles.entrySet()) {
                if (!entry.getValue().exists() && token.latest.profile(entry.getKey()).exists()) {
                    token.deletedProfiles.add(ProfileFileNames.comparisonKey(entry.getKey()));
                }
            }
            token.latest = advance(token.latest, before.storeMetadata, written);
            token.suppressionConsumed |= before.skipNextProfileSave && !after.skipNextProfileSave;
        }
    }

    private static StoreMetadata advance(StoreMetadata current, StoreMetadata before, StoreMetadata after) {
        StoreMetadata updated = current;
        if (current.mainConfig().equals(before.mainConfig())) {
            updated = updated.withMainConfig(after.mainConfig());
        }
        for (var entry : after.profiles().entrySet()) {
            String file = entry.getKey();
            if (current.profile(file).equals(before.profile(file))) {
                updated = updated.withProfile(file, entry.getValue());
            }
        }
        return updated;
    }

    static final class Token {
        final LocalCommitBaselines owner;
        private final Set<String> deletedProfiles = new HashSet<>();
        private final Set<String> reservedProfileFiles = new HashSet<>();
        private final StoreMetadata original;
        private StoreMetadata latest;
        private boolean suppressionConsumed;

        private Token(LocalCommitBaselines owner, StoreMetadata original) {
            this.owner = owner;
            this.original = original;
            this.latest = original;
        }
    }
}
