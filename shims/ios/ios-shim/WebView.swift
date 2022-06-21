import SwiftUI
import WebKit

final class WebView : NSObject, UIViewRepresentable, WKScriptMessageHandler {
    let request: URLRequest

    init(request : URLRequest) {
        print("WebView.init()")
        self.request = request
    }

    func makeUIView(context: Context) -> WKWebView  {
        let webview = WKWebView()
        webview.configuration.preferences.javaScriptEnabled = true
        webview.configuration.websiteDataStore = WKWebsiteDataStore.default()
        // inject JS to capture console.log output and send to iOS
        let source = "function captureLog(msg) { window.webkit.messageHandlers.logHandler.postMessage(msg); } window.console.log = captureLog; window.console.error = captureLog; var graasShimVersion = 'ios 0.1';"
        let script = WKUserScript(source: source, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        webview.configuration.userContentController.addUserScript(script)
        // register the bridge script that listens for the output
        webview.configuration.userContentController.add(self, name: "logHandler")
        print("+ set up webview")

        let websiteDataTypes = NSSet(array: [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache])
        let date = Date(timeIntervalSince1970: 0)
        WKWebsiteDataStore.default().removeData(ofTypes: websiteDataTypes as! Set<String>, modifiedSince: date, completionHandler:{ })

        DispatchQueue.global(qos: .userInitiated).async {
            var done = false

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
