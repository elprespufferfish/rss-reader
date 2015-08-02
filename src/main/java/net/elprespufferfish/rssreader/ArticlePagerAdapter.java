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

    public ArticlePagerAdapter(FragmentManager fm, Context context, String feedUrl) {
        super(fm);
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        this.database = databaseHelper.getReadableDatabase();
        this.feedUrl = feedUrl;
    }

    @Override
    public Fragment getItem(int i) {
        String[] selectionArgs = new String[0];
        String query = "SELECT " +
                ArticleTable.TABLE_NAME + "." + ArticleTable._ID + ", " +
                FeedTable.TABLE_NAME + "." + FeedTable.FEED_NAME + ", " +
                ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_NAME + ", " +
                ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_URL + ", " +
                ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_PUBLICATION_DATE + ", " +
                ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_DESCRIPTION + ", " +
                ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_IMAGE_URL + ", " +
                ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_GUID + " " +
                "FROM " + ArticleTable.TABLE_NAME + " " +
                "JOIN " + FeedTable.TABLE_NAME + " " +
                "ON " + ArticleTable.TABLE_NAME + "." + ArticleTable.ARTICLE_FEED + "=" + FeedTable.TABLE_NAME + "." + FeedTable._ID + " ";
        if (feedUrl != null) {
            query += "WHERE " + FeedTable.FEED_URL + "=? ";
            selectionArgs = new String[] { feedUrl };
        }
        query += "ORDER BY " + ArticleTable.ARTICLE_PUBLICATION_DATE + " DESC " +
                "LIMIT 1 " +
                "OFFSET " + i;
        Cursor articleCursor = database.rawQuery(query, selectionArgs);
        try {
            articleCursor.moveToNext();

            Builder articleBuilder = new Article.Builder();
            articleBuilder.setId(articleCursor.getInt(0));
            articleBuilder.setFeed(articleCursor.getString(1));
            articleBuilder.setTitle(articleCursor.getString(2));
            articleBuilder.setLink(articleCursor.getString(3));
            articleBuilder.setPublicationDate(new DateTime(articleCursor.getLong(4)));
            articleBuilder.setDescription(articleCursor.getString(5));
            articleBuilder.setImageUrl(articleCursor.getString(6));
            articleBuilder.setGuid(articleCursor.getString(7));
            Article article = articleBuilder.build();

            Fragment fragment = new ArticleFragment();
            Bundle bundle = new Bundle();
            bundle.putParcelable(ArticleFragment.ARTICLE_KEY, article);
            fragment.setArguments(bundle);

            return fragment;
        } finally {
            articleCursor.close();
        }
    }

    @Override
    public int getCount() {
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

    /**
     * Release resources
     */
    public void close() {
        database.close();
    }

}
