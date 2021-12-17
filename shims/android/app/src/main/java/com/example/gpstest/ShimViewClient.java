package com.example.gpstest;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayInputStream;

public class ShimViewClient extends WebViewClient implements ServiceConnection {
    private Intent serviceIntent;
    private GraasService graasService;
    private Context context;
    private PowerManager.WakeLock wakeLock;

    public ShimViewClient(Context context) {
        this.context = context;
        serviceIntent = new Intent(context, GraasService.class);
        context.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK,
                "graas::wakelock");
        wakeLock.setReferenceCounted(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public WebResourceResponse shouldInterceptRequest (WebView view,
                          WebResourceRequest request) {
        Log.d("gpstest", "ShimWebViewClient.shouldInterceptRequest()");

        String requestURL = request.getUrl().toString();
        Log.d("gpstest", "- requestURL: " + requestURL);
        Log.d("gpstest", "- graasService: " + graasService);

        if (graasService != null && requestURL.endsWith("/graas-location")) {
            //String json = "{\"lat\": 34.56789, \"long\": 123.45678}";
            String json = graasService.getPositionString();
            Log.d("gpstest", "- json: " + json);
            ByteArrayInputStream buf = new ByteArrayInputStream(json.getBytes());
            return new WebResourceResponse("application/json", "utf-8", buf);
        }

        if (requestURL.endsWith("/graas-start")) {
            Log.d("gpstest", "+ starting graas service");
            context.startForegroundService(serviceIntent);
            wakeLock.acquire();
            return null;
        }

        if (requestURL.endsWith("/graas-stop")) {
            Log.d("gpstest", "+ stopping graas service");

            if (graasService != null) {
                graasService.stopForeground(true);
                graasService.stopSelf();
            }

            context.stopService(serviceIntent);
            wakeLock.release();
            return null;
        }

        return null;
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        Log.e("Error", description);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.d("gpstest", "onServiceConnected()");
        GraasService.GraasBinder b = (GraasService.GraasBinder) binder;
        graasService = b.getService();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d("gpstest", "onServiceDisconnected()");
        graasService = null;
    }
}
