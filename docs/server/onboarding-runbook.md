Agency Onboarding Runbook
=========================

Onboarder prerequisites
-----------------------
`Onboarder` refers to the person performing the agency onboarding. Oboarder requires:
- `Editor` access to google cloud project
- the following command line tools:
  - `git`
  - `gcloud`
  - `gsutil`
- clone two git repos: graas & graas-agency-keys.

__TODO__: dockerize onboarding

Agency prerequisites
--------------------
- agency must have a publicly available static GTFS URL, including headsigns for each trip. You can verify that a given URL is valid by
  - downloading the contents of the URL with the command `curl -o gtfs.zip <agency-gtfs-url>`
  - unpacking the zip with `unzip gtfs.zip`
  - this should yield files like agency.txt, trips.txt, routes.txt, etc.
- agency needs to provide a list of vehicle IDs to uniquely identify each service vehicle. For smaller agencies who don't have existing vehicle IDs, this could be the last three digits of the VIN
- agency needs to provide a list of friendly trip names for the drivers to choose from when starting the web app. The default way of naming trips is to combine the head sign for each trip with the departure time at the first stop, e.g. `Venice Beach @ 3:05 pm`. If the agency doesn't find this scheme acceptable, then they need to provide a list of trip names. There is a tool available [here](https://github.com/cal-itp/gtfu/blob/master/scripts/trip-list-generator.sh) that automatically generates a list of trip names in the default scheme.

