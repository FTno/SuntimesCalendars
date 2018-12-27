/*
    Copyright (C) 2018 Forrest Guice
    This file is part of SuntimesCalendars.

    SuntimesCalendars is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuntimesCalendars is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SuntimesCalendars.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.forrestguice.suntimeswidget.calendar;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;

import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.forrestguice.suntimescalendars.R;
import com.forrestguice.suntimeswidget.calculator.core.CalculatorProviderContract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/**
 * SuntimesCalendarActivity
 */
public class SuntimesCalendarActivity extends AppCompatActivity
{
    public static String TAG = "SuntimesCalendar";

    public static final String DIALOGTAG_ABOUT = "aboutdialog";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final int MIN_PROVIDER_VERSION = 0;
    public static final String MIN_SUNTIMES_VERSION = "0.10.0";
    public static final String MIN_SUNTIMES_VERSION_STRING = "Suntimes v" + MIN_SUNTIMES_VERSION;


    public static final int REQUEST_CALENDAR_ENABLED = 12;           // individual calendar enabled/disabled
    public static final int REQUEST_CALENDAR_DISABLED = 14;

    public static final int REQUEST_CALENDARS_ENABLED = 2;          // all calendars enabled/disabled
    public static final int REQUEST_CALENDARS_DISABLED = 4;

    public static final String EXTRA_ON_PERMISSIONS_GRANTED = "on_permissions_granted_do_this";

    private Context context;
    private String config_apptheme = null;
    private static String systemLocale = null;  // null until locale is overridden w/ loadLocale

    private static String appVersionName = null, providerVersionName = null;
    private static Integer appVersionCode = null, providerVersionCode = null;
    private static boolean needsSuntimesPermissions = false;

    private CalendarPrefsFragment mainFragment = null;

    public SuntimesCalendarActivity()
    {
        super();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void attachBaseContext(Context newBase)
    {
        ContentResolver resolver = newBase.getContentResolver();
        if (resolver != null)
        {
            Uri uri = Uri.parse("content://" + CalculatorProviderContract.AUTHORITY + "/" + CalculatorProviderContract.QUERY_CONFIG );
            String[] projection = new String[] { CalculatorProviderContract.COLUMN_CONFIG_LOCALE, CalculatorProviderContract.COLUMN_CONFIG_APP_THEME,
                                                 CalculatorProviderContract.COLUMN_CONFIG_APP_VERSION, CalculatorProviderContract.COLUMN_CONFIG_APP_VERSION_CODE,
                                                 CalculatorProviderContract.COLUMN_CONFIG_PROVIDER_VERSION, CalculatorProviderContract.COLUMN_CONFIG_PROVIDER_VERSION_CODE };
            try {
                Cursor cursor = resolver.query(uri, projection, null, null, null);
                needsSuntimesPermissions = false;

                if (cursor != null)
                {
                    // a valid cursor - Suntimes is installed (and we have access)
                    cursor.moveToFirst();
                    String locale = (!cursor.isNull(0)) ? cursor.getString(0) : null;
                    config_apptheme = (!cursor.isNull(1)) ? cursor.getString(1) : null;
                    appVersionName = (!cursor.isNull(2)) ? cursor.getString(2) : null;
                    appVersionCode = (!cursor.isNull(3)) ? cursor.getInt(3) : null;
                    providerVersionName = (!cursor.isNull(4)) ? cursor.getString(4) : null;
                    providerVersionCode = (!cursor.isNull(5)) ? cursor.getInt(5) : null;
                    cursor.close();
                    super.attachBaseContext((locale != null) ? loadLocale(newBase, locale) : resetLocale(newBase));

                } else {
                    // cursor is null (but no SecurityException..) - Suntimes isn't installed at all
                    super.attachBaseContext(newBase);
                }

            } catch (SecurityException e) {
                // Security Exception! Suntimes is installed (but we don't have permissions for some reason)
                Log.e(TAG, "attachBaseContext: Unable to access SuntimesCalculatorProvider! " + e);
                appVersionName = MIN_SUNTIMES_VERSION;
                needsSuntimesPermissions = true;
                super.attachBaseContext(newBase);
            }
        } else super.attachBaseContext(newBase);
    }

    private static Context loadLocale( Context context, String languageTag )
    {
        if (systemLocale == null) {
            systemLocale = Locale.getDefault().getLanguage();
        }

        Locale customLocale = localeForLanguageTag(languageTag);
        Locale.setDefault(customLocale);
        Log.i(TAG, "loadLocale: " + languageTag);

        Resources resources = context.getApplicationContext().getResources();
        Configuration config = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= 17)
            config.setLocale(customLocale);
        else config.locale = customLocale;

        if (Build.VERSION.SDK_INT >= 25) {
            return new ContextWrapper(context.createConfigurationContext(config));

        } else {
            DisplayMetrics metrics = resources.getDisplayMetrics();
            //noinspection deprecation
            resources.updateConfiguration(config, metrics);
            return new ContextWrapper(context);
        }
    }

