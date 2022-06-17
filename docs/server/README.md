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
2. When the deploy is done, visit the staging environment at `https://<VERSION_ID>-dot--<PROJECT_ID>.wl.r.appspot.com/` Get the versionID from the [Versions Console](https://console.cloud.google.com/appengine/versions). Manually ensure the app UX works as expected by scanning a QR code and starting a trip.
3. Run the following three commands from graas/server/test:
    - `NODE_PATH=../node/node_modules node post-vehicle-positions.js -u https://<version-id>-dot-lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA`
    - `NODE_PATH=../node/node_modules node post-service-alerts.js -u https://<version-id>-dot-lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA`
    - `NODE_PATH=../node/node_modules node post-stop-time-entities.js -u https://<version-id>-dot-lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA`
4. Go to the [Versions Console](https://console.cloud.google.com/appengine/versions) and direct all traffic to the new version. Select the latest version, click the 3-dot "hamburger menu" and then click "migrate."
5. Run the same tests as above, this time on the production server, from graas/server/test:
    - `NODE_PATH=../node/node_modules node post-vehicle-positions.js -u https://lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA`
    - `NODE_PATH=../node/node_modules node post-service-alerts.js -u https://lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA`
    - `NODE_PATH=../node/node_modules node post-stop-time-entities.js -u https://lat-long-prototype.wl.r.appspot.com -a pr-test -e PR_TEST_ID_ECDSA`
6. From the app-engine directory, run a server stress test with this command: `python stress-test.py ../tests/stress-test-config.json`

Generate Weekly Reports
-----------------------
Make sure that you have `docker` in your path, then run `dockerized-report.sh <agency-id>`. `agency-id` needs to be the ID for an onboarded agency.
