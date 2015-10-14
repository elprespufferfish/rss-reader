package net.elprespufferfish.rssreader.parsing;

import net.elprespufferfish.rssreader.Article;
import net.elprespufferfish.rssreader.Feed;
import net.elprespufferfish.rssreader.parsing.Articles;
import net.elprespufferfish.rssreader.parsing.BaseParser;
import net.elprespufferfish.rssreader.parsing.Parser;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Parses RSS 2.0 feeds.
 *
 * @see <a href="http://cyber.law.harvard.edu/rss/rss.html">http://cyber.law.harvard.edu/rss/rss.html</a>
 */
public class RssParser extends BaseParser implements Parser {

    private static final DateTimeFormatter[] RFC822_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z"),
            DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z")
    };

    public RssParser() {
        super("item");
    }

    @Override
    public Feed parseFeed(String feedAddress, XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
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
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    if ("channel".equals(xmlPullParser.getName())) {
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
                    // no-op
                    break;
                }
                case XmlPullParser.TEXT: {
                    text = xmlPullParser.getText();
                    break;
                }
                case XmlPullParser.END_TAG: {
                    String namespace = xmlPullParser.getNamespace();

                    String nodeName = xmlPullParser.getName();
                    if ("".equals(namespace) && "title".equals(nodeName)) {
                        builder.setTitle(text);
                    } else if ("".equals(namespace) && "link".equals(nodeName)) {
                        builder.setLink(text);
                        String imageUrl = Articles.getOpenGraphContent(text, "image");
                        builder.setImageUrl(imageUrl);
                    } else if ("".equals(namespace) && "pubDate".equals(nodeName)) {
                        DateTime publicationDate = null;
                        for (DateTimeFormatter dateTimeFormatter : RFC822_FORMATTERS) {
                            try {
                                publicationDate = dateTimeFormatter.parseDateTime(text);
                                break;
                            } catch (IllegalArgumentException e) {
                                // ignore
                            }
                        }
                        if (publicationDate == null) {
                            throw new IllegalArgumentException("Could not parse " + text);
                        }
                        builder.setPublicationDate(publicationDate);
                    } else if ("".equals(namespace) && "description".equals(nodeName)) {
                        builder.setDescription(text);
                    } else if ("".equals(namespace) && "guid".equals(nodeName)) {
                        builder.setGuid(text);
                    } else if ("".equals(namespace) && "item".equals(nodeName)) {
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

        throw new IllegalStateException("Reached end of document without closing </item>");
    }

}
