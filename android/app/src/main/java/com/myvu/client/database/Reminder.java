package com.myvu.client.database;

public class Reminder {
    private final long id;
    private final String body;
    private final long triggerAt;
    private final String state;
    private final long createdAt;
    private final long updatedAt;
    private final Long firedAt;
    private final int snoozeCount;
    private final int alarmRequestCode;

    public Reminder(long id, String body, long triggerAt, String state, long createdAt, long updatedAt, Long firedAt, int snoozeCount, int alarmRequestCode) {
        this.id = id;
        this.body = body;
        this.triggerAt = triggerAt;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.firedAt = firedAt;
        this.snoozeCount = snoozeCount;
        this.alarmRequestCode = alarmRequestCode;
    }

    public long getId() { return id; }
    public String getBody() { return body; }
    public long getTriggerAt() { return triggerAt; }
    public String getState() { return state; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public Long getFiredAt() { return firedAt; }
    public int getSnoozeCount() { return snoozeCount; }
    public int getAlarmRequestCode() { return alarmRequestCode; }
}
