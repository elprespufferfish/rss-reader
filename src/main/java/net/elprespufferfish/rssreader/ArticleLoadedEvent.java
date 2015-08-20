package net.elprespufferfish.rssreader;

/**
 * Notify UI that a new article is being displayed
 */
public class ArticleLoadedEvent {

    private final int articleIndex;
    private final Article article;

    public ArticleLoadedEvent(int articleIndex, Article article) {
        this.articleIndex = articleIndex;
        this.article = article;
    }

    public int getArticleIndex() {
        return this.articleIndex;
    }

    public Article getArticle() {
        return this.article;
    }

}
