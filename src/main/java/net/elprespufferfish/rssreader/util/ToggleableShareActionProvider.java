package net.elprespufferfish.rssreader.util;

import android.content.Context;
import android.support.v7.widget.ShareActionProvider;

/**
 * ShareActionProvider exposing methods to manage it's visibility.
 *
 * @author elprespufferfish
 */
public class ToggleableShareActionProvider extends ShareActionProvider {

    private boolean isVisible = true;

    public ToggleableShareActionProvider(Context context) {
        super(context);
    }

    public void hide() {
        isVisible = false;
        refreshVisibility();
    }

    public void show() {
        isVisible = true;
        refreshVisibility();
    }

    @Override
    public boolean overridesItemVisibility() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return isVisible;
    }

}
