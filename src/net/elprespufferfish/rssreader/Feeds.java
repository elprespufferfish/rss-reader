package net.elprespufferfish.rssreader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import net.elprespufferfish.rssreader.DatabaseSchema.ArticleTable;
import net.elprespufferfish.rssreader.DatabaseSchema.FeedTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

public class Feeds {

    private static final Logger LOGGER = LoggerFactory.getLogger(Feeds.class);

    public static List<String> getFeeds(Context context) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        try {
            Cursor feedCursor = database.query(
                    FeedTable.TABLE_NAME,
                    new String[] { FeedTable.FEED_NAME, FeedTable.FEED_URL },
                    null,
                    null,
                    null,
                    null,
                    null);
            try {
                feedCursor.moveToFirst();
                List<String> feeds = new LinkedList<String>();
                while (!feedCursor.isAfterLast()) {
                    String feedUrl = feedCursor.getString(1);
                    feeds.add(feedUrl);
                    feedCursor.moveToNext();
                }
                return feeds;
            } finally {
                feedCursor.close();
            }
        } finally {
            database.close();
        }
    }

    public static void parseFeed(Context context, SQLiteDatabase database, String feedAddress) throws IOException, XmlPullParserException {
        LOGGER.info("Attempting to parse " + feedAddress);
        int feedId = getFeedId(database, feedAddress);
        String latestGuid = getLatestGuid(database, feedId);
        List<Article> articles = parseArticles(database, feedAddress, latestGuid);

        String insertSql = "INSERT INTO " + ArticleTable.TABLE_NAME +
                "(" +
                ArticleTable.ARTICLE_FEED + "," +
                ArticleTable.ARTICLE_NAME + "," +
                ArticleTable.ARTICLE_URL + "," +
                ArticleTable.ARTICLE_PUBLICATION_DATE + "," +
                ArticleTable.ARTICLE_DESCRIPTION + "," +
                ArticleTable.ARTICLE_GUID + "," +
                ArticleTable.ARTICLE_IS_READ +
                ") VALUES (?,?,?,?,?,?,?);";
        SQLiteStatement statement = database.compileStatement(insertSql);
        database.beginTransactionNonExclusive();
        try {
            for (Article article : articles) {
                LOGGER.info("Parsed article: " + article.getGuid());
                statement.clearBindings();
                statement.bindLong(1, feedId);
                statement.bindString(2, article.getTitle());
                statement.bindString(3, article.getLink());
                statement.bindLong(4, article.getPublicationDate().getMillis());
                statement.bindString(5, article.getDescription());
                statement.bindString(6, article.getGuid());
                statement.bindLong(7, 0);
                statement.execute();
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        LOGGER.info("Finished parsing " + feedAddress);
    }

    private static int getFeedId(SQLiteDatabase database, String feedAddress) {
        Cursor feedCursor = database.query(
                FeedTable.TABLE_NAME,
                new String[] { FeedTable._ID, FeedTable.FEED_URL },
                FeedTable.FEED_URL + " = ?",
                new String[] { feedAddress },
                null,
                null,
                null);
        try {
            feedCursor.moveToFirst();
            int feedId = feedCursor.getInt(0);
            return feedId;
        } finally {
            feedCursor.close();
        }
    }

    private static String getLatestGuid(SQLiteDatabase database, int feedId) {
        Cursor latestGuidCursor = database.query(
                ArticleTable.TABLE_NAME,
                new String[] { ArticleTable.ARTICLE_GUID },
                ArticleTable.ARTICLE_FEED + " = ?",
                new String[] { String.valueOf(feedId) },
                null,
                null,
                ArticleTable.ARTICLE_PUBLICATION_DATE + " DESC",
                "1");
        try {
            if (latestGuidCursor.isAfterLast()) {
                // no results, new feed
                return null;
            }
            latestGuidCursor.moveToFirst();
            return latestGuidCursor.getString(0);
        } finally {
            latestGuidCursor.close();
        }
    }

    private static List<Article> parseArticles(SQLiteDatabase database, String feedAddress, String latestGuid) throws IOException, XmlPullParserException {
        URL feedUrl = new URL(feedAddress);
        InputStream feedInput = feedUrl.openStream();
        try {
            XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
            xmlPullParserFactory.setNamespaceAware(true);
            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(feedInput, null);

            List<Article> articles = new LinkedList<Article>();

            int eventType = xmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                case XmlPullParser.START_TAG: {
                    if ("item".equals(xmlPullParser.getName())) {
                        Article article = Articles.fromXml(feedAddress, xmlPullParser);

                        if (latestGuid != null && latestGuid.equals(article.getGuid())) {
                            // already read this part of the feed
                            return articles;
                        }

                        articles.add(article);
                    }
                    break;
                }
                default: {
                    // no-op
                }
                }
                eventType = xmlPullParser.next();
            }
            return articles;
        } finally {
            feedInput.close();
        }
    }

    private Feeds() {
        // prevent instantiation
    }
}
