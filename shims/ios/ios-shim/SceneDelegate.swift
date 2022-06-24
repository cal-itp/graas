//
//  SceneDelegate.swift
//  ios-shim
//
//  Created by wildcard on 5/12/21.
//  Copyright © 2021 Cal-ITP. All rights reserved.
//

import UIKit
import SwiftUI
import CoreLocation
import CryptoKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate, CLLocationManagerDelegate {

    var window: UIWindow?
    //let locationManager = CLLocationManager()

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        UIApplication.shared.isIdleTimerDisabled = true
        
        /*locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.requestAlwaysAuthorization()
        locationManager.startUpdatingLocation()*/

        let baseURL = Bundle.main.object(forInfoDictionaryKey: "baseURL") as? String
        let contentView = WebView(request: URLRequest(url: URL(string:
                                                                baseURL! + "?mode=debug")!))

        if let windowScene = scene as? UIWindowScene {
            let window = UIWindow(windowScene: windowScene)
            window.rootViewController = UIHostingController(rootView: contentView)
            self.window = window
            window.makeKeyAndVisible()
        }
    }

    func sceneDidDisconnect(_ scene: UIScene) {
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
    }

    func sceneWillResignActive(_ scene: UIScene) {
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
    }

    /*
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {

        // "locations" contain the arrays of Data, in order to get the latest data we have to use the last one so,
        // the last Data will be locations[locations.count - 1]
        let location = locations[locations.count - 1]

        // To know if the Data that our device is correct, The "horizontalAccuracy" value should be grater then ZERO for valid GPS coordinate Data
        if location.horizontalAccuracy > 0 {
            //TODO: Step 7 ####################################################################
            // Time to stop collecting GPS data, as it will keep updating and will kill the battry of Device if we don't
            //locationManager.stopUpdatingLocation()


            // Getting coordinate
            let longitude = location.coordinate.longitude
            print("- longitude: \(longitude)")
            let latitude = location.coordinate.latitude
            print("- latitude: \(latitude)")

            // Getting Speed
            // The instantaneous speed of the device, measured in meters per second.
            let speed = location.speed
            print("- speed: \(speed)")

            // Getting The when the GPS data was taken
            let timeStamp = location.timestamp
            print("- timeStamp: \(timeStamp)")

            // The direction in which the device is traveling.
            let courseDirection = location.course
            print("- courseDirection: \(courseDirection)")
        }
    }

    // when there is an Error while collecting GPS coordinate data by "locationManager" this method is called

    //Write the didFailWithError method here:
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("*** location manager error: \(error)")
    }
     */
}

