package net.elprespufferfish.rssreader;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * 'Last' article to page through.
 * Displays a nice message saying there are no more articles
 */
public class EndOfLineFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.end_of_line_fragment, container, false);
    }

}
