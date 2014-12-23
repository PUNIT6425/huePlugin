
package com.ajimitei.kadecot.plugin.hue;

import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.example.sony.cameraremote.ServerDevice;
import com.example.sony.cameraremote.SimpleSsdpClient;
import com.sonycsl.Kadecot.plugin.DeviceData;
import com.sonycsl.Kadecot.plugin.KadecotProtocolClient;
import com.sonycsl.wamp.WampError;
import com.sonycsl.wamp.message.WampEventMessage;
import com.sonycsl.wamp.message.WampMessage;
import com.sonycsl.wamp.message.WampMessageFactory;
import com.sonycsl.wamp.message.WampMessageType;
import com.sonycsl.wamp.role.WampCallee.WampInvocationReplyListener;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HueProtocolClient extends KadecotProtocolClient {

    private static final String TAG = "hue plugin";
    static final String PROTOCOL_NAME = "hue";

    static final String DEVICE_TYPE_BRIDGE = "bridge";
    static final String DEVICE_TYPE_HUE = "hue";

    private static final String PRE_FIX = "com.sonycsl.kadecot." + PROTOCOL_NAME;
    private static final String PROCEDURE = ".procedure.";
    public static String mHueUsername = "";

    private SimpleSsdpClient mSsdpClient = new SimpleSsdpClient();
    private static String mIpAddress = null;
    private static SharedPreferences pref;
    private final Map<String, HueDeviceInfo> mInfos = new ConcurrentHashMap<String, HueDeviceInfo>();

    private Handler mHandler;

    public static enum Procedure {
        SET_MESSAGES("lights.state.set", ""),
        GET_MESSAGES("lights.state.get", ""), ;

        private final String mUri;
        private final String mServiceName;
        private final String mDescription;

        /**
         * @param servicename
         * @param description is displayed on JSONP called /v
         */
        Procedure(String servicename, String description) {
            mUri = PRE_FIX + PROCEDURE + servicename;
            mServiceName = servicename;
            mDescription = description;
        }

        public String getUri() {
            return mUri;
        }

        public String getServiceName() {
            return mServiceName;
        }

        public String getDescription() {
            return mDescription;
        }

        public static Procedure getEnum(String procedure) {
            for (Procedure p : Procedure.values()) {
                if (p.getUri().equals(procedure)) {
                    return p;
                }
            }
            return null;
        }
    }

    public HueProtocolClient(String ipAddress, SharedPreferences preferences) {
        mHandler = new Handler();
        mIpAddress = ipAddress;
        pref = preferences;
    }

    /**
     * Get the topics this plug-in want to SUBSCRIBE <br>
     */
    @Override
    public Set<String> getTopicsToSubscribe() {
        return new HashSet<String>();
    }

    /**
     * Get the procedures this plug-in supported. <br>
     */
    @Override
    public Map<String, String> getRegisterableProcedures() {
        Map<String, String> procs = new HashMap<String, String>();
        for (Procedure p : Procedure.values()) {
            procs.put(p.getUri(), p.getDescription());
        }
        return procs;
    }

    /**
     * Get the topics this plug-in supported. <br>
     */
    @Override
    public Map<String, String> getSubscribableTopics() {
        return new HashMap<String, String>();
    }

    private void searchDevice() {

        mSsdpClient.search(new SimpleSsdpClient.SearchResultHandler() {

            @Override
            public void onDeviceFound(final ServerDevice device) {
                // Called by non-UI thread.
                Log.d(TAG, ">> Search device found: " + device.getFriendlyName());
                new Thread() {
                    @Override
                    public void run() {
                        Log.v(TAG + "getIpAddres", device.getIpAddres());
                        Log.v(TAG + "getDDUrl", device.getDDUrl());
                        Log.v(TAG + "getFriendlyName", device.getFriendlyName());
                        Log.v(TAG + "getModelName", device.getModelName());
                        registerDevice(new DeviceData.Builder(PROTOCOL_NAME, "hue bridge:"
                                + device.getIpAddres(),
                                DEVICE_TYPE_BRIDGE, "hue bridge:" + device.getIpAddres(), true,
                                device.getIpAddres()).build());

                        HueDeviceInfo info = new HueDeviceInfo();
                        info.mDescription = "hue bridge:" + device.getIpAddres();
                        info.mDeviceType = DEVICE_TYPE_BRIDGE;
                        info.mNetworkAddress = device.getIpAddres();
                        info.mProtocol = PROTOCOL_NAME;

                        mInfos.put("hue bridge:" + device.getIpAddres(), info);

                        mHueUsername = pref.getString("http://" + device.getIpAddres() + ":80/", "");
                        Log.v("pref.getString",
                                pref.getString("http://" + device.getIpAddres() + ":80/", ""));
                        Log.v("deviceInfo.getUrlBase()", device.getIpAddres());

                        if (mHueUsername == null) {
                            Log.v("mHueUsername", "mHueUsername is null");
                            mHueUsername = "";
                        } else {
                            Log.v("mHueUsername", mHueUsername);
                        }

                        String baseUrl = "http://" + device.getIpAddres() + "/api/" + mHueUsername
                                + "/lights/";
                        Log.v(TAG + " baseUrl", baseUrl);

                        try {
                            HttpGet httpGet = new HttpGet(baseUrl);
                            DefaultHttpClient client = new DefaultHttpClient();
                            HttpResponse httpResponse = null;
                            httpResponse = client.execute(httpGet);
                            HttpEntity entity = httpResponse.getEntity();
                            String response = EntityUtils.toString(entity);
                            JSONObject rootObject;

                            try {
                                rootObject = new JSONObject(response);
                                JSONObject jsonObject = null;
                                int count = 1;
                                while (count <= 50) {
                                    String sCount = String.valueOf(count);
                                    if (rootObject.has(sCount)) {
                                        jsonObject = rootObject.getJSONObject(sCount);
                                    } else {
                                        break;
                                    }

                                    String infoUrl = baseUrl + sCount;
                                    Log.v(TAG + " infoUrl", infoUrl);

                                    registerDevice(new DeviceData.Builder(PROTOCOL_NAME,
                                            "hue lamp:" + device.getIpAddres() + ":" + sCount,
                                            DEVICE_TYPE_HUE, "hue lamp:" + device.getIpAddres()
                                                    + ":" + sCount,
                                            true, infoUrl).build());

                                    HueDeviceInfo lampInfo = new HueDeviceInfo();
                                    lampInfo.mDescription = "hue lamp:" + device.getIpAddres()
                                            + ":" + sCount;
                                    lampInfo.mDeviceType = DEVICE_TYPE_HUE;
                                    lampInfo.mNetworkAddress = infoUrl;
                                    lampInfo.mProtocol = PROTOCOL_NAME;

                                    mInfos.put("hue lamp:" + device.getIpAddres() + ":" + sCount,
                                            lampInfo);

                                    count++;
                                }

                                entity.consumeContent();
                                client.getConnectionManager().shutdown();

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        } catch (ParseException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                    }
                }.start();

            }

            @Override
            public void onFinished() {
                // Called by non-UI thread.
                Log.d(TAG, ">> Search finished.");
                new Thread() {
                    @Override
                    public void run() {
                    }
                }.start();
            }

            @Override
            public void onErrorFinished() {
                // Called by non-UI thread.
                Log.d(TAG, ">> Search Error finished.");
                new Thread() {
                    @Override
                    public void run() {
                    }
                }.start();
            }
        });
        /**
         * Call after finding device.
         */

    }

    @Override
    public void onSearchEvent(WampEventMessage eventMsg) {
        searchDevice();
    }

    @Override
    protected void onInvocation(final int requestId, String procedure, final String uuid,
            final JSONObject argumentsKw, final WampInvocationReplyListener listener) {
        WampMessage reply = null;

        Log.v(TAG + "requestId", Integer.toString(requestId));
        Log.v(TAG + "procedure", procedure);
        Log.v(TAG + "uuid", uuid);
        Log.v(TAG + "argumentsKw", argumentsKw.toString());

        HueDeviceInfo info = mInfos.get(uuid);
        if (info == null) {
            Log.v(TAG, " HueDeviceInfo info is null");
            return;
        } else {
            Log.v(TAG + "info.mDescription", info.mDescription);
            Log.v(TAG + "info.mDeviceType", info.mDeviceType);
            Log.v(TAG + "info.mNetworkAddress", info.mNetworkAddress);
            Log.v(TAG + "info.mProtocol", info.mProtocol);
        }

        try {
            final Procedure proc = Procedure.getEnum(procedure);
            if (proc == Procedure.GET_MESSAGES) {
                HttpGet httpGet = new HttpGet(info.mNetworkAddress);
                DefaultHttpClient client = new DefaultHttpClient();
                HttpResponse httpResponse;
                try {
                    httpResponse = client.execute(httpGet);
                    HttpEntity entity = httpResponse.getEntity();
                    String response = EntityUtils.toString(entity);
                    JSONObject rootObject;
                    rootObject = new JSONObject(response);
                    reply = WampMessageFactory.createYield(requestId,
                            new JSONObject(), new JSONArray(), rootObject);
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                listener.replyYield(reply.asYieldMessage());
                return;

            }

            if (proc == Procedure.SET_MESSAGES) {

                String url = info.mNetworkAddress + "/state";
                OutputStream outputStream = null;
                OutputStreamWriter writer = null;
                InputStream inputStream = null;
                String response = null;
                URL _url;
                HttpURLConnection httpConn = null;

                try {
                    _url = new URL(url);
                    httpConn = (HttpURLConnection) _url.openConnection();
                    httpConn.setRequestMethod("PUT");

                    outputStream = httpConn.getOutputStream();
                    writer = new OutputStreamWriter(outputStream, "UTF-8");
                    writer.write(argumentsKw.toString());
                    writer.flush();
                    writer.close();
                    writer = null;
                    outputStream.close();
                    outputStream = null;

                    int responseCode = httpConn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        inputStream = httpConn.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        response = sb.toString();
                        inputStream.close();
                    }

                    JSONObject rootObject = new JSONObject();
                    JSONArray resultObject = new JSONArray(response);
                    rootObject.put("result", resultObject);
                    reply = WampMessageFactory.createYield(requestId,
                            new JSONObject(), new JSONArray(), rootObject);

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } finally {
                    httpConn.disconnect();
                }
                listener.replyYield(reply.asYieldMessage());
                return;

            }

            /**
             * Return YIELD message as a result of INVOCATION.
             */
            JSONObject argumentKw = new JSONObject().put("targetDevice", uuid).put(
                    "calledProcedure", procedure);

            listener.replyYield(WampMessageFactory.createYield(requestId, new JSONObject(),
                    new JSONArray(), argumentKw).asYieldMessage());
        } catch (JSONException e) {
            listener.replyError(WampMessageFactory
                    .createError(WampMessageType.INVOCATION, requestId,
                            new JSONObject(), WampError.INVALID_ARGUMENT, new JSONArray(),
                            new JSONObject()).asErrorMessage());
        }
    }
}
