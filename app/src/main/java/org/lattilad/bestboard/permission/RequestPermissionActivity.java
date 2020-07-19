package org.lattilad.bestboard.permission;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.lattilad.bestboard.R;
import org.lattilad.bestboard.debug.Debug;
import org.lattilad.bestboard.prefs.PrefsFragment;
import org.lattilad.bestboard.scribe.Scribe;

import java.util.List;

/**
 * User needs to give 2 types of "permissions" for BestBoard:
 * - grant WRITE_EXTERNAL_STROAGE system permission (storage)
 * - enable INPUT METHOD (input)
 *
 * STORAGE:
 * BestBoard gives information about STORAGE at the very beginning.
 * This permission can be granted via system dialogs.
 * If these dialogs are disabled, then permission can be set only
 * at the system settings of the application.
 *
 * onRequestPermissionsResult will not work, if permission is granted inside settings,
 * so permission changes are checked in the onResume method.
 *
 * INPUT:
 * Input method should be enabled among system settings (PermissionInputSettingsButton),
 * and can be checked by InputMethodManager.
 * BestBoard cannot be selected without enabling it.
 *
 * Both permissions are checked within the onResume method,
 * because both permissions can be set outside this activity.
 */
public class RequestPermissionActivity extends Activity
    {
    // Preference needed by checkStorage()
    private String PERMISSION_ALREADY_REQUESTED = "permissionrequested";

    private Button PermissionStorageButton;
    private Button PermissionStorageSettingsButton;
    private TextView PermissionStorageOk;

    private Button PermissionInputSettingsButton;
    private TextView PermissionInputOk;


    @Override
    protected void onCreate(Bundle savedInstanceState)
        {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.request_permission_dialog);
        //getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        this.setFinishOnTouchOutside(false);

        /*
         * GRANTING STORAGE PERMISSION
         */

        // Asks permission for storage (external storage write access)
        // Stores that permission was already asked
        PermissionStorageButton = (Button) (findViewById(R.id.permission_storage_button));
        PermissionStorageButton.setOnClickListener(new View.OnClickListener()
            {
            @Override
            public void onClick(View view)
                {
                // Permission request code is not handled, permission is checked in the onResume method
                ActivityCompat.requestPermissions( RequestPermissionActivity.this,
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1234);

                // Set PERMISSION_ALREADY_REQUESTED flag
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences( getApplicationContext() );
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putBoolean(PERMISSION_ALREADY_REQUESTED, true );
                editor.apply();
                }
            });

        PermissionStorageSettingsButton = (Button) (findViewById(R.id.permission_storage_settings_button));
        PermissionStorageSettingsButton.setOnClickListener(new View.OnClickListener()
            {
            @Override
            public void onClick(View view)
                {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(intent);
                }
            });

        PermissionStorageOk = (TextView) (findViewById(R.id.permission_storage_ok));


        /*
         * ENABLING INPUT METHOD
         */

        PermissionInputSettingsButton = (Button) (findViewById(R.id.permission_input_settings_button));
        PermissionInputSettingsButton.setOnClickListener(new View.OnClickListener()
            {
            @Override
            public void onClick(View view)
                {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                // per doc activity may not exist
                // intent.resolveActivity(packageManager) can be helpful
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(intent);
                }
            });

        PermissionInputOk = (TextView) (findViewById(R.id.permission_input_ok));

        }


    @Override
    protected void onResume()
        {
        super.onResume();

        boolean storageEnabled = checkStorage();
        boolean inputEnabled = checkInput();

        // if both permissions are ready, we could finish
        if ( storageEnabled && inputEnabled )
            {
            Toast.makeText(this, getString(R.string.permission_ready), Toast.LENGTH_SHORT).show();
            // Call the next activity from here !!!!
            finish(); // - https://stackoverflow.com/questions/18957125/how-to-finish-activity-when-starting-other-activity-in-android
            }
        }


    // only false to true changes should RELOAD bestboard
    private boolean previousCheckStorage = true;

    public static boolean isStorageEnabled(Context context )
        {
        return ContextCompat.checkSelfPermission( context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE ) ==
                PackageManager.PERMISSION_GRANTED;
        }

    /**
     * Check whether Storage Permisson is granted, and sets views according to the result
     * @return true if enabled, false if not
     */
    boolean checkStorage()
        {
        if ( isStorageEnabled( this ) )
            {
            Scribe.debug(Debug.PERMISSION, "Storage permission is granted" );

            // if true, clear PERMISSION_ALREADY_REQUESTED flag
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putBoolean(PERMISSION_ALREADY_REQUESTED, false);
            editor.apply();

            // set views
            PermissionStorageOk.setVisibility(View.VISIBLE);
            PermissionStorageButton.setVisibility(View.GONE);
            PermissionStorageSettingsButton.setVisibility(View.GONE);

            if ( !previousCheckStorage ) // false -> true change
                {
                Scribe.debug(Debug.PERMISSION, "Storage permission is changed to ENABLED, BestBoard should reload. (if already started)" );
                PrefsFragment.performAction(this, PrefsFragment.PREFS_ACTION_RELOAD);
                }
            previousCheckStorage = true;
            }
        else
            {
            Scribe.debug(Debug.PERMISSION, "Storage permission is NOT granted" );
            PermissionStorageOk.setVisibility(View.GONE);

            // should NOT show rationale:
            //      - First run
            //      - User do not want to grant permission -> ONLY SETTINGS WORK
            // should show rationale:
            //      - Consequent run, user is not decided yet
            boolean shouldShowRequestPermissionRationale =
                    ActivityCompat.shouldShowRequestPermissionRationale(this,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

            // read PERMISSION_ALREADY_REQUESTED flag
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences( getApplicationContext() );
            boolean permissionRequested = sharedPrefs.getBoolean(PERMISSION_ALREADY_REQUESTED, false );

            if ( !permissionRequested ||                    // not yet requested, first run
                    shouldShowRequestPermissionRationale )  // requested, but not decided (info already shown)
                {
                PermissionStorageButton.setVisibility(View.VISIBLE);
                PermissionStorageSettingsButton.setVisibility(View.GONE);
                }
            else                                            // requested, but not granted; AND no info wanted
                {
                PermissionStorageButton.setVisibility(View.GONE);
                PermissionStorageSettingsButton.setVisibility(View.VISIBLE);
                }

            previousCheckStorage = false;
            }
        // this is the current one
        return previousCheckStorage;
        }


    public static boolean isInputEnabled(Context context )
        {
        InputMethodManager inputMethodManager= (InputMethodManager)(context.getSystemService( INPUT_METHOD_SERVICE ));
        List<InputMethodInfo> list = inputMethodManager.getEnabledInputMethodList();
        String packageName = context.getPackageName();

        Scribe.debug(Debug.PERMISSION, "Package name of Best Board: " + packageName );
        Scribe.debug(Debug.PERMISSION, "Package name of enabbled keyboards: " );

        for (InputMethodInfo info : list )
            {
            Scribe.debug(Debug.PERMISSION, " * " + info.getPackageName() );
            if ( info.getPackageName().equals( packageName ))
                {
                Scribe.debug(Debug.PERMISSION, "BestBoard is enabled" );
                return true;
                }
            }
        Scribe.debug(Debug.PERMISSION, "BestBoard is NOT enabled" );
        return false;
        }


    /**
     * Check whether Input Method is enabled, and sets views according to the result
     * @return true if enabled, false if not
     */
    boolean checkInput()
        {
        Scribe.locus(Debug.PERMISSION);

        if (isInputEnabled(this))
            {
            PermissionInputSettingsButton.setVisibility(View.GONE);
            PermissionInputOk.setVisibility(View.VISIBLE);
            return true;
            }
        else
            {
            PermissionInputSettingsButton.setVisibility(View.VISIBLE);
            PermissionInputOk.setVisibility(View.GONE);
            return false;
            }
        }
    }
