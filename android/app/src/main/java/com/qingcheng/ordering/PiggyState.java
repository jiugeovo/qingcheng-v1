package com.qingcheng.ordering;

import android.content.SharedPreferences;

final class PiggyState {
    static final String PREFERENCES = "electronic-piggy.state.v1";
    static final String COUNT = "count";
    static final String SOUND_ENABLED = "soundEnabled";
    static final String HAPTICS_ENABLED = "hapticsEnabled";

    final long count;
    final boolean soundEnabled;
    final boolean hapticsEnabled;

    PiggyState(long count, boolean soundEnabled, boolean hapticsEnabled) {
        this.count = Math.max(0L, count);
        this.soundEnabled = soundEnabled;
        this.hapticsEnabled = hapticsEnabled;
    }

    static PiggyState defaults() {
        return new PiggyState(0L, true, true);
    }

    static PiggyState load(SharedPreferences preferences) {
        if (preferences == null) {
            return defaults();
        }
        try {
            long count = preferences.getLong(COUNT, 0L);
            boolean sound = preferences.getBoolean(SOUND_ENABLED, true);
            boolean haptics = preferences.getBoolean(HAPTICS_ENABLED, true);
            return new PiggyState(count, sound, haptics);
        } catch (ClassCastException ignored) {
            return defaults();
        }
    }

    PiggyState tapped() {
        return count == Long.MAX_VALUE
                ? this
                : new PiggyState(count + 1L, soundEnabled, hapticsEnabled);
    }

    PiggyState withSoundEnabled(boolean enabled) {
        return new PiggyState(count, enabled, hapticsEnabled);
    }

    PiggyState withHapticsEnabled(boolean enabled) {
        return new PiggyState(count, soundEnabled, enabled);
    }

    PiggyState resetCount() {
        return new PiggyState(0L, soundEnabled, hapticsEnabled);
    }

    void save(SharedPreferences preferences) {
        withFeedback(preferences.edit().putLong(COUNT, count)).apply();
    }

    void saveFeedback(SharedPreferences preferences) {
        withFeedback(preferences.edit()).apply();
    }

    private SharedPreferences.Editor withFeedback(SharedPreferences.Editor editor) {
        return editor
                .putBoolean(SOUND_ENABLED, soundEnabled)
                .putBoolean(HAPTICS_ENABLED, hapticsEnabled);
    }
}
