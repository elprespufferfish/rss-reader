package net.elprespufferfish.rssreader;

import android.provider.BaseColumns;

public class DatabaseSchema {

    public static interface FeedTable extends BaseColumns {
        public static final String TABLE_NAME = "feeds";
        public static final String FEED_NAME = "feed_name";
        public static final String FEED_URL = "feed_url";
    }

    private DatabaseSchema() {
        // prevent instantiations
    }
}
