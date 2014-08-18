package net.elprespufferfish.rssreader;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.elprespufferfish.rssreader.DatabaseSchema.FeedTable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.ProgressDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;

public class MainActivity extends FragmentActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final ProgressDialog dialog = ProgressDialog.show(this, "", "Loading new articles...", true);

        new AsyncTask<Void, Void, List<Article>>() {
            @Override
            protected List<Article> doInBackground(Void... params) {
                Log.i("rss-reader", "Parsing feeds in the background");
                final List<Article> allArticles = Collections.synchronizedList(new LinkedList<Article>());

                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

                Set<AsyncTask<String, Void, Void>> tasks = new HashSet<AsyncTask<String, Void, Void>>();
                for (final String feed : getFeeds()) {
                    AsyncTask<String, Void, Void> articleFetchingTask = new AsyncTask<String, Void, Void>() {
                        @Override
                        protected Void doInBackground(String... feeds) {
                            List<Article> articles = parseFeed(feeds[0]);
                            allArticles.addAll(articles);
                            return null;
                        }
                    }.executeOnExecutor(executor, feed);
                    tasks.add(articleFetchingTask);
                }

                for (AsyncTask<String, Void, Void> task : tasks) {
                    try {
                        task.get();
                    } catch (InterruptedException e) {
                        Log.e("rss-reader", "Could not get articles", e);
                    } catch (ExecutionException e) {
                        Log.e("rss-reader", "Could not get articles", e);
                    }
                }

                // sort by date
                Collections.sort(allArticles, new Comparator<Article>() {
                    /**
                     * Sort newest to oldest
                     */
                    @Override
                    public int compare(Article lhs, Article rhs) {
                        return rhs.getPublicationDate().compareTo(lhs.getPublicationDate());
                    }
                });
                Log.i("rss-reader", "Done parsing feeds in the background");
                return allArticles;
            }

            @Override
            protected void onPostExecute(List<Article> articles) {
                ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
                viewPager.setAdapter(new ArticlePagerAdapter(MainActivity.this.getSupportFragmentManager(), articles));

                dialog.dismiss();
            }

        }.execute();
    }

    private List<String> getFeeds() {
        DatabaseHelper databaseHelper = new DatabaseHelper(this);
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

    private List<Article> parseFeed(String feedAddress) {
        Log.i("rss-reader", "Attempting to parse " + feedAddress);
        try {
            URL feedUrl = new URL(feedAddress);
            InputStream feedInput = feedUrl.openStream();
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
                        articles.add(Article.fromXml(xmlPullParser));
                    }
                    break;
                }
                default: {
                    // no-op
                }
                }
                eventType = xmlPullParser.next();
            }
            Log.i("rss-reader", "Finished parsing " + feedAddress);

            feedInput.close();
            return articles;
        } catch (Exception e) {
            throw new RuntimeException("Could not parse " + feedAddress, e);
        }
    }

}
