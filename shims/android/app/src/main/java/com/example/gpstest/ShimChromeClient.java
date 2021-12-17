package com.example.gpstest;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.annotation.RequiresApi;

public class ShimChromeClient extends WebChromeClient {
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        Log.d("gpstest", "[JS console] " + consoleMessage.message());
        return true;
    }

    /*public void onConsoleMessage(String message, int lineNumber, String sourceID) {
        Log.d("gpstest", message + " -- From line " +
                lineNumber + " of " + sourceID);
    }*/

    public void onGeolocationPermissionsHidePrompt() {
        Log.d("gpstest", "onGeolocationPermissionsHidePrompt()");
    }

    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        Log.d("gpstest", "onGeolocationPermissionsShowPrompt()");
        Log.d("gpstest", "- origin: " + origin);
        callback.invoke(origin, true, true);
    }

    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        Log.d("gpstest", "onJsAlert()");
        return false;
    }

    public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
        Log.d("gpstest", "onJsBeforeUnload()");
        return false;
    }

    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        Log.d("gpstest", "onJsBeforeUnload()");
        return false;
    }

    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        Log.d("gpstest", "onJsPrompt()");
        return false;
    }

    public boolean onJsTimeout() {
        Log.d("gpstest", "onJsTimeout()");
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onPermissionRequest(PermissionRequest request) {
        Log.d("gpstest", "onPermissionRequest()");
        request.grant(new String[] {PermissionRequest.RESOURCE_VIDEO_CAPTURE});
    }

    public void onPermissionRequestCanceled(PermissionRequest request) {
        Log.d("gpstest", "onPermissionRequestCanceled()");
    }

    public void onProgressChanged(WebView view, int newProgress) {
        Log.d("gpstest", "onProgressChanged()");
    }

    public void onReachedMaxAppCacheSize(long requiredStorage, long quota, WebStorage.QuotaUpdater quotaUpdater) {
        Log.d("gpstest", "onReachedMaxAppCacheSize()");
    }

    public void onReceivedIcon(WebView view, Bitmap icon) {
        Log.d("gpstest", "onReceivedIcon()");
    }

    public void onReceivedTitle(WebView view, String title) {
        Log.d("gpstest", "onReceivedIcon()");
    }

    public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
        Log.d("gpstest", "onReceivedTouchIconUrl()");
    }

    public void onRequestFocus(WebView view) {
        Log.d("gpstest", "onRequestFocus()");
    }

    public void onShowCustomView(View view, int requestedOrientation, WebChromeClient.CustomViewCallback callback) {
        Log.d("gpstest", "onShowCustomView()");
    }

    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        Log.d("gpstest", "onShowCustomView()");
    }

    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        Log.d("gpstest", "onShowFileChooser()");
        return true;
    }
}
