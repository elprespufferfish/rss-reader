package net.elprespufferfish.rssreader.parsing;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Articles {

    private static final Logger LOGGER = LoggerFactory.getLogger(Articles.class);

    /**
     * @return opengraph content at provided content if available, or <code>null</code>.
     */
    public static String getOpenGraphContent(String articleAddress, String type) {
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
