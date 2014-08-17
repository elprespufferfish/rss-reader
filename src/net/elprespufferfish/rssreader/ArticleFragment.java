package net.elprespufferfish.rssreader;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.TextView;

public class ArticleFragment extends Fragment {

    public static String TITLE_KEY = "title";
    public static String LINK_KEY = "link";
    public static String DESCRIPTION_KEY = "description";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.article_fragment, container, false);
        Bundle arguments = getArguments();

        ((TextView) view.findViewById(R.id.title)).setText(arguments.getString(TITLE_KEY));

        ((TextView) view.findViewById(R.id.link)).setText(arguments.getString(LINK_KEY));

        WebView webView = (WebView) view.findViewById(R.id.description);
        webView.setWebChromeClient(new WebChromeClient()); // HTML5 video requires a WebChromeClient
        enableJavaScript(webView);
        webView.loadData(arguments.getString(DESCRIPTION_KEY), "text/html", null);

        return view;
    }

    /* JavaScript is required to play youtube videos */
    @SuppressLint("SetJavaScriptEnabled")
    private void enableJavaScript(WebView webView) {
        webView.getSettings().setJavaScriptEnabled(true);
    }

}
