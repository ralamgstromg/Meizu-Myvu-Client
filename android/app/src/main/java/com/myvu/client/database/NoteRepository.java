package com.myvu.client.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class NoteRepository {

    private final LocalDatabase dbHelper;

    public NoteRepository(Context context) {
        this.dbHelper = LocalDatabase.getInstance(context);
    }

    public long insertNote(String body) {
        if (body == null || body.trim().isEmpty()) return -1;
        long now = System.currentTimeMillis();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("body", body.trim());
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("archived", 0);
        return db.insert("notes", null, values);
    }

    public List<Note> getActiveNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query("notes",
                new String[]{"id", "body", "created_at", "updated_at", "archived"},
                "archived = 0", null, null, null, "updated_at DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    notes.add(new Note(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getLong(2),
                            cursor.getLong(3),
                            cursor.getInt(4) == 1
                    ));
                } while (cursor.moveToNext());
            }
        }
        return notes;
    }

    public boolean updateNote(long id, String body) {
        if (body == null || body.trim().isEmpty()) return false;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("body", body.trim());
        values.put("updated_at", System.currentTimeMillis());
        return db.update("notes", values, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean archiveNote(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("archived", 1);
        values.put("updated_at", System.currentTimeMillis());
        return db.update("notes", values, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteNote(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete("notes", "id = ?", new String[]{String.valueOf(id)}) > 0;
    }
}
