GTFS-RT Server
=============

Description
-----------
This repository contains an early access implementation for a GTFS-RT server and an AVL web app that runs on both android and iOS.

The server is implemented in python as a Google App Engine Flask application, the web app is available at https://<PROJECT-ID>.wl.r.appspot.com.

For steps required to onboard a new agency, please see the [Onboarding Runbook](onboarding-runbook.md).

To start running the client, press the `Start` button, then select `Route` and `Bus No` from the respective dropdowns. Press `Okay` to start sending location updates. Upon trip completion, press the `Stop` button.

Testing locally
------------
1. From `server/app-engine`, enter your virtual environment (by running something like `source venv/bin/activate `)
2. Run the command `python main.py -c <path-to-cert.pem> -k <path-to-key.pem>` to run the app on your local machine.
3. Visit `https://127.0.0.1:8080` from your web browser to test out your changes
4. (Optional) After setting up [ngrok](https://ngrok.com/), use the command `./ngrok http https://127.0.0.1:8080` to test the app from your mobile device.

Deployment
----------
1. Ensure you are on the latest version of the main branch, and run `gcloud app deploy --no-promote` from the app-engine directory. This deploys the web app to a sort of "staging environment", since web traffic continues to be directed to the previous version.
2. When the deploy is done, visit the staging environment at `https://<VERSION_ID>-dot--<PROJECT_ID>.wl.r.appspot.com/` Get the versionID from the [Versions Console](https://console.cloud.google.com/appengine/versions). Manually ensure the app UX works as expected by scanning a QR code and starting a trip. Note that all API requests will be routed to the previous instance, so this method won't test back-end updates.
3. After checking that the front end updates are working correctly to the [Versions Console](https://console.cloud.google.com/appengine/versions) and direct all traffic to the new version. Select the latest version, click the 3-dot "hamburger menu" and then click "migrate."
4. From server/test, run the command `NODE_PATH=../node/node_modules node post-position-update.js` to post an update to the server.
5. From the gtfu directory, and within 30 seconds after running step 4, run Java tests with this command: `java -cp build/classes/java/test:build/libs/gtfu.jar gtfu.test.ServerTest pr-test`
6. From the app-engine directory, run a server stress test with this command: `python stress-test.py ../tests/stress-test-config.json`

Generate Weekly Reports
-----------------------
Make sure that you have `docker` in your path, then run `dockerized-report.sh <agency-id>`. `agency-id` needs to be the ID for an onboarded agency.
