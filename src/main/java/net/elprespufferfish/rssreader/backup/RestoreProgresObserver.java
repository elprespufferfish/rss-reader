package net.elprespufferfish.rssreader.backup;

import android.app.ProgressDialog;
import android.app.backup.RestoreObserver;

/**
 * Tie backup progress to a ProgressDialog.
 *
 * @author elprespufferfish
 */
public class RestoreProgresObserver extends RestoreObserver {

    private final ProgressDialog progresDialog;

    private int numPackages;

    public RestoreProgresObserver(ProgressDialog progressDialog) {
        this.progresDialog = progressDialog;
    }

    @Override
    public void restoreStarting(int numPackages) {
        super.restoreStarting(numPackages);
        this.numPackages = numPackages;
    }

    @Override
    public void onUpdate(int nowBeingRestored, String currentPackage) {
        super.onUpdate(nowBeingRestored, currentPackage);
        progresDialog.setProgress(nowBeingRestored / numPackages);
    }

    @Override
    public void restoreFinished(int error) {
        super.restoreFinished(error);
        // TODO - handle error
        progresDialog.dismiss();
    }

}
