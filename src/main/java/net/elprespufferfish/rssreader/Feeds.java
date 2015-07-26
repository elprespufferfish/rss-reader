package net.elprespufferfish.rssreader;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.elprespufferfish.rssreader.DatabaseSchema.ArticleTable;
import net.elprespufferfish.rssreader.DatabaseSchema.FeedTable;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;

public class Feeds {

    private static final Logger LOGGER = LoggerFactory.getLogger(Feeds.class);
    private static final int MAX_AGE_DAYS = 14; // do not store articles older than this

    private static Feeds INSTANCE;

    public static Feeds initialize() {
        if (INSTANCE != null) throw new IllegalStateException("initialize() called twice");
        INSTANCE = new Feeds();
        return INSTANCE;
    }

    public static Feeds getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("getInstance called before initialize()");
        return INSTANCE;
    }

    private final AtomicBoolean isRefreshInProgress = new AtomicBoolean(false);

    private Feeds() {}

    /**
     * Trigger a refresh of all feeds
     * @return true iff a refresh was started
     */
    public boolean refresh(final SQLiteDatabase database) {
        if (!isRefreshInProgress.compareAndSet(false, true)) {
            LOGGER.info("Refresh already in progress");
            return false;
        }
        LOGGER.info("Starting refresh");
        long startTime = System.nanoTime();

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        Set<AsyncTask<String, Void, Void>> tasks = new HashSet<AsyncTask<String, Void, Void>>();
        for (final String feed : getFeeds(database)) {
            AsyncTask<String, Void, Void> articleFetchingTask = new AsyncTask<String, Void, Void>() {
                @Override
                protected Void doInBackground(String... feeds) {
                    String feedAddress = feeds[0];
                    try {
                        parseFeed(database, feedAddress);
                    } catch (Exception e) {
                        LOGGER.error("Could not parse feed " + feedAddress, e);
                    }
                    return null;
                }
            }.executeOnExecutor(executor, feed);
            tasks.add(articleFetchingTask);
        }

        for (AsyncTask<String, Void, Void> task : tasks) {
            try {
                task.get();
            } catch (Exception e) {
                LOGGER.error("Could not get articles", e);
            }
        }

        long endTime = System.nanoTime();
        long durationMs = MILLISECONDS.convert(endTime - startTime, NANOSECONDS);
        LOGGER.info("Refresh complete in " + durationMs + "ms");

        removeOldArticles(database);
        return isRefreshInProgress.getAndSet(false);
    }

    private List<String> getFeeds(SQLiteDatabase database) {
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
    }

    private void parseFeed(SQLiteDatabase database, String feedAddress) throws IOException, XmlPullParserException {
        LOGGER.info("Attempting to parse " + feedAddress);
        long startTime = System.nanoTime();

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
        long endTime = System.nanoTime();
        long durationMs = MILLISECONDS.convert(endTime - startTime, NANOSECONDS);
        LOGGER.info("Finished parsing " + feedAddress + " in " + durationMs + "ms");
    }

    private int getFeedId(SQLiteDatabase database, String feedAddress) {
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

    private String getLatestGuid(SQLiteDatabase database, int feedId) {
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

    private List<Article> parseArticles(SQLiteDatabase database, String feedAddress, String latestGuid) throws IOException, XmlPullParserException {
        URL feedUrl = new URL(feedAddress);
        InputStream feedInput = feedUrl.openStream();
        try {
            XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
            xmlPullParserFactory.setNamespaceAware(true);
            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(feedInput, null);

            List<Article> articles = new LinkedList<Article>();

            DateTime maxArticleAge = DateTime.now().minusDays(MAX_AGE_DAYS);

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

                        if (article.getPublicationDate().isBefore(maxArticleAge)) {
                            // too far back in feed
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

    private void removeOldArticles(SQLiteDatabase database) {
        LOGGER.info("Deleting old articles");
        long startTime = System.nanoTime();

        DateTime oldestDate = DateTime.now().minusDays(MAX_AGE_DAYS);
        int deletedRows = database.delete(
                ArticleTable.TABLE_NAME,
                "? < ?",
                new String[] { ArticleTable.ARTICLE_PUBLICATION_DATE, String.valueOf(oldestDate.getMillis()) });

        long endTime = System.nanoTime();
        long durationMs = MILLISECONDS.convert(endTime - startTime, NANOSECONDS);
        LOGGER.info("Done deleting {} old articles in {}ms", deletedRows, durationMs);
    }

}
