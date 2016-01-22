package net.elprespufferfish.rssreader;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import net.elprespufferfish.rssreader.DatabaseSchema.ArticleTable;
import net.elprespufferfish.rssreader.DatabaseSchema.FeedTable;

public class ArticlePagerAdapter extends FragmentStatePagerAdapter {

    private final DatabaseHelper databaseHelper;
    /**
     * Feed URL that we are paging through.
     * NOTE: May be null to represent 'all feeds'.
     */
    private final String feedUrl;
    private final int count;
    private final boolean isHidingReadArticles;

    /**
     * Instantiate a new adapter.
     */
    public ArticlePagerAdapter(FragmentManager fm, DatabaseHelper databaseHelper, String feedUrl, boolean isHidingReadArticles) {
        super(fm);
        this.databaseHelper = databaseHelper;
        this.feedUrl = feedUrl;
        this.isHidingReadArticles = isHidingReadArticles;
        this.count = computeCount();
    }

    private int computeCount() {
        String[] selectionArgs = new String[0];
        String query = "SELECT COUNT(*)"
                + "FROM " + ArticleTable.TABLE_NAME + " ";
        if (feedUrl != null) {
            query += "JOIN " + FeedTable.TABLE_NAME + " "
                    + "ON " + ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_FEED + "=" + FeedTable.TABLE_NAME + "." + FeedTable._ID + " "
                    + "WHERE " + FeedTable.FEED_URL + "=? ";
            selectionArgs = new String[] { feedUrl };
            if (isHidingReadArticles) {
                query += "AND " + DatabaseSchema.ArticleTable.ARTICLE_IS_READ + "!=" + DatabaseSchema.ReadStatus.READ + " ";
            }
        } else {
            if (isHidingReadArticles) {
                query += "WHERE " + DatabaseSchema.ArticleTable.ARTICLE_IS_READ + "!=" + DatabaseSchema.ReadStatus.READ + " ";
            }
        }

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor articleCountCursor = database.rawQuery(query, selectionArgs);
        try {
            articleCountCursor.moveToNext();
            return articleCountCursor.getInt(0);
        } finally {
            articleCountCursor.close();
            database.close();
        }
    }

    @Override
    public Fragment getItem(int index) {
        if (index == count) {
            return new EndOfLineFragment();
        }

        Bundle bundle = new Bundle();
        bundle.putString(ArticleFragment.ARTICLE_FEED_URL_KEY, feedUrl);
        bundle.putInt(ArticleFragment.ARTICLE_INDEX_KEY, index);
        bundle.putBoolean(ArticleFragment.ARTICLE_IS_HIDING_READ_ARTICLES_KEY, isHidingReadArticles);

        Fragment fragment = new ArticleFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public int getCount() {
        if (count > 0) {
            // add an extra item for the 'end of the line' message
            return count + 1;
        } else {
            return count;
        }
    }

}
