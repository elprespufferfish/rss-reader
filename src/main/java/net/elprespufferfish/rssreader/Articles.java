package net.elprespufferfish.rssreader;

import java.io.IOException;

import net.elprespufferfish.rssreader.Article.Builder;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class Articles {

    private static final Logger LOGGER = LoggerFactory.getLogger(Articles.class);

    private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final DateTimeFormatter[] RFC822_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z"),
            DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss z")
    };

    /**
     * @return parsed {@link Article} from XML stream.
     * Should be called immediately after reading an open <item> tag
     * Will not advance parser beyond closing </item> tag
     */
    public static Article fromRss(String feed, XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        Builder builder = new Builder();
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
                    String imageUrl = getOpenGraphContent(text, "image");
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

    /**
     * @return parsed {@link Article} from an Atom feed.
     * Should be called immediately after reading an open <entry> tag
     * Will not advance parser beyond closing </entry> tag
     */
    public static Article fromAtom(String feed, XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        Builder builder = new Builder();
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
                            String imageUrl = getOpenGraphContent(link, "image");
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
    /**
     * @return opengraph content at provided content if available
     */
    private static String getOpenGraphContent(String articleAddress, String type) {
        try {
            Document document = Jsoup.connect(articleAddress).get();
            return getOpenGraphContent(document, type);
        } catch (Exception e) {
            LOGGER.error("Could not crawl for opengraph content: " + e.getMessage());
        }
        return null;
    }

    private static String getOpenGraphContent(Document document, String type) {
        Elements elements = document.select("meta[property=og:" + type);
        if (elements.size() == 0) {
            return null;
        }
        Element element = elements.first();
        return element.attr("content");
    }

    private Articles() {
        // prevent instantiation
    }
}
