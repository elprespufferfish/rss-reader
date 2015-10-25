package net.elprespufferfish.rssreader.settings;

import android.app.ProgressDialog;
import android.app.backup.BackupManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import net.elprespufferfish.rssreader.R;
import net.elprespufferfish.rssreader.backup.RestoreProgresObserver;

/**
 * Settings screen.
 *
 * @author elprespufferfish
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setTitle(R.string.settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);

            findPreference("backup").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new BackupManager(getActivity()).dataChanged();
                    Toast.makeText(getActivity(), R.string.backup_message, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            findPreference("restore").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.restore_prompt_title)
                            .setMessage(R.string.restore_prompt_message)
                            .setNegativeButton(R.string.restore_prompt_cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // no-op
                                }
                            })
                            .setPositiveButton(R.string.restore_prompt_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    final ProgressDialog restoreDialog = new ProgressDialog(getActivity());
                                    restoreDialog.setTitle(getString(R.string.restore_progress_title));
                                    restoreDialog.setMessage(getString(R.string.restore_progress_message));
                                    restoreDialog.show();

                                    int requestCode = new BackupManager(getActivity()).requestRestore(new RestoreProgresObserver(restoreDialog));
                                    if (requestCode != 0) {
                                        restoreDialog.dismiss();

                                        new AlertDialog.Builder(SettingsFragment.this.getActivity())
                                                .setTitle(R.string.restore_failure_title)
                                                .setMessage(R.string.restore_failure_message)
                                                .setPositiveButton(R.string.restore_failure_ok, new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        // no-op
                                                    }
                                                })
                                                .create()
                                                .show();
                                    } else {
                                        Toast.makeText(getActivity(), R.string.restore_complete, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .create()
                            .show();

                    return true;
                }
            });
        }

    }

}
