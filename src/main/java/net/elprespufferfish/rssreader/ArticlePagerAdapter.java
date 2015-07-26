package net.elprespufferfish.rssreader;

import net.elprespufferfish.rssreader.Article.Builder;
import net.elprespufferfish.rssreader.DatabaseSchema.ArticleTable;

import org.joda.time.DateTime;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v7.widget.ShareActionProvider;

public class ArticlePagerAdapter extends FragmentStatePagerAdapter {

    private final SQLiteDatabase database;
    private final ShareActionProvider shareActionProvider;

    public ArticlePagerAdapter(FragmentManager fm, Context context, ShareActionProvider shareActionProvider) {
        super(fm);
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        this.database = databaseHelper.getReadableDatabase();

        this.shareActionProvider = shareActionProvider;
    }

    @Override
    public Fragment getItem(int i) {
        Cursor articleCursor = database.rawQuery("SELECT * FROM " + ArticleTable.TABLE_NAME + " ORDER BY " + ArticleTable.ARTICLE_PUBLICATION_DATE + " DESC LIMIT 1 OFFSET " + i, new String[] {});
        try {
            articleCursor.moveToNext();

            Builder articleBuilder = new Article.Builder();
            articleBuilder.setFeed(articleCursor.getString(1));
            articleBuilder.setTitle(articleCursor.getString(2));
            articleBuilder.setLink(articleCursor.getString(3));
            articleBuilder.setPublicationDate(new DateTime(articleCursor.getInt(4)));
            articleBuilder.setDescription(articleCursor.getString(5));
            articleBuilder.setGuid(articleCursor.getString(6));
            Article article = articleBuilder.build();

            Fragment fragment = new ArticleFragment();
            Bundle bundle = new Bundle();
            bundle.putString(ArticleFragment.TITLE_KEY, article.getTitle());
            bundle.putString(ArticleFragment.LINK_KEY, article.getLink());
            bundle.putString(ArticleFragment.DESCRIPTION_KEY, article.getDescription());
            fragment.setArguments(bundle);

            return fragment;
        } finally {
            articleCursor.close();
        }
    }

    @Override
    public int getCount() {
        Cursor articleCountCursor = database.rawQuery("SELECT Count(*) FROM " + ArticleTable.TABLE_NAME, new String[] {});
        try {
            articleCountCursor.moveToNext();
            return articleCountCursor.getInt(0);
        } finally {
            articleCountCursor.close();
        }
    }

    private void shareArticle(Article article) {
        String textToShare = article.getTitle() + "\n\n" + article.getLink();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, article.getTitle());
        intent.putExtra(Intent.EXTRA_TEXT, textToShare);
        intent.setType("text/plain");

        shareActionProvider.setShareIntent(intent);
    }

}
