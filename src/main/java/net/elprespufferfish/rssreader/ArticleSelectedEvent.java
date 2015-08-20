package net.elprespufferfish.rssreader;

/**
 * Notify that an article is being viewed
 */
public class ArticleSelectedEvent {

    private final int index;

    public ArticleSelectedEvent(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

}
