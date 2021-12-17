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
    let locationManager = CLLocationManager()

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        // Use this method to optionally configure and attach the UIWindow `window` to the provided UIWindowScene `scene`.
        // If using a storyboard, the `window` property will automatically be initialized and attached to the scene.
        // This delegate does not imply the connecting scene or session are new (see `application:configurationForConnectingSceneSession` instead).
        UIApplication.shared.isIdleTimerDisabled = true
        
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.requestAlwaysAuthorization()
        locationManager.startUpdatingLocation()

        // Create the SwiftUI view that provides the window contents.
        //let contentView = ContentView()
        let baseURL = Bundle.main.object(forInfoDictionaryKey: "baseURL") as? String
        let contentView = WebView(request: URLRequest(url: URL(string:
                                                                baseURL! + "?mode=debug")!))

        // Use a UIHostingController as window root view controller.
        if let windowScene = scene as? UIWindowScene {
            let window = UIWindow(windowScene: windowScene)
            window.rootViewController = UIHostingController(rootView: contentView)
            self.window = window
            window.makeKeyAndVisible()
        }
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        // Called as the scene is being released by the system.
        // This occurs shortly after the scene enters the background, or when its session is discarded.
        // Release any resources associated with this scene that can be re-created the next time the scene connects.
        // The scene may re-connect later, as its session was not neccessarily discarded (see `application:didDiscardSceneSessions` instead).
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // Called when the scene has moved from an inactive state to an active state.
        // Use this method to restart any tasks that were paused (or not yet started) when the scene was inactive.
    }

    func sceneWillResignActive(_ scene: UIScene) {
        // Called when the scene will move from an active state to an inactive state.
        // This may occur due to temporary interruptions (ex. an incoming phone call).
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        // Called as the scene transitions from the background to the foreground.
        // Use this method to undo the changes made on entering the background.
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        // Called as the scene transitions from the foreground to the background.
        // Use this method to save data, release shared resources, and store enough scene-specific state information
        // to restore the scene back to its current state.
    }

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

    func derToRaw(der: Data) -> Data {
        let raw = Data()

        // ### copy r, s from der to raw

        return raw
    }

    func sign(msg: String) -> String? {
        let der = Data(base64Encoded: "")
        let raw = derToRaw(der: der!)

        do {
            let key = try P256.Signing.PrivateKey(rawRepresentation: raw)
            let sign = try key.signature(for: msg.data(using: .utf8)!)
            return sign.derRepresentation.base64EncodedString()
        } catch {
            print("sign() failed")
            return nil
        }
    }

    func post(msg: Data) {
        /*
         let json: [String: Any] = ["title": "ABC",
                                    "dict": ["1":"First", "2":"Second"]]

         let jsonData = try? JSONSerialization.data(withJSONObject: json)

         // create post request
         let url = URL(string: "http://httpbin.org/post")!
         var request = URLRequest(url: url)
         request.httpMethod = "POST"

         // insert json data to the request
         request.httpBody = jsonData

         let task = URLSession.shared.dataTask(with: request) { data, response, error in
             guard let data = data, error == nil else {
                 print(error?.localizedDescription ?? "No data")
                 return
             }
             let responseJSON = try? JSONSerialization.jsonObject(with: data, options: [])
             if let responseJSON = responseJSON as? [String: Any] {
                 print(responseJSON)
             }
         }

         task.resume()
        */
    }
}

