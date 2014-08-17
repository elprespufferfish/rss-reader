package net.elprespufferfish.rssreader;

import java.io.IOException;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

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
                } else if ("".equals(namespace) && "pubDate".equals(nodeName)) {
                    builder.setPublicationDate(RFC822_FORMATTER.parseDateTime(text));
                } else if ("".equals(namespace) && "description".equals(nodeName)) {
                    builder.setDescription(text);
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
