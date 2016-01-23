package net.elprespufferfish.rssreader;

import static butterknife.ButterKnife.findById;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * 'Last' article to page through.
 * Displays a nice message saying there are no more articles
 */
public class EndOfLineFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.end_of_line_fragment, container, false);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                ((MainActivity) getActivity()).refresh();
            }
        };
        String refreshMessage = getString(R.string.end_of_the_line);
        SpannableString spannableString = new SpannableString(refreshMessage);
        String refreshPrompt = getString(R.string.refresh_prompt);
        spannableString.setSpan(clickableSpan,
                refreshMessage.indexOf(refreshPrompt),
                refreshMessage.indexOf(refreshPrompt) + refreshPrompt.length(),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        TextView noNewArticles = findById(view, R.id.no_articles);
        noNewArticles.setText(spannableString);
        noNewArticles.setMovementMethod(LinkMovementMethod.getInstance());

        return view;
    }

}
