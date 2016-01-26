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
 * Fragment displayed when the user has added no feeds.
 *
 * @author elprespufferfish
 */
public class NoFeedsFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.no_feeds, container, false);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                ((MainActivity) getActivity()).addFeed();
            }
        };
        String message = getString(R.string.no_feeds_added);
        SpannableString spannableString = new SpannableString(message);
        String prompt = getString(R.string.add_feed_prompt);
        spannableString.setSpan(clickableSpan,
                message.indexOf(prompt),
                message.indexOf(prompt) + prompt.length(),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        TextView noNewArticles = findById(view, R.id.message);
        noNewArticles.setText(spannableString);
        noNewArticles.setMovementMethod(LinkMovementMethod.getInstance());

        return view;
    }

}
