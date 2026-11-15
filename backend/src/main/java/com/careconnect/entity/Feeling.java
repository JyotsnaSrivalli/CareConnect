package com.careconnect.entity;

/**
 * How the parent feels overall. The app only ever SAVES the first three values.
 * The other four are the values the table allowed before Day 5. They stay here only so that
 * any old row in `daily_checkins` can still be READ without crashing (an enum cannot map a
 * database value it does not know).
 */
public enum Feeling {
    GOOD, OKAY, NOT_GOOD,
    // ---- legacy values (read-only) ----
    VERY_GOOD, NORMAL, FEELING_LOW, FEELING_UNWELL;

    /** True for the old values, which the API refuses to accept in a new check-in. */
    public boolean isLegacy() {
        return this != GOOD && this != OKAY && this != NOT_GOOD;
    }
}
