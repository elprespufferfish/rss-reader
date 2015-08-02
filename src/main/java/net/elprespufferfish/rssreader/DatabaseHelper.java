package net.elprespufferfish.rssreader;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.elprespufferfish.rssreader.DatabaseSchema.ArticleTable;
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
                FeedTable.FEED_URL + " TEXT NOT NULL UNIQUE" +
                ")");

        Map<String, String> defaultFeeds = new HashMap<String, String>();
        defaultFeeds.put("/Film", "http://feeds2.feedburner.com/slashfilm");
        defaultFeeds.put("Autoblog", "http://www.autoblog.com/rss.xml");
        defaultFeeds.put("Engadget", "http://www.engadget.com/rss.xml");
        defaultFeeds.put("LifeHacker", "http://feeds.gawker.com/lifehacker/full");
        defaultFeeds.put("Penny Arcade", "http://feeds.penny-arcade.com/pa-mainsite");
        defaultFeeds.put("xkcd.com", "https://xkcd.com/rss.xml");
        defaultFeeds.put("Android Developers Blog", "http://feeds.feedburner.com/blogspot/hsDu");
        defaultFeeds.put("Google Voice Blog", "http://feeds2.feedburner.com/GoogleVoiceBlog");
        defaultFeeds.put("Planet Gentoo", "http://planet.gentoo.org/rss20.xml");
        defaultFeeds.put("cuteoverload", "http://cuteoverload.com/feed/");

        for (Entry<String, String> feedEntry : defaultFeeds.entrySet()) {
            ContentValues values = new ContentValues();
            values.put(FeedTable.FEED_NAME, feedEntry.getKey());
            values.put(FeedTable.FEED_URL, feedEntry.getValue());
            db.insert(FeedTable.TABLE_NAME, null, values);
        }

        db.execSQL("CREATE TABLE " + ArticleTable.TABLE_NAME + " (" +
                ArticleTable._ID + " INTEGER PRIMARY KEY," +
                ArticleTable.ARTICLE_FEED + " INTEGER NOT NULL," +
                ArticleTable.ARTICLE_NAME + " TEXT NOT NULL," +
                ArticleTable.ARTICLE_URL + " TEXT NOT NULL," +
                ArticleTable.ARTICLE_PUBLICATION_DATE + " INTEGER NOT NULL," +
                ArticleTable.ARTICLE_DESCRIPTION + " TEXT NOT NULL," +
                ArticleTable.ARTICLE_IMAGE_URL + " TEXT," + // may be null if no image is present
                ArticleTable.ARTICLE_GUID + " TEXT NOT NULL," +
                ArticleTable.ARTICLE_IS_READ + " INTEGER NOT NULL," +
                "FOREIGN KEY(" + ArticleTable.ARTICLE_FEED + ") REFERENCES " + FeedTable.TABLE_NAME + "(" + FeedTable._ID + ")" +
                ")");
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
