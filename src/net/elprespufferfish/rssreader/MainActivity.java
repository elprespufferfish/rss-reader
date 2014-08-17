package net.elprespufferfish.rssreader;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.ProgressDialog;
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
                List<Article> allArticles = new LinkedList<Article>();
                for (String feedAddress : getFeeds()) {
                    List<Article> articles = parseFeed(feedAddress);
                    allArticles.addAll(articles);
                }
                // sort by date
                Collections.sort(allArticles);
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
        List<String> feeds = new LinkedList<String>();
        feeds.add("http://cuteoverload.com/feed/"); // content:encoded
        feeds.add("http://cabinporn.com/rss"); // no inline content
        return feeds;
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
