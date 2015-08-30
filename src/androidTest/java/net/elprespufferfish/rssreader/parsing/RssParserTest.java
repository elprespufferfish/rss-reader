package net.elprespufferfish.rssreader.parsing;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.ActivityInstrumentationTestCase2;

import net.elprespufferfish.rssreader.Article;
import net.elprespufferfish.rssreader.Feed;
import net.elprespufferfish.rssreader.MainActivity;
import net.elprespufferfish.rssreader.parsing.RssParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(AndroidJUnit4.class)
public class RssParserTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private RssParser parser;

    public RssParserTest() {
        super(MainActivity.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());

        parser = new RssParser();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testParseFeed() throws Exception {
        // given
        String feedAddress = "http://www.todo.com";
        XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
        xmlPullParserFactory.setNamespaceAware(true);
        XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
        InputStream input = getInstrumentation().getContext().getResources().getAssets().open("raw/slashfilm.xml");
        xmlPullParser.setInput(input, null);

        // when
        Feed feed = parser.parseFeed(feedAddress, xmlPullParser);

        // then
        assertThat(feed.getName(), is("/Film"));
        assertThat(feed.getUrl(), is(feedAddress));
    }

    @Test
    public void testParseAllArticles() throws Exception {
        // given
        String feedAddress = "http://www.todo.com";
        XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
        xmlPullParserFactory.setNamespaceAware(true);
        XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
        InputStream input = getInstrumentation().getContext().getResources().getAssets().open("raw/slashfilm.xml");
        xmlPullParser.setInput(input, null);

        // when
        List<Article> articles = parser.parseArticles(feedAddress, xmlPullParser, 1000, null);

        // then
        assertThat(articles.size(), is(20));
        // TODO - validate contents
    }

    @Test
    public void testTitleNotFirst() throws Exception {
        // given
        String feedAddress = "http://www.todo.com";
        XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
        xmlPullParserFactory.setNamespaceAware(true);
        XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
        InputStream input = getInstrumentation().getContext().getResources().getAssets().open("raw/netflix.xml");
        xmlPullParser.setInput(input, null);

        // when
        parser.parseFeed(feedAddress, xmlPullParser);

        // then
        // success
    }

}
