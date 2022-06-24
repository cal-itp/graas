import SwiftUI
import WebKit

final class WebView : NSObject, UIViewRepresentable, WKScriptMessageHandler {
    let injectionSource = """
        function captureLog(msg) {
            window.webkit.messageHandlers.logHandler.postMessage(msg);
        }

        window.console.log = captureLog;
        window.console.error = captureLog;
        var graasShimVersion = 'ios 0.1';

        // management for success and error listeners and its calling
        navigator.geolocation.helper = {
         listeners: {},
         noop: function() {},
         id: function() {
             var min = 1, max = 1000;
             return Math.floor(Math.random() * (max - min + 1)) + min;
         },
         clear: function(isError) {
             for (var id in this.listeners) {
                 if (isError || this.listeners[id].onetime) {
                     navigator.geolocation.clearWatch(id);
                 }
             }
         },
         success: function(timestamp, latitude, longitude, altitude, accuracy, altitudeAccuracy, heading, speed) {
             var position = {
                 timestamp: new Date(timestamp).getTime() || new Date().getTime(), // safari can not parse date format returned by swift e.g. 2019-12-27 15:46:59 +0000 (fallback used because we trust that safari will learn it in future because chrome knows that format)
                 coords: {
                     latitude: latitude,
                     longitude: longitude,
                     altitude: altitude,
                     accuracy: accuracy,
                     altitudeAccuracy: altitudeAccuracy,
                     heading: (heading > 0) ? heading : null,
                     speed: (speed > 0) ? speed : null
                 }
             };
             for (var id in this.listeners) {
                 this.listeners[id].success(position);
             }
             this.clear(false);
         },
         error: function(code, message) {
             var error = {
                 PERMISSION_DENIED: 1,
                 POSITION_UNAVAILABLE: 2,
                 TIMEOUT: 3,
                 code: code,
                 message: message
             };
             for (var id in this.listeners) {
                 this.listeners[id].error(error);
             }
             this.clear(true);
         }
        };

        // @override getCurrentPosition()
        navigator.geolocation.getCurrentPosition = function(success, error, options) {
         var id = this.helper.id();
         this.helper.listeners[id] = { onetime: true, success: success || this.noop, error: error || this.noop };
         window.webkit.messageHandlers.listenerAdded.postMessage("");
        };

        // @override watchPosition()
        navigator.geolocation.watchPosition = function(success, error, options) {
         var id = this.helper.id();
         this.helper.listeners[id] = { onetime: false, success: success || this.noop, error: error || this.noop };
         window.webkit.messageHandlers.listenerAdded.postMessage("");
         return id;
        };

        // @override clearWatch()
        navigator.geolocation.clearWatch = function(id) {
         var idExists = (this.helper.listeners[id]) ? true : false;
         if (idExists) {
             this.helper.listeners[id] = null;
             delete this.helper.listeners[id];
             window.webkit.messageHandlers.listenerRemoved.postMessage("");
         }
        };
   """
    let request: URLRequest
    var navigatorGeolocation = NavigatorGeolocation();

    init(request : URLRequest) {
        print("WebView.init()")
        self.request = request
    }

    func makeUIView(context: Context) -> WKWebView  {
        let webview = WKWebView()
        webview.configuration.preferences.javaScriptEnabled = true
        webview.configuration.websiteDataStore = WKWebsiteDataStore.default()
        navigatorGeolocation.setWebView(webView: webview)
        // inject JS to capture console.log output and send to iOS
        let script = WKUserScript(source: injectionSource, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        webview.configuration.userContentController.addUserScript(script)
        // register the bridge script that listens for the output
        webview.configuration.userContentController.add(self, name: "logHandler")
        print("+ set up webview")

        let websiteDataTypes = NSSet(array: [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache])
        let date = Date(timeIntervalSince1970: 0)
        WKWebsiteDataStore.default().removeData(ofTypes: websiteDataTypes as! Set<String>, modifiedSince: date, completionHandler:{ })

        DispatchQueue.global(qos: .userInitiated).async {
            var done = false

            print("pre-sleeping...")
            Thread.sleep(forTimeInterval: 10)
            print("done")

            while !done {
                Thread.sleep(forTimeInterval: 1)

                if Reachability.isConnectedToNetwork() {
                    DispatchQueue.main.async {
                        print("loading request...")
                        webview.load(self.request)
                    }
                    done = true
               } else {
                   print("waiting for connectivity...")
               }
            }
        }

        return webview
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "logHandler" {
            print("JS: \(message.body)")
        }
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        print("- updateUIView()")
        //uiView.load(request)
    }

}
