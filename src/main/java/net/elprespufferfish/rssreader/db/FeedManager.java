package net.elprespufferfish.rssreader.db;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.app.backup.BackupManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import net.elprespufferfish.rssreader.Article;
import net.elprespufferfish.rssreader.Feed;
import net.elprespufferfish.rssreader.db.DatabaseSchema.ArticleTable;
import net.elprespufferfish.rssreader.db.DatabaseSchema.FeedTable;
import net.elprespufferfish.rssreader.settings.Settings;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FeedManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedManager.class);

    private final Context context;
    private final SQLiteDatabase database;
    private final SharedPreferences preferences;

    public FeedManager(
            Context context,
            DatabaseHelper databaseHelper,
            SharedPreferences sharedPreferences) {
        this.context = context.getApplicationContext();
        this.database = databaseHelper.getWritableDatabase();
        this.preferences = sharedPreferences;
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
        new BackupManager(context).dataChanged();
    }

    /**
     * @return true if the provided feedUrl is already in the database.
     */
    private boolean isFeedPresent(String feedUrl) {
        Cursor feedExistenceCursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + FeedTable.TABLE_NAME + " WHERE " + FeedTable.FEED_URL + "=?",
                new String[]{feedUrl});
        try {
            feedExistenceCursor.moveToNext();
            int numMatchingFeeds = feedExistenceCursor.getInt(0);
            return numMatchingFeeds != 0;
        } finally {
            feedExistenceCursor.close();;
        }
    }

    /**
     * @return List of all added FeedManager.
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
     * @return Map of all added FeedManager to the number of unread articles for that feed.
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
                    new String[]{feed.getUrl()});
            database.setTransactionSuccessful();
            new BackupManager(context).dataChanged();
        } finally {
            database.endTransaction();
        }
    }

    public int getFeedId(String feedAddress) {
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

    public String getLatestGuid(int feedId) {
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

    public void addArticles(long feedId, List<Article> articles) {
        String insertSql = "INSERT INTO " + DatabaseSchema.ArticleTable.TABLE_NAME
                + "("
                + DatabaseSchema.ArticleTable.ARTICLE_FEED + ","
                + DatabaseSchema.ArticleTable.ARTICLE_NAME + ","
                + DatabaseSchema.ArticleTable.ARTICLE_URL + ","
                + DatabaseSchema.ArticleTable.ARTICLE_PUBLICATION_DATE + ","
                + DatabaseSchema.ArticleTable.ARTICLE_DESCRIPTION + ","
                + DatabaseSchema.ArticleTable.ARTICLE_IMAGE_URL + ","
                + DatabaseSchema.ArticleTable.ARTICLE_GUID + ","
                + DatabaseSchema.ArticleTable.ARTICLE_IS_READ
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

    public void removeOldArticles() {
        long startTime = System.nanoTime();

        int maxAge = preferences.getInt(Settings.RETENTION_PERIOD.first, Settings.RETENTION_PERIOD.second);
        DateTime oldestDate = DateTime.now().minusDays(maxAge);
        LOGGER.info("Deleting old articles older than {}", oldestDate.getMillis());
        int deletedRows = database.delete(
                ArticleTable.TABLE_NAME,
                ArticleTable.ARTICLE_PUBLICATION_DATE + " < ?",
                new String[] { String.valueOf(oldestDate.getMillis()) });

        long endTime = System.nanoTime();
        long durationMs = MILLISECONDS.convert(endTime - startTime, NANOSECONDS);
        LOGGER.info("Done deleting {} old articles in {}ms", deletedRows, durationMs);
    }

}