    private static Context resetLocale( Context context )
    {
        if (systemLocale != null) {
            return loadLocale(context, systemLocale);
        }
        return context;
    }

    private static @NonNull Locale localeForLanguageTag(@NonNull String languageTag)
    {
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            locale = Locale.forLanguageTag(languageTag.replaceAll("_", "-"));

        } else {
            String[] parts = languageTag.split("[_]");
            String language = parts[0];
            String country = (parts.length >= 2) ? parts[1] : null;
            locale = (country != null) ? new Locale(language, country) : new Locale(language);
        }
        Log.d(TAG, "localeForLanguageTag: tag: " + languageTag + " :: locale: " + locale.toString());
        return locale;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle icicle)
    {
        setResult(RESULT_OK);
        context = this;

        if (config_apptheme != null) {
            setTheme(config_apptheme.equals(THEME_LIGHT) ? R.style.AppTheme_Light : R.style.AppTheme_Dark);
        }

        super.onCreate(icicle);
        mainFragment = new CalendarPrefsFragment();
        mainFragment.setAboutClickListener(onAboutClick);
        mainFragment.setProviderVersion(providerVersionCode);
        getFragmentManager().beginTransaction().replace(android.R.id.content, mainFragment).commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (grantResults.length > 0 && permissions.length > 0)
        {
            switch (requestCode)
            {
                case REQUEST_CALENDAR_ENABLED:
                case REQUEST_CALENDAR_DISABLED:
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    {
                        if (runCalendarTask(SuntimesCalendarActivity.this, false, false))
                        {
                            if (mainFragment != null)
                            {
                                SharedPreferences.Editor pref = PreferenceManager.getDefaultSharedPreferences(context).edit();
                                ArrayList<SuntimesCalendarTask.SuntimesCalendarTaskItem> items = loadItems(this, true);
                                for (SuntimesCalendarTask.SuntimesCalendarTaskItem item : items)
                                {
                                    boolean enabled = (item.getAction() == SuntimesCalendarTask.SuntimesCalendarTaskItem.ACTION_UPDATE);
                                    pref.putBoolean(SuntimesCalendarSettings.PREF_KEY_CALENDARS_ENABLED, enabled);
                                    pref.apply();

                                    CheckBoxPreference calendarPref = mainFragment.getCalendarPref(item.getCalendar());
                                    if (calendarPref != null) {
                                        calendarPref.setEnabled(enabled);
                                    }
                                }
                            }
                        }
                    }
                    break;

                case REQUEST_CALENDARS_ENABLED:
                case REQUEST_CALENDARS_DISABLED:
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    {
                        boolean enabled = requestCode == (REQUEST_CALENDARS_ENABLED);
                        if (runCalendarTask(SuntimesCalendarActivity.this, !enabled, true))
                        {
                            SharedPreferences.Editor pref = PreferenceManager.getDefaultSharedPreferences(context).edit();
                            pref.putBoolean(SuntimesCalendarSettings.PREF_KEY_CALENDARS_ENABLED, enabled);
                            pref.apply();

                            if (mainFragment != null)
                            {
                                CheckBoxPreference calendarsPref = mainFragment.getCalendarsEnabledPref();
                                if (calendarsPref != null) {
                                    calendarsPref.setChecked(enabled);
                                }
                            }
                        }
                    }
                    break;
            }
        }
    }

    /**
     * onSaveInstanceState
     * @param bundle outState
     */
    @Override
    protected void onSaveInstanceState( Bundle bundle )
    {
        super.onSaveInstanceState(bundle);
        bundle.putBoolean("needsSuntimesPermissions", needsSuntimesPermissions);
    }

    /**
     * onRestoreInstanceState
     * @param bundle inState
     */
    @Override
    protected void onRestoreInstanceState( Bundle bundle )
    {
        super.onRestoreInstanceState(bundle);
        needsSuntimesPermissions = bundle.getBoolean("needsSuntimesPermissions");
    }

    /**
     * CalendarPrefsFragment
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class CalendarPrefsFragment extends PreferenceFragment
    {
        private CheckBoxPreference calendarsEnabledPref = null;
        public CheckBoxPreference getCalendarsEnabledPref()
        {
            return calendarsEnabledPref;
        }

        private HashMap<String, CheckBoxPreference> calendarPrefs = new HashMap<>();
        public CheckBoxPreference getCalendarPref(String calendar)
        {
            return calendarPrefs.get(calendar);
        }

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            Log.i(TAG, "CalendarPrefsFragment: Arguments: " + getArguments());
            PreferenceManager.setDefaultValues(getActivity(), R.xml.preference_calendars, false);
            addPreferencesFromResource(R.xml.preference_calendars);

            if (savedInstanceState != null && savedInstanceState.containsKey("providerVersion")) {
                providerVersion = savedInstanceState.getInt("providerVersion");
            }

            Preference aboutPref = findPreference("app_about");
            if (aboutPref != null && onAboutClick != null) {
                aboutPref.setOnPreferenceClickListener(onAboutClick);
            }

            final Activity activity = getActivity();
            calendarsEnabledPref = (CheckBoxPreference) findPreference(SuntimesCalendarSettings.PREF_KEY_CALENDARS_ENABLED);
            calendarsEnabledPref.setOnPreferenceChangeListener(onPreferenceChanged0(activity));

            for (String calendar : SuntimesCalendarAdapter.ALL_CALENDARS)
            {
                CheckBoxPreference calendarPref = (CheckBoxPreference)findPreference(SuntimesCalendarSettings.PREF_KEY_CALENDARS_CALENDAR + calendar);
                calendarPrefs.put(calendar, calendarPref);
                calendarPref.setOnPreferenceChangeListener(onPreferenceChanged1(activity, calendar));
            }

            if (needsSuntimesPermissions || !checkDependencies())
            {
                if (!calendarsEnabledPref.isChecked())
                {
                    calendarsEnabledPref.setEnabled(false);
                    Preference windowStart = findPreference(SuntimesCalendarSettings.PREF_KEY_CALENDAR_WINDOW0);
                    windowStart.setEnabled(false);
                    Preference windowEnd = findPreference(SuntimesCalendarSettings.PREF_KEY_CALENDAR_WINDOW1);
                    windowEnd.setEnabled(false);
                }

                if (needsSuntimesPermissions)
                    showPermissionDeniedMessage(getActivity(), getActivity().getWindow().getDecorView().findViewById(android.R.id.content));
                else showMissingDepsMessage(getActivity(), getActivity().getWindow().getDecorView().findViewById(android.R.id.content));

            } /**else {
                boolean enabled = calendarsEnabledPref.isChecked();
                for (CheckBoxPreference pref : calendarPrefs.values()) {
                    pref.setEnabled(enabled);
                }
            }*/
        }

        private Preference.OnPreferenceChangeListener onPreferenceChanged0(final Activity activity)
        {
            return new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    boolean enabled = (Boolean)newValue;
                    int calendarPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CALENDAR);
                    if (calendarPermission != PackageManager.PERMISSION_GRANTED)
                    {
                        final int requestCode = (enabled ? REQUEST_CALENDARS_ENABLED : REQUEST_CALENDARS_DISABLED);
                        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_CALENDAR))
                        {
                            if (enabled) {
                                savePendingItems(activity);
                            }
                            showPermissionRational(activity, requestCode);
                            return false;

                        } else {
                            if (enabled) {
                                savePendingItems(activity);
                            }
                            ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.WRITE_CALENDAR }, requestCode);
                            return false;
                        }

                    } else {
                        savePendingItems(activity);
                        return runCalendarTask(activity, !enabled, true);
                    }
                }
            };
        }

        private Preference.OnPreferenceChangeListener onPreferenceChanged1(final Activity activity, final String calendar)
        {
            return new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    boolean calendarsEnabled = SuntimesCalendarSettings.loadCalendarsEnabledPref(activity);
                    if (calendarsEnabled)
                    {
                        boolean enabled = (Boolean)newValue;
                        int calendarPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CALENDAR);
                        if (calendarPermission != PackageManager.PERMISSION_GRANTED)
                        {
                            final int requestCode = (enabled ? REQUEST_CALENDAR_ENABLED : REQUEST_CALENDAR_DISABLED);
                            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_CALENDAR))
                            {
                                savePendingItem(activity, calendar, enabled);
                                showPermissionRational(activity, requestCode);
                                return false;

                            } else {
                                savePendingItem(activity, calendar, enabled);
                                ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.WRITE_CALENDAR }, requestCode);
                                return false;
                            }

                        } else {
                            savePendingItem(activity, calendar, enabled);
                            return runCalendarTask(activity, false, true);
                        }

                    } else {
                        return true;
                    }
                }
            };
        }

        private void showPermissionRational(final Activity activity, final int requestCode)
        {
            String permissionMessage = activity.getString(R.string.privacy_permission_calendar);
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.privacy_permissiondialog_title))
                    .setMessage(fromHtml(permissionMessage))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.WRITE_CALENDAR }, requestCode);
                        }
                    });
            builder.show();
        }

        @Override
        public void onSaveInstanceState(Bundle outState)
        {
            super.onSaveInstanceState(outState);
            if (providerVersion != null) {
                outState.putInt("providerVersion", providerVersion);
            }
        }

        private Integer providerVersion = null;
        public void setProviderVersion( Integer version )
        {
            providerVersion = version;
        }

        private Preference.OnPreferenceClickListener onAboutClick = null;
        public void setAboutClickListener( Preference.OnPreferenceClickListener onClick )
        {
            onAboutClick = onClick;
        }

        protected boolean checkDependencies()
        {
            return (providerVersion != null && providerVersion >= MIN_PROVIDER_VERSION);
        }
    }

    protected static void showMissingDepsMessage(final Activity context, View view)
    {
        if (view != null)
        {
            CharSequence message = fromHtml(context.getString(R.string.snackbar_missing_dependency, MIN_SUNTIMES_VERSION_STRING));
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);
            View snackbarView = snackbar.getView();
            snackbarView.setBackgroundColor(ContextCompat.getColor(context, R.color.snackbarError_background));
            snackbarView.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(AboutDialog.WEBSITE_URL));
                    if (intent.resolveActivity(context.getPackageManager()) != null)
                    {
                        context.startActivity(intent);
                    }
                }
            });

            TextView textView = (TextView)snackbarView.findViewById(android.support.design.R.id.snackbar_text);
            if (textView != null)
            {
                textView.setTextColor(ContextCompat.getColor(context, R.color.snackbarError_text));
                textView.setMaxLines(3);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            }

            snackbar.show();
        }
    }

    protected static void showPermissionDeniedMessage(final Activity context, View view)
    {
        if (view != null)
        {
            CharSequence message = fromHtml(context.getString(R.string.snackbar_missing_permission));
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);
            View snackbarView = snackbar.getView();
            snackbarView.setBackgroundColor(ContextCompat.getColor(context, R.color.snackbarError_background));
            snackbarView.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    // TODO: show dialog
                }
            });

            TextView textView = (TextView)snackbarView.findViewById(android.support.design.R.id.snackbar_text);
            if (textView != null)
            {
                textView.setTextColor(ContextCompat.getColor(context, R.color.snackbarError_text));
                textView.setMaxLines(7);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            }

            snackbar.show();
        }
    }

    private static SuntimesCalendarTask calendarTask = null;
    private static boolean runCalendarTask(final Activity activity, boolean clearCalendars, boolean clearPending)
    {
        if (calendarTask != null)
        {
            switch (calendarTask.getStatus())
            {
                case PENDING:
                    Log.w(TAG, "runCalendarTask: A task is already pending! ignoring...");
                    return false;

                case RUNNING:
                    Log.w(TAG, "runCalendarTask: A task is already running! ignoring...");
                    return false;
            }
        }

        calendarTask = new SuntimesCalendarTask(activity);
        calendarTask.setTaskListener(new SuntimesCalendarTask.SuntimesCalendarTaskListener()
        {
            private ProgressDialog progress;

            @Override
            public void onStarted(boolean flag_clear)
            {
                if (!flag_clear)
                {
                    //Toast.makeText(activity, activity.getString(R.string.calendars_notification_adding), Toast.LENGTH_SHORT).show();

                    progress = new ProgressDialog(activity);
                    progress.setIndeterminate(true);
                    progress.setMessage(activity.getString(R.string.calendars_notification_adding));
                    progress.setCanceledOnTouchOutside(false);
                    progress.show();
                }
            }

            @Override
            public void onSuccess(boolean flag_clear)
            {
                if (progress != null) {
                    progress.dismiss();
                }

                if (!flag_clear)
                    Toast.makeText(activity, activity.getString(R.string.calendars_notification_added), Toast.LENGTH_SHORT).show();
                else Toast.makeText(activity, activity.getString(R.string.calendars_notification_cleared), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailed(final String errorMsg)
            {
                if (progress != null) {
                    progress.dismiss();
                }

                super.onFailed(errorMsg);
                AlertDialog.Builder errorDialog = new AlertDialog.Builder(activity);
                errorDialog.setTitle(activity.getString(R.string.calendars_notification_adding_failed))
                        .setMessage(errorMsg)
                        .setIcon(R.drawable.ic_action_about)
                        .setNeutralButton(activity.getString(R.string.actionCopyError), new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    ClipData clip = ClipData.newPlainText("SuntimesCalendarErrorMsg", errorMsg);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(activity, activity.getString(R.string.actionCopyError_toast), Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setPositiveButton(android.R.string.ok, null);
                errorDialog.show();
            }
        });

        ArrayList<SuntimesCalendarTask.SuntimesCalendarTaskItem> items = new ArrayList<>();
        if (clearCalendars)
            calendarTask.setFlagClearCalendars(true);
        else items = loadItems(activity, clearPending);

        calendarTask.execute(items.toArray(new SuntimesCalendarTask.SuntimesCalendarTaskItem[0]));
        return true;
    }

    /**
     * loadItems
     */
    public static ArrayList<SuntimesCalendarTask.SuntimesCalendarTaskItem> loadItems(Activity activity, boolean clearPending)
    {
        Intent intent = activity.getIntent();
        SuntimesCalendarTask.SuntimesCalendarTaskItem[] items = (SuntimesCalendarTask.SuntimesCalendarTaskItem[]) intent.getParcelableArrayExtra(EXTRA_ON_PERMISSIONS_GRANTED);
        if (clearPending) {
            intent.removeExtra(EXTRA_ON_PERMISSIONS_GRANTED);
        }
        return new ArrayList<>(Arrays.asList(items));
    }

    /**
     * saveItems
     */
    public static void savePendingItems(Activity activity)
    {
        ArrayList<SuntimesCalendarTask.SuntimesCalendarTaskItem> items = new ArrayList<>();
        for (String calendar : SuntimesCalendarAdapter.ALL_CALENDARS) {
            if (SuntimesCalendarSettings.loadPrefCalendarEnabled(activity, calendar)) {
                items.add(new SuntimesCalendarTask.SuntimesCalendarTaskItem(calendar, SuntimesCalendarTask.SuntimesCalendarTaskItem.ACTION_UPDATE));
            }
        }
        savePendingItems(activity, items);
    }

    public static void savePendingItems(Activity activity, ArrayList<SuntimesCalendarTask.SuntimesCalendarTaskItem> items)
    {
        activity.getIntent().putExtra(EXTRA_ON_PERMISSIONS_GRANTED, items.toArray(new SuntimesCalendarTask.SuntimesCalendarTaskItem[0]));
    }

    public static void savePendingItem(Activity activity, String calendar, boolean enabled)
    {
        ArrayList<SuntimesCalendarTask.SuntimesCalendarTaskItem> items = new ArrayList<>();
        int action = (enabled ? SuntimesCalendarTask.SuntimesCalendarTaskItem.ACTION_UPDATE : SuntimesCalendarTask.SuntimesCalendarTaskItem.ACTION_DELETE);
        items.add(new SuntimesCalendarTask.SuntimesCalendarTaskItem(calendar, action));
        savePendingItems(activity, items);
    }

    /**
     * showAbout
     */
    protected void showAbout()
    {
        AboutDialog aboutDialog = new AboutDialog();
        aboutDialog.setVersion(appVersionName, providerVersionCode);
        aboutDialog.setPermissionStatus(needsSuntimesPermissions);
        aboutDialog.show(getSupportFragmentManager(), DIALOGTAG_ABOUT);
    }
    private Preference.OnPreferenceClickListener onAboutClick = new Preference.OnPreferenceClickListener()
    {
        @Override
        public boolean onPreferenceClick(Preference preference)
        {
            showAbout();
            return false;
        }
    };

    public static Spanned fromHtml(String htmlString )
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            return Html.fromHtml(htmlString, Html.FROM_HTML_MODE_LEGACY);
        else return Html.fromHtml(htmlString);
    }
}
