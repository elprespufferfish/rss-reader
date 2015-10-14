package net.elprespufferfish.rssreader;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.elprespufferfish.rssreader.DatabaseSchema.ArticleTable;
import net.elprespufferfish.rssreader.DatabaseSchema.FeedTable;
import net.elprespufferfish.rssreader.net.HttpUrlConnectionFactory;
import net.elprespufferfish.rssreader.parsing.Parser;
import net.elprespufferfish.rssreader.parsing.ParserFactory;

import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;
import com.google.common.net.HttpHeaders;

public class Feeds {

    private static final Logger LOGGER = LoggerFactory.getLogger(Feeds.class);
    private static final int MAX_AGE_DAYS = 14; // do not store articles older than this

    private static final Set<String> HTML_CONTENT_TYPES;

    static {
        Set<String> tmp = new HashSet<>();
        tmp.add("text/html");
        HTML_CONTENT_TYPES = ImmutableSet.copyOf(tmp);
    }

    private static final Set<String> FEED_CONTENT_TYPES;

    static {
        Set<String> tmp = new HashSet<>();
        tmp.add("application/rss+xml");
        tmp.add("application/atom+xml");
        tmp.add("text/xml");
        FEED_CONTENT_TYPES = ImmutableSet.copyOf(tmp);
    }

    private static volatile Feeds INSTANCE;

    /**
     * Initialize the singleton.
     *
     * @throws IllegalStateException if initialize has already been called.
     */
    public static Feeds initialize(Context context) {
        if (INSTANCE != null) {
            throw new IllegalStateException("initialize() called twice");
        }
        INSTANCE = new Feeds(context);
        return INSTANCE;
    }

