package net.elprespufferfish.rssreader;

import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;

/**
 * Represents a single item to be read
 *
 * @author elprespufferfish
 */
public class Article implements Comparable<Article> {

    private static final DateTimeFormatter RFC822_FORMATTER = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z");

    /**
     * @return parsed {@link Article} from XML stream.
     * Should be called immediately after reading an open <item> tag
     * Will not advance parser beyond closing </item> tag
     */
    public static Article fromXml(XmlPullParser xmlPullParser) throws XmlPullParserException, IOException {
        Builder builder = new Builder();

        boolean hasOpenGraphContent = false;
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
                    String openGraphContent = getOpenGraphContent(text);
                    if (openGraphContent != null) {
                        hasOpenGraphContent = true;
                        builder.setDescription(openGraphContent);
                    }
                } else if ("".equals(namespace) && "pubDate".equals(nodeName)) {
                    builder.setPublicationDate(RFC822_FORMATTER.parseDateTime(text));
                } else if ("".equals(namespace) && "description".equals(nodeName)) {
                    if (!hasOpenGraphContent) {
                        // fall back to plain description if nothing better is available
                        builder.setDescription(text);
                    }
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
     * @return opengraph content at provided content if available
     */
    private static String getOpenGraphContent(String articleAddress) {
        try {
            Document document = Jsoup.connect(articleAddress).get();
            String imageAddress = getOpenGraphContent(document, "image");
            return "<html><body><img src=\"" + imageAddress + "\" width=\"100%\"/></body></html>";
        } catch (Exception e) {
            Log.e("rss-reader", "Could not crawl for opengraph content", e);
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

    private static class Builder {

        private String title;
        private String link;
        private DateTime publicationDate;
        private String description;

        public void setTitle(String title) {
            this.title = title;
        }

        public void setLink(String link) {
            this.link = link;
        }

        public void setPublicationDate(DateTime publicationDate) {
            this.publicationDate = publicationDate;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Article build() {
            return new Article(title, link, publicationDate, description);
        }
    }

    private final String title;
    private final String link;
    private final DateTime publicationDate;
    private final String description;

    private Article(
            String title,
            String link,
            DateTime publicationDate,
            String description) {
        this.title = title;
        this.link = link;
        this.publicationDate = publicationDate;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getLink() {
        return link;
    }

    public DateTime getPublicationDate() {
        return publicationDate;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Sort newest to oldest
     */
    @Override
    public int compareTo(Article another) {
        return another.getPublicationDate().compareTo(this.getPublicationDate());
    }

}
