package net.elprespufferfish.rssreader;

import net.elprespufferfish.rssreader.Article.Builder;
import net.elprespufferfish.rssreader.DatabaseSchema.ArticleTable;
import net.elprespufferfish.rssreader.DatabaseSchema.FeedTable;

import org.joda.time.DateTime;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class ArticlePagerAdapter extends FragmentStatePagerAdapter {

    private final SQLiteDatabase database;
    /**
     * Feed URL that we are paging through
     * NOTE: May be null to represent 'all feeds'
     */
    private final String feedUrl;
    private final int count;

    public ArticlePagerAdapter(FragmentManager fm, Context context, String feedUrl) {
        super(fm);
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        this.database = databaseHelper.getReadableDatabase();
        this.feedUrl = feedUrl;
        this.count = computeCount();
    }

    private int computeCount() {
        String[] selectionArgs = new String[0];
        String query = "SELECT COUNT(*)" +
                "FROM " + ArticleTable.TABLE_NAME + " ";
        if (feedUrl != null) {
            query += "JOIN " + FeedTable.TABLE_NAME + " " +
                    "ON " + ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_FEED + "=" + FeedTable.TABLE_NAME + "." + FeedTable._ID + " " +
                    "WHERE " + FeedTable.FEED_URL + "=? ";
            selectionArgs = new String[] { feedUrl };
        }

        Cursor articleCountCursor = database.rawQuery(query, selectionArgs);
        try {
            articleCountCursor.moveToNext();
            return articleCountCursor.getInt(0);
        } finally {
            articleCountCursor.close();
        }
    }

    @Override
    public Fragment getItem(int i) {
        Fragment fragment = new ArticleFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ArticleFragment.ARTICLE_FEED_URL_KEY, feedUrl);
        bundle.putInt(ArticleFragment.ARTICLE_INDEX_KEY, i);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public int getCount() {
        return count;
    }

    /**
     * Release resources
     */
    public void close() {
        database.close();
    }

}
