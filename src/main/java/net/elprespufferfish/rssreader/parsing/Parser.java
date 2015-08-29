package net.elprespufferfish.rssreader.parsing;

import net.elprespufferfish.rssreader.Article;
import net.elprespufferfish.rssreader.Feed;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

/**
 * Parses article feeds
 */
public interface Parser {

    /**
     * Parse a {@link Feed} object from the provided XML stream.
     */
    Feed parseFeed(String feedAddress, XmlPullParser xmlPullParser) throws XmlPullParserException, IOException;

    /**
     * Parse {@link Article} objects from the provided XML stream.
     *
     * @param feedAddress
     * @param xmlPullParser
     * @param maxAgeDays
     * @param latestGuid
     * @return
     * @throws IOException
     * @throws XmlPullParserException
     */
    List<Article> parseArticles(String feedAddress, XmlPullParser xmlPullParser, int maxAgeDays, String latestGuid) throws IOException, XmlPullParserException;

}