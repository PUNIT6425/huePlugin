
package com.ajimitei.kadecot.plugin.hue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class is a implementation of Settings activity. <br>
 * This activity is able to be launched by Kadecot devices tab.
 */
public class SettingsActivity extends Activity {

    static HuePluginService mService;
    private static EditTextPreference usernamePref;
    private static EditTextPreference createUsernamePref;
    private static SharedPreferences pref;
    private static SharedPreferences.Editor editor;
    private static final String PREF_KEY = "hue";
    private static String mHueUsername;
    private static String mDeviceType;
    private static String mHueIpAddress;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((HuePluginService.HuePluginServiceBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        bindService(new Intent(this, HuePluginService.class),
                mServiceConnection, BIND_ABOVE_CLIENT);

        mDeviceType = getIntent().getExtras().getString("deviceType");
        mHueIpAddress = getIntent().getExtras().getString("ipAddress");
        mHueIpAddress = "http://" + mHueIpAddress + ":80/";
        pref = getSharedPreferences(PREF_KEY, Activity.MODE_PRIVATE);
        mHueUsername = pref.getString(mHueIpAddress, "");

        Log.v("ipaddess", getIntent().getExtras().getString("ipAddress"));
        Log.v("mHueIpAddress", mHueIpAddress);
        Log.v("mHueUsername", mHueUsername);

        getFragmentManager().beginTransaction()
                .add(R.id.container, SettingsFragment.newInstance())
                .commit();
    }

    @Override
    protected void onDestroy() {
        unbindService(mServiceConnection);
        super.onDestroy();
    }

    public static final class SettingsFragment extends PreferenceFragment {

        private static boolean mWaitResponse;
        private static boolean mCreateUser;

        public static SettingsFragment newInstance() {
            return new SettingsFragment();
        }

        public SettingsFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.hue_preferences);

            createUsernamePref = (EditTextPreference) findPreference("huecreateusername");
            createUsernamePref.setText("");
            if (mDeviceType.equals("bridge")) {
                createUsernamePref.setEnabled(true);
            } else {
                createUsernamePref.setEnabled(false);
            }

            createUsernamePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, final Object arg1) {
                    mWaitResponse = false;
                    mCreateUser = false;

                    Thread trd = new Thread(new Runnable() {
                        public void run() {
                            String url = mHueIpAddress + "api";
                            Log.v("url", url);
                            JSONObject postData = null;
                            try {
                                postData = new JSONObject().put("devicetype", "test user").put(
                                        "username", arg1.toString());
                                Log.v("postData", postData.toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            OutputStream outputStream = null;
                            OutputStreamWriter writer = null;
                            InputStream inputStream = null;
                            String response = null;
                            URL _url;
                            HttpURLConnection httpConn = null;

                            try {
                                _url = new URL(url);
                                httpConn = (HttpURLConnection) _url.openConnection();
                                httpConn.setRequestMethod("POST");

                                outputStream = httpConn.getOutputStream();
                                writer = new OutputStreamWriter(outputStream, "UTF-8");
                                writer.write(postData.toString());
                                writer.flush();
                                writer.close();
                                writer = null;
                                outputStream.close();
                                outputStream = null;

                                int responseCode = httpConn.getResponseCode();
                                if (responseCode == HttpURLConnection.HTTP_OK) {
                                    inputStream = httpConn.getInputStream();
                                    BufferedReader br = new BufferedReader(new InputStreamReader(
                                            inputStream));
                                    StringBuilder sb = new StringBuilder();
                                    String line;
                                    while ((line = br.readLine()) != null) {
                                        sb.append(line);
                                    }
                                    response = sb.toString();
                                    inputStream.close();
                                }

                                JSONArray resultObject = new JSONArray(response);
                                JSONObject rootObject = resultObject.getJSONObject(0);
                                Log.v("rootObject", rootObject.toString());

                                if (rootObject.has("success")) {
                                    mCreateUser = true;
                                } else {
                                    Log.v("description", rootObject.getJSONObject("error")
                                            .toString());

                                    mCreateUser = false;
                                }

                                mWaitResponse = true;

                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            } finally {
                                httpConn.disconnect();
                            }

                        };
                    });
                    trd.start();

                    int i = 0;
                    while (!mWaitResponse && i < 10) {
                        try {
                            i++;
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (mCreateUser && mWaitResponse) {
                        Log.v("response", "OK");
                        editor = pref.edit();
                        editor.putString(mHueIpAddress, arg1.toString());
                        editor.apply();
                        mHueUsername = arg1.toString();
                        usernamePref.setSummary(mHueUsername);
                        usernamePref.setText(mHueUsername);
                        Toast.makeText(getActivity(), R.string.hue_plugin_create_username_success,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Log.v("response", "NG");
                        Toast.makeText(getActivity(), R.string.hue_plugin_create_username_fail,
                                Toast.LENGTH_LONG).show();
                    }

                    return false;
                }
            });

            usernamePref = (EditTextPreference) findPreference("hueusername");
            usernamePref.setSummary(mHueUsername);
            usernamePref.setText(mHueUsername);
            if (mDeviceType.equals("bridge")) {
                usernamePref.setEnabled(true);
            } else {
                usernamePref.setEnabled(false);
            }

            usernamePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object arg1) {
                    editor = pref.edit();
                    editor.putString(mHueIpAddress, arg1.toString());
                    editor.apply();
                    usernamePref.setSummary(arg1.toString());
                    return true;
                }
            });
        }
    }
}
