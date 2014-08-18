package net.elprespufferfish.rssreader;

import net.elprespufferfish.rssreader.DatabaseSchema.FeedTable;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "rssReader.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DatabaseSchema.FeedTable.TABLE_NAME + " (" +
                FeedTable._ID + " INTEGER PRIMARY KEY," +
                FeedTable.FEED_NAME + " TEXT NOT NULL," +
                FeedTable.FEED_URL + " TEXT NOT NULL" +
                ")");

        ContentValues values = new ContentValues();
        values.put(FeedTable.FEED_NAME, "cuteoverload");
        values.put(FeedTable.FEED_URL, "http://cuteoverload.com/feed/");
        db.insert(FeedTable.TABLE_NAME, null, values);

        values.clear();
        values.put(FeedTable.FEED_NAME, "cabinporn");
        values.put(FeedTable.FEED_URL, "http://cabinporn.com/rss");
        db.insert(FeedTable.TABLE_NAME, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO
    }

}
