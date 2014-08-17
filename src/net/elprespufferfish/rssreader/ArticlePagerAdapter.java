package net.elprespufferfish.rssreader;

import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class ArticlePagerAdapter extends FragmentStatePagerAdapter {

    private final List<Article> articles;

    public ArticlePagerAdapter(FragmentManager fm, List<Article> articles) {
        super(fm);
        this.articles = articles;
    }

    @Override
    public Fragment getItem(int i) {
        Article article = articles.get(i);

        Fragment fragment = new ArticleFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ArticleFragment.TITLE_KEY, article.getTitle());
        bundle.putString(ArticleFragment.LINK_KEY, article.getLink());
        bundle.putString(ArticleFragment.DESCRIPTION_KEY, article.getDescription());
        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public int getCount() {
        return articles.size();
    }

}
