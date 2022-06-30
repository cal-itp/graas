import WebKit
import CoreLocation

class NavigatorGeolocation: NSObject, WKScriptMessageHandler, CLLocationManagerDelegate {

    var locationManager = CLLocationManager();
    var listenerCount = 0;
    var webView: WKWebView!;

    override init() {
        super.init();
        locationManager.delegate = self;
    }

    func setWebView(webView: WKWebView) {
        webView.configuration.userContentController.add(self, name: "listenerAdded");
        webView.configuration.userContentController.add(self, name: "listenerRemoved");
        self.webView = webView;
    }

    func locationServicesIsEnabled() -> Bool {
        return (CLLocationManager.locationServicesEnabled()) ? true : false;
    }

    func authorizationStatusNeedRequest(status: CLAuthorizationStatus) -> Bool {
        return (status == .notDetermined) ? true : false;
    }

    func authorizationStatusIsGranted(status: CLAuthorizationStatus) -> Bool {
        return (status == .authorizedAlways || status == .authorizedWhenInUse) ? true : false;
    }

    func authorizationStatusIsDenied(status: CLAuthorizationStatus) -> Bool {
        return (status == .restricted || status == .denied) ? true : false;
    }

    func onLocationServicesIsDisabled() {
        webView.evaluateJavaScript("navigator.geolocation.helper.error(2, 'Location services disabled');");
    }

    func onAuthorizationStatusNeedRequest() {
        locationManager.requestWhenInUseAuthorization();
    }

    func onAuthorizationStatusIsGranted() {
        locationManager.startUpdatingLocation();
    }

    func onAuthorizationStatusIsDenied() {
        webView.evaluateJavaScript("navigator.geolocation.helper.error(1, 'App does not have location permission');");
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if (message.name == "listenerAdded") {
            listenerCount += 1;

            if (!locationServicesIsEnabled()) {
                onLocationServicesIsDisabled();
            }
            else if (authorizationStatusIsDenied(status: CLLocationManager.authorizationStatus())) {
                onAuthorizationStatusIsDenied();
            }
            else if (authorizationStatusNeedRequest(status: CLLocationManager.authorizationStatus())) {
                onAuthorizationStatusNeedRequest();
            }
            else if (authorizationStatusIsGranted(status: CLLocationManager.authorizationStatus())) {
                onAuthorizationStatusIsGranted();
            }
        }
        else if (message.name == "listenerRemoved") {
            listenerCount -= 1;

            // no listener left in web view to wait for position
            if (listenerCount == 0) {
                locationManager.stopUpdatingLocation();
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        // didChangeAuthorization is also called at app startup, so this condition checks listeners
        // count before doing anything otherwise app will start location service without reason
        if (listenerCount > 0) {
            if (authorizationStatusIsDenied(status: status)) {
                onAuthorizationStatusIsDenied();
            }
            else if (authorizationStatusIsGranted(status: status)) {
                onAuthorizationStatusIsGranted();
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let location = locations.last {
            webView.evaluateJavaScript("navigator.geolocation.helper.success('\(location.timestamp)', \(location.coordinate.latitude), \(location.coordinate.longitude), \(location.altitude), \(location.horizontalAccuracy), \(location.verticalAccuracy), \(location.course), \(location.speed));");
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        webView.evaluateJavaScript("navigator.geolocation.helper.error(2, 'Failed to get position (\(error.localizedDescription))');");
    }
}
