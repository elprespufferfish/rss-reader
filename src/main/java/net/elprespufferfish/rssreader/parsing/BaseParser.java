package net.elprespufferfish.rssreader.parsing;

import net.elprespufferfish.rssreader.Article;

import org.joda.time.DateTime;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Base class to implement common Article parsing logic
 */
public abstract class BaseParser implements Parser {

    private final String articleKey;

    protected BaseParser(String articleKey) {
        this.articleKey = articleKey;
    }

    @Override
    public List<Article> parseArticles(String feedAddress, XmlPullParser xmlPullParser, int maxAgeDays, String latestGuid) throws IOException, XmlPullParserException {
        List<Article> articles = new LinkedList<>();

        DateTime maxArticleAge = DateTime.now().minusDays(maxAgeDays);

        int eventType = xmlPullParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    if (articleKey.equals(xmlPullParser.getName())) {
                        Article article = parseArticle(feedAddress, xmlPullParser);

                        if (latestGuid != null && latestGuid.equals(article.getGuid())) {
                            // already read this part of the feed
                            return articles;
                        }

                        if (article.getPublicationDate().isBefore(maxArticleAge)) {
                            // too far back in feed
                            return articles;
                        }

                        articles.add(article);
                    }
                    break;
                }
                default: {
                    // no-op
                }
            }
            eventType = xmlPullParser.next();
        }
        return articles;
    }


    /**
     * @return parsed {@link Article} from XML stream.
     * Should be called immediately after reading an open article tag
     * Will not advance parser beyond closing article tag
     */
    abstract protected Article parseArticle(String feed, XmlPullParser xmlPullParser) throws XmlPullParserException, IOException;

}
