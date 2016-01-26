package net.elprespufferfish.rssreader.net;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;
import com.google.common.net.HttpHeaders;

import net.elprespufferfish.rssreader.Article;
import net.elprespufferfish.rssreader.Feed;
import net.elprespufferfish.rssreader.db.FeedManager;
import net.elprespufferfish.rssreader.parsing.Parser;
import net.elprespufferfish.rssreader.parsing.ParserFactory;
import net.elprespufferfish.rssreader.settings.Settings;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author elprespufferfish
 */
public class FeedFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedFetcher.class);

    private static final Set<String> HTML_CONTENT_TYPES = ImmutableSet.<String> builder()
            .add("text/html")
            .build();

    private static final Set<String> FEED_CONTENT_TYPES = ImmutableSet.<String> builder()
            .add("application/rss+xml")
            .add("application/atom+xml")
            .add("text/xml")
            .build();


    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final FeedManager feedManager;
    private final XmlPullParserFactory xmlPullParserFactory;
    private final HttpUrlConnectionFactory httpUrlConnectionFactory = new HttpUrlConnectionFactory();
    private final AtomicBoolean isRefreshInProgress = new AtomicBoolean(false);
    private final SharedPreferences preferences;

    public FeedFetcher(
            FeedManager feedManager,
            SharedPreferences preferences) {
        this.feedManager = feedManager;
        this.preferences = preferences;
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
        for (final Feed feed : feedManager.getAllFeeds()) {
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

        feedManager.removeOldArticles();

        return isRefreshInProgress.getAndSet(false);
    }

    private void parseFeed(String feedAddress) throws IOException, XmlPullParserException {
        LOGGER.info("Attempting to parse " + feedAddress);
        long startTime = System.nanoTime();

        int feedId = feedManager.getFeedId(feedAddress);
        String latestGuid = feedManager.getLatestGuid(feedId);
        List<Article> articles = parseArticles(feedAddress, latestGuid);
        try {
            feedManager.addArticles(feedId, articles);
        } finally {
            long endTime = System.nanoTime();
            long durationMs = MILLISECONDS.convert(endTime - startTime, NANOSECONDS);
            LOGGER.info("Finished parsing " + feedAddress + " in " + durationMs + "ms");
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
            int maxAge = preferences.getInt(Settings.RETENTION_PERIOD.first, Settings.RETENTION_PERIOD.second);
            return articleParser.parseArticles(feedAddress, xmlPullParser, maxAge, latestGuid);
        } finally {
            Closeables.closeQuietly(feedInput);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

}
