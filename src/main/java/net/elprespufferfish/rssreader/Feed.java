package net.elprespufferfish.rssreader;

import android.content.Context;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * An RSS feed
 */
public class Feed {

    public static class Builder {

        private String name;
        private String url;

        public Builder withName(String name) {
            Preconditions.checkNotNull(name);
            this.name = name;
            return this;
        }

        public Builder withUrl(String url) {
            Preconditions.checkNotNull(url);
            this.url = url;
            return this;
        }

        public Feed build() {
            Preconditions.checkNotNull(name);
            Preconditions.checkNotNull(url);
            return new Feed(name, url);
        }
    }

    public static Feed nullFeed(Context context) {
        return new Feed(context.getString(R.string.all_feeds), null);
    }

    private final String name;
    private final String url;

    private Feed(String name, String url) {
        this.name = name;
        this.url = url;
    }

    /**
     * @return the title of this feed
     */
    public String getName() {
        return name;
    }

    /**
     * @return the URL of this feed
     */
    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return name + "@" + url;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Feed)) return false;
        Feed that = (Feed) o;
        return Objects.equal(this.name, that.name) &&
                Objects.equal(this.url, that.url);
    }

}
