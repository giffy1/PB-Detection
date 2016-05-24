package edu.umass.cs.camera;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;

public class SettingsActivity extends PreferenceActivity {

    private static final int SELECT_DIRECTORY_REQUEST_CODE = 1;
    private Preference prefDirectory;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String path = preferences.getString(getString(R.string.pref_directory_key),
                Constants.PREFERENCES.SAVE_DIRECTORY.DEFAULT);

        prefDirectory = findPreference(getString(R.string.pref_directory_key));
        prefDirectory.setSummary(path);
        prefDirectory.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {

                final Intent chooserIntent = new Intent(SettingsActivity.this, DirectoryChooserActivity.class);

                final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                        .newDirectoryName("DirChooserSample")
                        .allowReadOnlyDirectory(true)
                        .allowNewDirectoryNameModification(true)
                        .build();

                chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);

                startActivityForResult(chooserIntent, SELECT_DIRECTORY_REQUEST_CODE);
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_DIRECTORY_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);

            if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                String dir = data.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR);
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(getString(R.string.pref_directory_key), dir);
                editor.apply();
                prefDirectory.setSummary(dir);
            }
        }
    }
}