package org.lattilad.bestboard.debug;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.lattilad.bestboard.R;
import org.lattilad.bestboard.scribe.Scribe;

/**
 * Collection of message-limit constants for Scribe and
 * Scribe initialisation.
 */
public class Debug
	{
	// Constants for PRIMARY configuration
    public static final String LOG_TAG = "SCRIBE_BEST";

	// Constants for SECONDARY configuration
    public static final String coatLogFileName = "coat.log";
    public static final String LOG_TAG_COAT = "SCRIBE_COAT";

    public static final String tokenLogFileName = "token.log";
    public static final String LOG_TAG_TOKEN = "TOKEN";

    // Settings of debug levels - only EVEN numbers!
    private static final int LIMIT = 500;

    public static final int PREF = 990;
    public static final int LAYOUT = 20;
    public static final int TOUCH = 24;
    public static final int TOUCH_VERBOSE = 24;
    public static final int DRAW =26;
    public static final int DRAW_VERBOSE = 26;
    public static final int VIEW = 30;
    public static final int COMMANDS = 40;
    public static final int IGNITION = 50;
    public static final int DATA = 60;
    public static final int PARSER = 70;
    public static final int BLOCK = 70;
    public static final int SERVICE = 80;
    public static final int TEXT = 90;
    public static final int CURSOR = 90;
    public static final int TIMER = 90;
    public static final int WEBVIEW = 90;
    public static final int SELECTOR = 90;
    public static final int CODETEXT = 00;

    public static final int LAYOUTSTATE = 60;
    public static final int CAPSSTATE = 60;
    public static final int HARDSTATE = 60;
    public static final int METASTATE = 60;
    public static final int BOARDTABLE = 60;

    public static final int BUTTON = 60;
    public static final int PERMISSION = 960;

    // Other settings


	/**
	 * Scribe primary and secondary config initialisation.
	 * !! If there are more entry points, during initial settings, 
	 * messages could be delivered to default log-file. This should be fixed later !! 
	 * @param context context containing package information
	 */
	public static void initScribe( Context context )
		{
        // Scribe initialization: PRIMARY - debug
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences( context );

        String directoryName =
                sharedPrefs.getString( context.getString( R.string.descriptor_directory_key ),
                        context.getString( R.string.descriptor_directory_default ) );

        boolean enabled =
                sharedPrefs.getBoolean( context.getString( R.string.debug_key ),
                        context.getResources().getBoolean( R.bool.debug_default ));

        Scribe.setConfig()
                .enable( enabled )
                .setDirectoryName( directoryName )      // Primary directory name
                .enableFileLog()                        // Enable file log under the default name
                .enableSysLog( LOG_TAG )                // Primary log-tag : BEST
                .setLimit( LIMIT )
                .init( context );                       // Primary file name : package name

		// !! Service will be started only once, so this should go into a more frequent position
        // InputMethodService.onWindowHidden() or .onFinishInput() could be a good place.
		Scribe.checkLogFileLength(); // Primary log will log several runs
		Scribe.logUncaughtExceptions(); // Primary log will store uncaught exceptions


        // Scribe initialization: SECONDARY - log for user
        Scribe.setConfig()
                .setDirectoryName( directoryName )      // Secondary directory name should be given
                .enableFileLog( coatLogFileName )       // Secondary file name : "coat.log"
                .enableSysLog( LOG_TAG_COAT )           // Secondary log-tag : "COAT"
                .initSecondary();
        }
	
	}