Create service data for agency
------------------------------
- Go to [agencies.yml](https://github.com/cal-itp/data-infra/blob/main/airflow/data/agencies.yml), a Cal-ITP source-of-truth, in order to determine the agency-id for this agency.
- Create a new branch
- From `graas/server/agency-config/gtfs`, run the command `./setup-agency-templates.sh <id>`. This will create a directory for the agency at `graas/server/agency-config/gtfs/gtfs-aux/<agency-id>`, containing 3 files:
    - agency-params.json
    - trip-names.json
    - vehicle-ids.json
- Add the vehicle IDs provided by the agency to `vehicle-ids.json`. Assuming the agency has IDs `001`, `002` and `003` the the file should look like this:
```
[
    "001",
    "002",
    "003"
]
```
- Update the values in agency-params.json to accomodate your agency. Options for `triplist-generator-namefield`:
    - headsign
    - route_short_name
    - route_long_name
    - shape_id
- Create a new pull request containing updates to agency-params.json & vehicle-ids.json. Agency-params.json needs to be updated on the main branch in order to run the following command.
- Within the gtfu directory, run the command `java -cp build/libs/gtfu.jar gtfu.tools.TripListGenerator -a <agency-id> -v`. You can try different values for `triplist-generator-namefield` and `triplist-generator-use-direction` until the output looks good. Once the output is satisfactory, run the same command with the "-l" flag to update the trip-names.json file directly. The file will look something like this.
```
[
    {"route_name": "Valley West (Northbound) @ 1:59 pm", "trip_id": "t_1194609_b_26559_tn_0", "calendar": [0, 0, 0, 0, 0, 1, 0], "departure_pos": {"lat": 40.780441, "long": -124.188820}},
    {"route_name": "Valley West (Northbound) @ 4:12 pm", "trip_id": "t_1194610_b_26559_tn_0", "calendar": [0, 0, 0, 0, 0, 1, 0], "departure_pos": {"lat": 40.780441, "long": -124.188820}},
    {"route_name": "Valley West (Northbound) @ 5:07 pm", "trip_id": "t_1194611_b_26559_tn_0", "calendar": [0, 0, 0, 0, 0, 1, 0], "departure_pos": {"lat": 40.780441, "long": -124.188820}},
    {"route_name": "Willow Creek (Eastbound) @ 8:25 am", "trip_id": "t_1522946_b_30738_tn_0", "calendar": [1, 1, 1, 1, 1, 0, 0], "departure_pos": {"lat": 40.868565, "long": -124.084099}},
    {"route_name": "Willow Creek (Eastbound) @ 8:25 am", "trip_id": "t_1522952_b_30738_tn_0", "calendar": [0, 0, 0, 0, 0, 1, 0], "departure_pos": {"lat": 40.868565, "long": -124.084099}},
    {"route_name": "Willow Creek (Eastbound) @ 10:40 am", "trip_id": "t_1522950_b_30738_tn_0", "calendar": [0, 0, 0, 0, 0, 1, 0], "departure_pos": {"lat": 40.868565, "long": -124.084099}},
    {"route_name": "Willow Creek (Eastbound) @ 3:45 pm", "trip_id": "t_1522945_b_30738_tn_0", "calendar": [1, 1, 1, 1, 1, 0, 0], "departure_pos": {"lat": 40.874722, "long": -124.084763}},
    {"route_name": "Willow Creek (Eastbound) @ 5:40 pm", "trip_id": "t_1522951_b_30738_tn_0", "calendar": [0, 0, 0, 0, 0, 1, 0], "departure_pos": {"lat": 40.868565, "long": -124.084099}}
]

```
- Confirm that none of the calendar arrays are null. If they are, those routes will be hidden by default, and you should ask the agency to update the GTFS feed
- Create a PR on the GRaaS repo containing these three new files. Since the web app refers to Github as a source of truth, merging this PR updates production.
- Update the file `live-agencies.txt`, which lives on the GRaaS storage bucket, to include the agency-id
[TODO](https://github.com/cal-itp/graas/issues/86): Automatically perform this update

Create keys for agency
----------------------
From `graas/server/app-engine`, run `python keygen.py -a <agency-id>`. This uploads the public key to Google Cloud and generates a folder called `<agency-id>` with two files inside it:
- __id_ecdsa__: private key. This is __PRIVILEGED INFORMATION__ and needs to be kept confidential.
- __id_ecdsa.pub__: public key

- Move this new agency key folder to the graas-agency-keys repo (or elsewhere if you prefer). This is privleged information and shouldn't live on the open-source repo.

- Create a unique agency QR code, which encodes the private key, to be read by the GRaaS app. Generate the code by going to the `graas/server/qr-gen` directory and running `./run.sh ../../../graas-agency-keys/<agency-id>/id_ecdsa "<nickname for agency>"`. This will save a file called "qr.png" to the qr-gen folder - you'll want to paste this into the onboarding docs provided to the agency. __Since this QR code contains the private key, it should handled, shared and deleted carefully__.
- You can visit [this page](https://console.cloud.google.com/datastore/entities;kind=agency) to confirm the key was added to Google datastore

Device Setup
------------
- On agency device, visit the [web app](https://<PROJECT-ID>.wl.r.appspot.com/) and create "home screen shortcut" to this URL (e.g. for iOS see [here](https://www.macrumors.com/how-to/add-a-web-link-to-home-screen-iphone-ipad))
- Open web app via home screen. Grant camera access to the app and scan the agency QR code. This will save the private key to the device.
- Press `Start`, then select an arbitrary route and vehicle and press `Okay`. Press `Allow` for the Location Services dialog that appears, then press the `Stop` button in the app.

Device Maintenance
------------------
All devices used for GPS data collection need to be rebooted once a week.

General troubleshooting
------------------
- To check whether an agency is publishing data, run the command `gcloud app logs tail`
- If the agency isn't seeing seeing any route results from the dropdown, confirm that they are starting the app while physically near the first stop (1/4 mile) and within 30 minutes of the start time. If it still isnt working, edit the parameters in agency-params.json (note that if they don't have parameters listed, the app defaults to some - you can see in app-engine/templates/staging.html line 1094).
- If you need to get the logs for a given day, run the following command from venv within the app-engine directory: `python get-pos.py -a <agency-id> -d MM/DD/YY -t > <agency-name>-MM-DD-YY.log`