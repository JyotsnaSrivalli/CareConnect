package com.careconnect.entity;

/** What is stored in medication_logs. There is no "missed": a dose without a TAKEN log is simply pending. */
public enum LogStatus {
    TAKEN, UNDONE
}
