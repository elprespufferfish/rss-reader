package net.elprespufferfish.rssreader;

import android.provider.BaseColumns;

public class DatabaseSchema {

    public static interface FeedTable extends BaseColumns {
        public static final String TABLE_NAME = "feeds";
        public static final String FEED_NAME = "feed_name";
        public static final String FEED_URL = "feed_url";
    }

    public static interface ArticleTable extends BaseColumns {
        public static final String TABLE_NAME = "articles";
        public static final String ARTICLE_FEED = "article_feed";
        public static final String ARTICLE_NAME = "article_name";
        public static final String ARTICLE_URL = "article_url";
        public static final String ARTICLE_PUBLICATION_DATE = "article_pubdate";
        public static final String ARTICLE_DESCRIPTION = "article_description";
        public static final String ARTICLE_GUID = "article_guid";
        public static final String ARTICLE_IS_READ = "article_is_read";
    }

    private DatabaseSchema() {
        // prevent instantiations
    }
}
