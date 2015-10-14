package net.elprespufferfish.rssreader;

import android.content.Context;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;

/**
 * An RSS or Atom feed.
 */
public class Feed {

    public static class Builder {

        private String name;
        private String url;

        /**
         * Sets a non-null name for the Feed being built.
         * @return a Builder for chaining.
         */
        public Builder withName(String name) {
            Preconditions.checkNotNull(name);
            this.name = name;
            return this;
        }

        /**
         * Sets a non-null URL for the Feed being built.
         * @return a Builder for chaining.
         */
        public Builder withUrl(String url) {
            Preconditions.checkNotNull(url);
            this.url = url;
            return this;
        }

        /**
         * @return validated Feed object.
         */
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
     * @return title of this feed.
     */
    public String getName() {
        return name;
    }

    /**
     * @return URL of this feed.
     */
    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return name + "@" + url;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Feed)) {
            return false;
        }
        Feed that = (Feed) object;
        return Objects.equal(this.name, that.name)
                && Objects.equal(this.url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, url);
    }

}
