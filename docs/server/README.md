GTFS-RT Server
=============

Description
-----------
This repository contains an early access implementation for a GTFS-RT server and an AVL web app that runs on both android and iOS.

The server is implemented in python as a Google App Engine Flask application, the web app is available at https://lat-long-prototype.wl.r.appspot.com.

For steps required to onboard a new agency, please see the [Onboarding Runbook](onboarding-runbook.md).

To start running the client, press the `Start` button, then select `Route` and `Bus No` from the respective dropdowns. Press `Okay` to start sending location updates. Upon trip completion, press the `Stop` button.

Deployment
----------
Issue `gcloud app deploy` from the app-engine folder of this repository.

Generate Weekly Reports
-----------------------
Make sure that you have `docker` in your path, then run `dockerized-report.sh <agency-id>`. `agency-id` needs to be the ID for an onboarded agency.