    /**
     * @return initialized singleton.
     * @throws IllegalStateException if initialize has not been called.
     */
    public static Feeds getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("getInstance called before initialize()");
        }
        return INSTANCE;
    }

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final SQLiteDatabase database;
    private final XmlPullParserFactory xmlPullParserFactory;
    private final HttpUrlConnectionFactory httpUrlConnectionFactory = new HttpUrlConnectionFactory();
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
     * @return Feed present at specified address, or List of autodiscovered feeds.
     * @throws RuntimeException if the title could not be determined.
     */
    public List<Feed> getFeeds(String feedAddress) {
        HttpURLConnection connection = null;
        try {
            connection = httpUrlConnectionFactory.create(new URL(feedAddress));

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Could not fetch " + feedAddress);
            }

            String contentType = parseContentType(connection.getHeaderField(HttpHeaders.CONTENT_TYPE));
            if (isHtml(contentType)) {
                return autoDiscoverFeeds(connection, feedAddress);
            } else if (isFeed(contentType)) {
                return Collections.singletonList(getFeed(connection, feedAddress));
            } else {
                throw new RuntimeException("Cannot handle content type '" + contentType + "'");
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String parseContentType(String contentTypeHeader) {
        // TODO - make less shitty
        String[] parts = contentTypeHeader.split(";");
        return parts[0];
    }

    private boolean isHtml(String contentType) {
        return HTML_CONTENT_TYPES.contains(contentType);
    }

    private boolean isFeed(String contentType) {
        return FEED_CONTENT_TYPES.contains(contentType);
    }

    /**
     * Attempt to autodiscover feeds as described at <a href="http://www.rssboard.org/rss-autodiscovery">http://www.rssboard.org/rss-autodiscovery</a>
     * @return List of autodiscovered RSS feeds
     */
    private List<Feed> autoDiscoverFeeds(HttpURLConnection connection, String discoveryAddress) {
        List<Feed> feeds = new LinkedList<>();

        try {
            Document document = Jsoup.parse(connection.getInputStream(), null, discoveryAddress);
            Elements elements = document.select("link[rel=alternate][type=application/rss+xml]");
            for (Element element : elements) {
                String feedUrl = element.attr("href");
                if (feedUrl.startsWith("/")) {
                    // ensure feedUrl is absolute
                    URL discoveryUrl = new URL(discoveryAddress);
                    feedUrl = discoveryUrl.getProtocol() + "://" + discoveryUrl.getHost() + (discoveryUrl.getPort() != -1 ? ":" + discoveryUrl.getPort() : "") + feedUrl;
                }

                // unfortunately, we can't trust that the provided 'title' is accurate
                // so we re-fetch
                Feed feed = getFeed(feedUrl);
                feeds.add(feed);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not discover feeds at " + discoveryAddress, e);
        }

        return feeds;
    }

    private Feed getFeed(String feedAddress) {
        HttpURLConnection connection = null;
        try {
            connection = httpUrlConnectionFactory.create(new URL(feedAddress));

            return getFeed(connection, feedAddress);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Feed getFeed(HttpURLConnection connection, String feedAddress) {
        InputStream feedInput = null;
        try {
            feedInput = connection.getInputStream();

            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(feedInput, null);

            Parser feedParser = ParserFactory.newParser(xmlPullParser);
            return feedParser.parseFeed(feedAddress, xmlPullParser);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Unable to parse " + feedAddress, e);
        } finally {
            Closeables.closeQuietly(feedInput);
        }
    }

    /**
     * Add a new feed to the database.
     * @throws FeedAlreadyAddedException if the feed has already been added.
     */
    public void addFeed(Feed feed) throws FeedAlreadyAddedException {
        if (isFeedPresent(feed.getUrl())) {
            throw new FeedAlreadyAddedException();
        }

        ContentValues values = new ContentValues();
        values.put(FeedTable.FEED_NAME, feed.getName());
        values.put(FeedTable.FEED_URL, feed.getUrl());
        database.insertOrThrow(FeedTable.TABLE_NAME, null, values);
    }

    /**
     * @return true if the provided feedUrl is already in the database.
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
     * Trigger a refresh of all feeds.
     * @return true iff a refresh was started.
     */
    public boolean refresh() {
        if (!isRefreshInProgress.compareAndSet(false, true)) {
            LOGGER.info("Refresh already in progress");
            return false;
        }
        LOGGER.info("Starting refresh");
        long startTime = System.nanoTime();

        Set<AsyncTask<String, Void, Void>> tasks = new HashSet<AsyncTask<String, Void, Void>>();
        for (final Feed feed : getAllFeeds()) {
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
            };
            articleFetchingTask.executeOnExecutor(executor, feed.getUrl());
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

    /**
     * @return List of all added Feeds.
     */
    public List<Feed> getAllFeeds() {
        Cursor feedCursor = database.query(
                FeedTable.TABLE_NAME,
                new String[]{FeedTable.FEED_NAME, FeedTable.FEED_URL},
                null,
                null,
                null,
                null,
                null);
        try {
            feedCursor.moveToFirst();
            List<Feed> feeds = new LinkedList<Feed>();
            while (!feedCursor.isAfterLast()) {
                Feed feed = new Feed.Builder().withName(feedCursor.getString(0)).withUrl(feedCursor.getString(1)).build();
                feeds.add(feed);
                feedCursor.moveToNext();
            }
            return feeds;
        } finally {
            feedCursor.close();
        }
    }

    /**
     * @return Map of all added Feeds to the number of unread articles for that feed.
     */
    public Map<Feed, Integer> getUnreadArticleCounts() {
        Map<Feed, Integer> feeds = new TreeMap<>(new Comparator<Feed>() {
            @Override
            public int compare(Feed lhs, Feed rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });

        Cursor feedCursor = database.rawQuery(
                "SELECT " + FeedTable.FEED_NAME + ", " + FeedTable.FEED_URL + ", COUNT(CASE WHEN " + ArticleTable.ARTICLE_IS_READ + "=" + DatabaseSchema.ReadStatus.UNREAD + " THEN 1 END) "
                        + "FROM " + FeedTable.TABLE_NAME + " "
                        + "LEFT JOIN " + ArticleTable.TABLE_NAME + " "
                        + "ON " + FeedTable.TABLE_NAME + "." + FeedTable._ID + "=" + ArticleTable.ARTICLE_FEED + " "
                        + "GROUP BY " + FeedTable.TABLE_NAME + "." + FeedTable._ID + " "
                        + "ORDER BY " + FeedTable.FEED_NAME,
                new String[0]);
        try {
            feedCursor.moveToFirst();

            while (!feedCursor.isAfterLast()) {
                Feed feed = new Feed.Builder().withName(feedCursor.getString(0)).withUrl(feedCursor.getString(1)).build();
                feeds.put(feed, feedCursor.getInt(2));
                feedCursor.moveToNext();
            }

            return feeds;
        } finally {
            feedCursor.close();
        }
    }

    /**
     * Remove provided Feed and all associated Articles.
     */
    public void removeFeed(Feed feed) {
        database.beginTransaction();
        try {
            database.rawQuery(
                    "DELETE FROM " + ArticleTable.TABLE_NAME + " "
                            + "WHERE " + ArticleTable.ARTICLE_FEED + "=" + "(SELECT " + FeedTable._ID + " "
                            + "FROM " + FeedTable.TABLE_NAME + " "
                            + "WHERE " + FeedTable.FEED_URL + "=?)",
                    new String[0]);
            database.delete(
                    FeedTable.TABLE_NAME,
                    FeedTable.FEED_URL + "=?",
                    new String[] { feed.getUrl() });
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private void parseFeed(String feedAddress) throws IOException, XmlPullParserException {
        LOGGER.info("Attempting to parse " + feedAddress);
        long startTime = System.nanoTime();

        int feedId = getFeedId(feedAddress);
        String latestGuid = getLatestGuid(feedId);
        List<Article> articles = parseArticles(feedAddress, latestGuid);

        String insertSql = "INSERT INTO " + ArticleTable.TABLE_NAME
                + "("
                + ArticleTable.ARTICLE_FEED + ","
                + ArticleTable.ARTICLE_NAME + ","
                + ArticleTable.ARTICLE_URL + ","
                + ArticleTable.ARTICLE_PUBLICATION_DATE + ","
                + ArticleTable.ARTICLE_DESCRIPTION + ","
                + ArticleTable.ARTICLE_IMAGE_URL + ","
                + ArticleTable.ARTICLE_GUID + ","
                + ArticleTable.ARTICLE_IS_READ
                + ") VALUES (?,?,?,?,?,?,?, ?);";
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
                if (article.getImageUrl() != null) {
                    statement.bindString(6, article.getImageUrl());
                }
                statement.bindString(7, article.getGuid());
                statement.bindLong(8, 0);
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
                new String[]{FeedTable._ID, FeedTable.FEED_URL},
                FeedTable.FEED_URL + " = ?",
                new String[]{feedAddress},
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
        HttpURLConnection connection = httpUrlConnectionFactory.create(new URL(feedAddress));
        InputStream feedInput = null;
        try {
            feedInput = connection.getInputStream();

            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(feedInput, null);

            Parser articleParser = ParserFactory.newParser(xmlPullParser);
            return articleParser.parseArticles(feedAddress, xmlPullParser, MAX_AGE_DAYS, latestGuid);
        } finally {
            Closeables.closeQuietly(feedInput);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Mark article as having been read, but still visible in the current pager.
     */
    public void markArticleGrey(Article article) {
        ContentValues values = new ContentValues();
        values.put(ArticleTable.ARTICLE_IS_READ, DatabaseSchema.ReadStatus.GREY.toString());
        LOGGER.debug("Marking {} as grey", article.getId());
        database.update(ArticleTable.TABLE_NAME,
                values,
                ArticleTable._ID + "=?",
                new String[] { Integer.toString(article.getId()) });
    }

    /**
     * Mark all grey articles as read so that they will not show up.
     */
    public void finalizeGreyArticles() {
        ContentValues values = new ContentValues();
        values.put(ArticleTable.ARTICLE_IS_READ, DatabaseSchema.ReadStatus.READ.toString());
        LOGGER.debug("Transitioning grey articles to read");
        database.update(ArticleTable.TABLE_NAME,
                values,
                ArticleTable.ARTICLE_IS_READ + "=" + DatabaseSchema.ReadStatus.GREY,
                new String[0]);
    }

    /**
     * Mark all articles for the provided Feed as read.
     */
    public void markAllAsRead(Feed feed) {
        if (feed.getUrl() == null) {
            ContentValues values = new ContentValues();
            values.put(ArticleTable.ARTICLE_IS_READ, DatabaseSchema.ReadStatus.READ.toString());
            database.update(ArticleTable.TABLE_NAME,
                    values,
                    "", new String[0]);
        } else {
            database.execSQL("UPDATE " + ArticleTable.TABLE_NAME + " "
                    + "SET " + ArticleTable.ARTICLE_IS_READ + "=" + DatabaseSchema.ReadStatus.READ + " "
                    + "WHERE " + ArticleTable.ARTICLE_FEED + "= "
                    + "(SELECT " + FeedTable._ID + " FROM " + FeedTable.TABLE_NAME + " WHERE " + FeedTable.FEED_URL + "='" + feed.getUrl() + "')");
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
