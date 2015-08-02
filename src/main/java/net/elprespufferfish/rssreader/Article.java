package net.elprespufferfish.rssreader;

import android.os.Parcel;
import android.os.Parcelable;

import org.joda.time.DateTime;

/**
 * Represents a single item to be read
 *
 * @author elprespufferfish
 */
public class Article implements Parcelable {

    public static class Builder {

        private String feed;
        private String title;
        private String link;
        private DateTime publicationDate;
        private String description;
        private String imageUrl;
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

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public void setGuid(String guid) {
            this.guid = guid;
        }

        public Article build() {
            return new Article(feed, title, link, publicationDate, description, imageUrl, guid);
        }
    }

    public static final Parcelable.Creator<Article> CREATOR = new Parcelable.Creator<Article>() {
        public Article createFromParcel(Parcel in) {
            return new Article(in.readString(),
                    in.readString(),
                    in.readString(),
                    new DateTime(in.readLong()),
                    in.readString(),
                    in.readString(),
                    in.readString());
        }

        public Article[] newArray(int size) {
            return new Article[size];
        }
    };

    private final String feed;
    private final String title;
    private final String link;
    private final DateTime publicationDate;
    private final String description;
    private final String imageUrl;
    private final String guid;

    private Article(
            String feed,
            String title,
            String link,
            DateTime publicationDate,
            String description,
            String imageUrl,
            String guid) {
        this.feed = feed;
        this.title = title;
        this.link = link;
        this.publicationDate = publicationDate;
        this.description = description;
        this.imageUrl = imageUrl;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public String getGuid() {
        return guid;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(feed);
        dest.writeString(title);
        dest.writeString(link);
        dest.writeLong(publicationDate.getMillis());
        dest.writeString(description);
        dest.writeString(imageUrl);
        dest.writeString(guid);
    }

}
