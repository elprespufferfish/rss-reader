package net.elprespufferfish.rssreader;

import android.support.v4.view.ViewPager;

/**
 * Handles marking articles as read
 */
public class ArticleReadListener implements ViewPager.OnPageChangeListener {

    private final ArticlePagerAdapter articlePagerAdapter;

    public ArticleReadListener(ArticlePagerAdapter articlePagerAdapter) {
        this.articlePagerAdapter = articlePagerAdapter;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // unused
    }

    @Override
    public void onPageSelected(int position) {
        ArticleFragment articleFragment = (ArticleFragment) articlePagerAdapter.getItem(position);
        Article article = articleFragment.getArguments().getParcelable(ArticleFragment.ARTICLE_KEY);
        Feeds.getInstance().markArticleRead(article);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // unused
    }

}
