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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;

public class Feeds {

    private static final Logger LOGGER = LoggerFactory.getLogger(Feeds.class);
    private static final int MAX_AGE_DAYS = 14; // do not store articles older than this

    private static Feeds INSTANCE;

    public static Feeds initialize(Context context) {
        if (INSTANCE != null) throw new IllegalStateException("initialize() called twice");
        INSTANCE = new Feeds(context);
        return INSTANCE;
    }

    public static Feeds getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("getInstance called before initialize()");
        return INSTANCE;
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final SQLiteDatabase database;
    private final XmlPullParserFactory xmlPullParserFactory;
    private final AtomicBoolean isRefreshInProgress = new AtomicBoolean(false);

    private Feeds(Context context) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        database = databaseHelper.getWritableDatabase();

        try {
            xmlPullParserFactory = XmlPullParserFactory.newInstance();
            xmlPullParserFactory.setNamespaceAware(true);
        } catch (XmlPullParserException e) {
            throw new RuntimeException("Could not instantiate XmlPullParserFactory", e);
        }
    }

    /**
     * @return Feed present at specified address
     * @throws RuntimeException if the title could not be determined
     */
    public Feed getFeed(String feedAddress) {
        InputStream feedInput = null;
        try {
            URL feedUrl = new URL(feedAddress);
            feedInput = feedUrl.openStream();

            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(feedInput, null);

            boolean isInChannel = false;

            int eventType = xmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {

                switch (eventType) {
                    case XmlPullParser.START_TAG: {
                        if ("channel".equals(xmlPullParser.getName())) {
                            isInChannel = true;
                        } else {
                            if (isInChannel && "title".equals(xmlPullParser.getName())) {
                                xmlPullParser.next();
                                String feedName = xmlPullParser.getText();
                                return new Feed.Builder()
                                        .withName(feedName)
                                        .withUrl(feedAddress)
                                        .build();
                            }

                            isInChannel = false;
                        }
                        break;
                    }
                    default: {
                        // no-op
                    }
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not determine feed title at " + feedAddress, e);
        } finally {
            if (feedInput != null) {
                try {
                    feedInput.close();
                } catch (IOException ignored) {
                    // ignored
                }
            }
        }

        throw new IllegalArgumentException("Could not determine feed title at " + feedAddress);
    }

    /**
     * Add a new feed to the database
     * @throws FeedAlreadyAddedException if the feed has already been added
     */
    public void addFeed(Feed feed) throws FeedAlreadyAddedException {
        if (isFeedPresent(feed.getUrl())) throw new FeedAlreadyAddedException();

        ContentValues values = new ContentValues();
        values.put(FeedTable.FEED_NAME, feed.getName());
        values.put(FeedTable.FEED_URL, feed.getUrl());
        database.insertOrThrow(FeedTable.TABLE_NAME, null, values);
    }

    /**
     * @return true if the provided feedUrl is already in the database
     */
    private boolean isFeedPresent(String feedUrl) {
        Cursor feedExistenceCursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + FeedTable.TABLE_NAME + " WHERE " + FeedTable.FEED_URL + "=?",
                new String[] {feedUrl});
        try {
            feedExistenceCursor.moveToNext();
            int numMatchingFeeds = feedExistenceCursor.getInt(0);
            return numMatchingFeeds != 0;
        } finally {
            feedExistenceCursor.close();;
        }
    }

    /**
     * Trigger a refresh of all feeds
     * @return true iff a refresh was started
     */
    public boolean refresh() {
        if (!isRefreshInProgress.compareAndSet(false, true)) {
            LOGGER.info("Refresh already in progress");
            return false;
        }
        LOGGER.info("Starting refresh");
        long startTime = System.nanoTime();

        Set<AsyncTask<String, Void, Void>> tasks = new HashSet<AsyncTask<String, Void, Void>>();
        for (final String feed : getFeeds()) {
            AsyncTask<String, Void, Void> articleFetchingTask = new AsyncTask<String, Void, Void>() {
                @Override
                protected Void doInBackground(String... feeds) {
                    String feedAddress = feeds[0];
                    try {
                        parseFeed(feedAddress);
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

        removeOldArticles();
        return isRefreshInProgress.getAndSet(false);
    }

    private List<String> getFeeds() {
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

    private void parseFeed(String feedAddress) throws IOException, XmlPullParserException {
        LOGGER.info("Attempting to parse " + feedAddress);
        long startTime = System.nanoTime();

        int feedId = getFeedId(feedAddress);
        String latestGuid = getLatestGuid(feedId);
        List<Article> articles = parseArticles(feedAddress, latestGuid);

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

    private int getFeedId(String feedAddress) {
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

    private String getLatestGuid(int feedId) {
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

    private List<Article> parseArticles(String feedAddress, String latestGuid) throws IOException, XmlPullParserException {
        URL feedUrl = new URL(feedAddress);
        InputStream feedInput = feedUrl.openStream();
        try {
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

    private void removeOldArticles() {
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
