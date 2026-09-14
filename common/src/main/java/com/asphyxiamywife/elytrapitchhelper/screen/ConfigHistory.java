package com.asphyxiamywife.elytrapitchhelper.screen;

import com.asphyxiamywife.elytrapitchhelper.config.Config;
import com.asphyxiamywife.elytrapitchhelper.util.MonotonicClock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

final class ConfigHistory {
    static final long DEFAULT_COALESCE_MILLIS = 750L;
    private static final int MAX_ENTRIES = 100;

    private final Deque<Entry> undo = new ArrayDeque<>();
    private final Deque<Entry> redo = new ArrayDeque<>();
    private PendingAction pending;
    private boolean coalescingBlocked;

    void begin(String key, Config before, Context context) {
        if (pending != null) {
            commit(before, context, !pending.before().hasSameState(before), true,
                    MonotonicClock.millis());
        }
        pending = new PendingAction(key, before.copy(), context);
    }

    boolean isActionActive() {
        return pending != null;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    void commit(Config after, Context context, boolean changed, boolean coalesce, long nowMillis) {
        PendingAction action = pending;
        pending = null;
        if (action == null || !changed) {
            return;
        }
        record(action.key(), action.before(), action.context(), after, context, coalesce, nowMillis);
    }

    void record(String key, Config before, Context beforeContext, Config after, Context afterContext,
            boolean coalesce, long nowMillis) {
        Entry next = new Entry(key, before.copy(), after.copy(), beforeContext, afterContext, nowMillis);
        Entry previous = undo.peekLast();
        long elapsedMillis = previous == null ? Long.MAX_VALUE : nowMillis - previous.timestampMillis();
        if (coalesce && !coalescingBlocked && previous != null && previous.key().equals(key)
                && previous.afterContext().sameLocation(beforeContext)
                && previous.after().hasSameState(before)
                && elapsedMillis >= 0L && elapsedMillis <= DEFAULT_COALESCE_MILLIS) {
            undo.removeLast();
            undo.addLast(new Entry(key, previous.before(), next.after(), previous.beforeContext(), next.afterContext(),
                    nowMillis));
        } else {
            undo.addLast(next);
            while (undo.size() > MAX_ENTRIES) {
                undo.removeFirst();
            }
        }
        redo.clear();
        coalescingBlocked = false;
    }

    Restore undo() {
        pending = null;
        Entry entry = undo.pollLast();
        if (entry == null) {
            return null;
        }
        redo.addLast(entry);
        coalescingBlocked = true;
        return new Restore(entry.before().copy(), entry.beforeContext(), entry.after().copy());
    }

    Restore redo() {
        pending = null;
        Entry entry = redo.pollLast();
        if (entry == null) {
            return null;
        }
        undo.addLast(entry);
        coalescingBlocked = true;
        return new Restore(entry.after().copy(), entry.afterContext(), entry.before().copy());
    }

    void breakCoalescing() {
        coalescingBlocked = true;
    }

    void clear() {
        undo.clear();
        redo.clear();
        pending = null;
        coalescingBlocked = false;
    }

    record Context(boolean editingProfile, String profileFile, ConfigCategory category,
            String dimensionKey, int profileScroll, int editorScroll, boolean deleteMode,
            String selectedProfile, List<String> markedProfiles, String focusedSetting) {
        Context {
            markedProfiles = markedProfiles == null ? List.of() : List.copyOf(markedProfiles);
        }

        boolean sameLocation(Context other) {
            return other != null
                    && editingProfile == other.editingProfile
                    && java.util.Objects.equals(profileFile, other.profileFile)
                    && category == other.category
                    && java.util.Objects.equals(dimensionKey, other.dimensionKey)
                    && deleteMode == other.deleteMode;
        }
    }

    record Restore(Config config, Context context, Config actionSource) {
    }

    private record PendingAction(String key, Config before, Context context) {
    }

    private record Entry(String key, Config before, Config after, Context beforeContext, Context afterContext,
            long timestampMillis) {
    }
}
