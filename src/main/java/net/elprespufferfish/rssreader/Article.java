package net.elprespufferfish.rssreader;

import org.joda.time.DateTime;

/**
 * Represents a single item to be read
 *
 * @author elprespufferfish
 */
public class Article {

    public static class Builder {

        private String feed;
        private String title;
        private String link;
        private DateTime publicationDate;
        private String description;
        private String guid;

        public void setFeed(String feed) {
            this.feed = feed;
        }

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

        public void setGuid(String guid) {
            this.guid = guid;
        }

        public Article build() {
            return new Article(feed, title, link, publicationDate, description, guid);
        }
    }

    private final String feed;
    private final String title;
    private final String link;
    private final DateTime publicationDate;
    private final String description;
    private final String guid;

    private Article(
            String feed,
            String title,
            String link,
            DateTime publicationDate,
            String description,
            String guid) {
        this.feed = feed;
        this.title = title;
        this.link = link;
        this.publicationDate = publicationDate;
        this.description = description;
        this.guid = guid;
    }

    public String getFeed() {
        return feed;
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

    public String getGuid() {
        return guid;
    }

}
