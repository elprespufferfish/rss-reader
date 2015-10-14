package net.elprespufferfish.rssreader.parsing;

import net.elprespufferfish.rssreader.Article;
import net.elprespufferfish.rssreader.Feed;
import net.elprespufferfish.rssreader.parsing.Articles;
import net.elprespufferfish.rssreader.parsing.BaseParser;
import net.elprespufferfish.rssreader.parsing.Parser;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Parses Atom feeds.
 *
 * @see <a href="https://tools.ietf.org/html/rfc4287">https://tools.ietf.org/html/rfc4287</a>
 */
public class AtomParser extends BaseParser implements Parser {

    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";

    public AtomParser() {
        super("entry");
    }

    @Override
    public Feed parseFeed(String feedAddress, XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        int eventType = xmlPullParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    if ("title".equals(xmlPullParser.getName())) {
                        xmlPullParser.next();
                        String feedName = xmlPullParser.getText();
                        return new Feed.Builder()
                                .withName(feedName)
                                .withUrl(feedAddress)
                                .build();
                    }
                    break;
                }
                default: {
                    // no-op
                }
            }
            eventType = xmlPullParser.next();
        }

        throw new IllegalArgumentException("Could not determine feed title at " + feedAddress);
    }

    @Override
    protected Article parseArticle(String feed, XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        Article.Builder builder = new Article.Builder();
        builder.setFeed(feed);

        String text = null;
        int eventType = xmlPullParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    String namespace = xmlPullParser.getNamespace();

                    String nodeName = xmlPullParser.getName();
                    if (ATOM_NAMESPACE.equals(namespace) && "link".equals(nodeName)) {
                        if ("alternate".equals(xmlPullParser.getAttributeValue(null, "rel"))) {
                            String link = xmlPullParser.getAttributeValue(null, "href");
                            builder.setLink(link);
                            String imageUrl = Articles.getOpenGraphContent(link, "image");
                            builder.setImageUrl(imageUrl);
                        }
                    }
                    break;
                }
                case XmlPullParser.TEXT: {
                    text = xmlPullParser.getText();
                    break;
                }
                case XmlPullParser.END_TAG: {
                    String namespace = xmlPullParser.getNamespace();
                    String nodeName = xmlPullParser.getName();
                    if (ATOM_NAMESPACE.equals(namespace) && "title".equals(nodeName)) {
                        builder.setTitle(text);
                    } else if (ATOM_NAMESPACE.equals(namespace) && "updated".equals(nodeName)) {
                        DateTime publicationDate = ISODateTimeFormat.dateTime().parseDateTime(text);
                        builder.setPublicationDate(publicationDate);
                    } else if (ATOM_NAMESPACE.equals(namespace) && "content".equals(nodeName)) {
                        builder.setDescription(text);
                    } else if (ATOM_NAMESPACE.equals(namespace) && "id".equals(nodeName)) {
                        builder.setGuid(text);
                    } else if (ATOM_NAMESPACE.equals(namespace) && "entry".equals(nodeName)) {
                        // return immediately to ensure we do not advance the
                        // pull parser too far
                        return builder.build();
                    }
                    break;
                }
                default: {
                    // no-op
                }
            }
            eventType = xmlPullParser.next();
        }

        throw new IllegalStateException("Reached end of document without closing </entry>");
    }

}
