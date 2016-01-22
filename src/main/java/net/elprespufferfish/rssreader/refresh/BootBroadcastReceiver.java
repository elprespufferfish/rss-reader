package net.elprespufferfish.rssreader.refresh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.elprespufferfish.rssreader.RssReaderApplication;

/**
 * Ensure refreshes are scheduled after a device reboot.
 */
public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            return;
        }

        RssReaderApplication application = (RssReaderApplication) context.getApplicationContext();
        application.scheduleRefresh();
    }

}
