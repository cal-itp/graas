General Transit Feed Utilities
==============================
This repository provides a number of tools and utilities to monitor GTFS realtime feeds. The tools are detailed in the sections below. The Realtime Visualization component is very suitable for high-level validation of both static GTFS and Vehicle Position feeds for an operator. Finally, there is a utility to post Service Alerts.

Vehicle Position Feed
---------------------

A command line utility that continuously displays vehicle position data for an operator to a terminal window.

![Vehicle Positions](img/vehicle-positions.png)

For each vehicle, when available, we display the distance traveled since the last update, the heading (indicated by an arrow), speed and time since last time. Vehicle IDs are shown with a green, yellow or red background, depending on how stale their last update is.

To run: `scripts/monitor-position-updates.sh -url <vehicle-position-url>`

With Docker and Docker Compose: `docker-compose run positions_feed -url <vehicle-position-url>`

Service Alert Feed
------------------

A command line utility that dumps active alerts for an operator to a terminal window.

![Service Alerts](img/service-alerts.png)

To run: `scripts/dump-service-alerts.sh <service-alert-url>`

With Docker and Docker Compose: `docker-compose run service_alerts <service-alert-url>`

Service Alert UI
----------------

A desktop app to let a user post GTFS service alerts.

![Alert UI](img/alert-ui.png)

To run: `scripts/alert-ui.sh -url <path-to-config-file>`

Below is an example for a config file. Make sure to substitute the appropriate values for your scenario.

```json
{
    "private_key_file": "/Users/foo/id_rsa",
    "static_gtfs_url": "http://operator.com/gtfs.zip",
    "post_url": "https://operator.com/post-service-alert"
}
```

Realtime Operator Visualization
-------------------------------
Provides ongoing realtime visualization for a GTFS-rt compliant operator. Visuals are created by combining the static GTFS data with the realtime Vehicle Position Feed.

![Realtime Visualization](img/big-blue-bus.png)

Coordinates are auto-scaled to display all routes for an operator. Routes are drawn in the colors specified in the GTFS file. Stops are drawn in gray, vehicles are shown in magenta. Clicking on a stop shows the stop name, clicking on a vehicle shows the schedule adherence status (early, on time, late). In the upper left corner, we show the operator name, and vehicle, stop and trip count.

The first time data for an operator is shown, the system downloads and caches the static GTFS zip file. On subsequent runs, the modification time of the zip URL is checked against the cache timestamp. If the URL content is newer, we update the cache.

The vehicle position feed is sampled every few seconds, and any updates are reflected on the screen.

Pressing `n` or `p` switches to the next or previous operator feed, respectively. We show a loading overlay while switching operators. The overlay has two progress bars, data download up top and data parsing at the bottom.

To run: `scripts/realtime-visualization.sh <path-to-config-file>`

Below is an example for a config file. Make sure to substitute the appropriate values for your scenario.

```json
[
    {
        "agency_id": "agency-1",
        "static_gtfs_url": "http://agency-1.com/gtfs.zip",
        "vehicle_position_url": "http://agency-1.com/vehicle-positions.pb"
    },
    {
        "agency_id": "agency-2",
        "static_gtfs_url": "http://agency-2.com/gtfs.zip",
        "vehicle_position_url": "http://agency-2.com/vehicle-positions.pb"
    },
]
```

Trip List Utility
-----------------

A command line utility for generating a list of friendly trip names from a static GTFS feed. This is part of the onboarding process for new operators, see [here](https://github.com/cal-itp/graas-staging/blob/master/server/doc/onboarding-runbook.md) for more details.

To run: `scripts/dump-service-alerts.sh <tmp-dir> <agency-id> <static-gtfs-url>`

With Docker and Docker Compose: `docker-compose run trip_list_generator <tmp-dir> <agency-id> <static-gtfs-url>`

Use something like `/tmp/gtfu-cache` as `tmp-dir`.

GTFS Validator
-----------------
This library utilizes [MobilityData's gtfs-validator tool](https://github.com/mobilitydata/gtfs-validator). It runs as a part of the TripListGenerator tool, and you can also run it with the command: `java -cp build/libs/gtfu.jar gtfu.tools.GTFSValidator <agency-static-gtfs-url>`

To Build
--------
`gradle distZip`

