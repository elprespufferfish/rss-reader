package net.elprespufferfish.rssreader.parsing;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class ParserFactory {

    /**
     * @param xmlPullParser must not be advanced beyond start of stream.
     * @return {@link Parser} appropriate for this stream
     * @throws IllegalArgumentException if the stream type is unknown
     * @throws XmlPullParserException if the document cannot be parsed
     * @throws IOException if the document cannot be parsed
     */
    public static Parser newParser(XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        int tokenType = xmlPullParser.getEventType();
        if (tokenType != XmlPullParser.START_DOCUMENT) {
            throw new IllegalStateException("XmlPullParser must be at start of document to determine type.  Was at " + tokenType);
        }

        xmlPullParser.next();
        String tokenName = xmlPullParser.getName();

        if (isRss(tokenName)) {
            return new RssParser();
        } else if (isAtom(tokenName)) {
            return new AtomParser();
        } else {
            throw new IllegalStateException("Could not determine feed type.  Feed started with '" + tokenName + "'");
        }
    }

    private static boolean isRss(String openingTokenName) {
        return "rss".equals(openingTokenName);
    }

    private static boolean isAtom(String openingTokenName) {
        return "feed".equals(openingTokenName);
    }

}
