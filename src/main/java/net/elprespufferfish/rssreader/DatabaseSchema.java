package net.elprespufferfish.rssreader;

import android.provider.BaseColumns;

public class DatabaseSchema {

    public interface FeedTable extends BaseColumns {
        String TABLE_NAME = "feeds";
        String FEED_NAME = "feed_name";
        String FEED_URL = "feed_url";
    }

    public interface ArticleTable extends BaseColumns {
        String TABLE_NAME = "articles";
        String ARTICLE_FEED = "article_feed";
        String ARTICLE_NAME = "article_name";
        String ARTICLE_URL = "article_url";
        String ARTICLE_PUBLICATION_DATE = "article_pubdate";
        String ARTICLE_DESCRIPTION = "article_description";
        String ARTICLE_IMAGE_URL = "article_image_url";
        String ARTICLE_GUID = "article_guid";
        String ARTICLE_IS_READ = "article_is_read";
    }

    /**
     * Enumeration of states for the {@link ArticleTable#ARTICLE_IS_READ} column.
     */
    public enum ReadStatus {
        /** Article has not been read. */
        UNREAD(0),
        /** Article has been read, but still in current pager. */
        GREY(1),
        /** Article has been read and should not be visible in the pager if hiding read articles. */
        READ(2);

        private final int value;

        ReadStatus(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

    }

    private DatabaseSchema() {
        // prevent instantiations
    }
}
