package net.elprespufferfish.rssreader.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.elprespufferfish.rssreader.db.DatabaseSchema.ArticleTable;
import net.elprespufferfish.rssreader.db.DatabaseSchema.FeedTable;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "rssReader.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + DatabaseSchema.FeedTable.TABLE_NAME + " ("
                + FeedTable._ID + " INTEGER PRIMARY KEY,"
                + FeedTable.FEED_NAME + " TEXT NOT NULL,"
                + FeedTable.FEED_URL + " TEXT NOT NULL UNIQUE"
                + ")");

        db.execSQL("CREATE TABLE " + ArticleTable.TABLE_NAME + " ("
                + ArticleTable._ID + " INTEGER PRIMARY KEY,"
                + ArticleTable.ARTICLE_FEED + " INTEGER NOT NULL,"
                + ArticleTable.ARTICLE_NAME + " TEXT NOT NULL,"
                + ArticleTable.ARTICLE_URL + " TEXT NOT NULL,"
                + ArticleTable.ARTICLE_PUBLICATION_DATE + " INTEGER NOT NULL,"
                + ArticleTable.ARTICLE_DESCRIPTION + " TEXT NOT NULL,"
                + ArticleTable.ARTICLE_IMAGE_URL + " TEXT," // may be null if no image is present
                + ArticleTable.ARTICLE_GUID + " TEXT NOT NULL,"
                + ArticleTable.ARTICLE_IS_READ + " INTEGER NOT NULL,"
                + "FOREIGN KEY(" + ArticleTable.ARTICLE_FEED + ") REFERENCES " + FeedTable.TABLE_NAME + "(" + FeedTable._ID + ")"
                + ")");
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
