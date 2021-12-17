package com.example.gpstest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

//import android.support.v4.app.ActivityCompat;
//import android.test.mock.MockPackageManager;

public class MainActivity extends Activity {
    EditText textField;
    WebView webView;
    private static final int REQUEST_CODE_PERMISSION = 2;
    private ShimViewClient viewClient;
    //String mPermission = Manifest.permission.ACCESS_FINE_LOCATION;

    //GPSTracker gps;

    class ShimVersion {
        String tag = "android 0.1";

        @JavascriptInterface
        public String toString() {
            return tag;
        }

        @JavascriptInterface
        public boolean startsWith(String s) {
            return tag.startsWith(s);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    },
                    REQUEST_CODE_PERMISSION);
        } catch (Exception e) {
            e.printStackTrace();
        }

        textField = (EditText) findViewById(R.id.text_field);
        Debug.textField = textField;


        //i.putExtra("KEY1", "Value to be used by the service");
        //gps = new GPSTracker(this, textField);

        webView = (WebView) findViewById(R.id.web_view);
        viewClient = new ShimViewClient(this);
        webView.setWebViewClient(viewClient);
        webView.clearCache(true);
        webView.setWebChromeClient(new ShimChromeClient());

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(new ShimVersion(), "graasShimVersion");
        webView.loadUrl(getString(R.string.base_url) + "?mode=debug");
        //webView.loadUrl("https://storage.googleapis.com/graas-resources/test/abort-controller-2.html");


        /*try {
                ActivityCompat.requestPermissions(this, new String[]{mPermission},
                        REQUEST_CODE_PERMISSION);
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        /*
        PowerManager pm = (PowerManager)mContext.getSystemService(
                                              Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                                              PowerManager.SCREEN_DIM_WAKE_LOCK
                                              | PowerManager.ON_AFTER_RELEASE,
                                              TAG);
         wl.acquire();
         // ... do work...
         wl.release();
         */

        /*PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ON_AFTER_RELEASE,
                "shim:wakelocktag");

        wl.acquire();*/


        /*String pem = getString(R.string.pk);
        //Debug.log("- pem: " + pem);

        PrivateKey sk = CryptoUtil.importPrivateKeyFromPEM(pem);
        String msg = "Hello from the other side";
        String sig = CryptoUtil.sign(msg, sk);
        Debug.log("- sig: " + sig);*/

        /*if(!gps.canGetLocation()) {
            textField.append("location service NOT accessible\n");
        }*/
    }

    public void onDestroy() {
        unbindService(viewClient);
        webView.loadUrl("about:blank");
        super.onDestroy();
    }
}
