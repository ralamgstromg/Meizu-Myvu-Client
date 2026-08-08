package com.myvu.client.database;

public class Note {
    private final long id;
    private final String body;
    private final long createdAt;
    private final long updatedAt;
    private final boolean archived;

    public Note(long id, String body, long createdAt, long updatedAt, boolean archived) {
        this.id = id;
        this.body = body;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archived = archived;
    }

    public long getId() { return id; }
    public String getBody() { return body; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
