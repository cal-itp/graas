
GRaaS: GTFS Realtime as a Service
==================================
GRaaS is a program which creates GTFS-RT data using a variety of Android, iOS, and other internet capable devices. This lets you - and transit riders - know where your vehicle is in real time and plan your trip accordingly!

Background & Overview
---------------------

The [California Integrated Travel Project (Cal-ITP)](https://www.calitp.org/) launched in 2019 with specific goals to improve public transport across the state and beyond. One of the main goals of the project was to provide accurate and complete information for trip planning in real time in a way that gave transit agencies ownership over their own data: an end to black boxes. Cal-ITP looked to create an open-source software which created open data in an open standard. Thus, GRaaS was born.

GRaaS adds to the growing number of [Mobility ‘as a Service’](https://en.wikipedia.org/wiki/Mobility_as_a_service) projects in the transit sphere. The software creates real-time data in the [General Transit Feed Standard](https://gtfs.org/) (GTFS) format: vehicle positions and service alerts. (Note: At this time, we are not providing trip updates). We’re working to make transit data open and accessible for all.

As the industry standard, GTFS-rt feeds can be read by most [journey planning apps](https://en.wikipedia.org/wiki/Journey_planner) like Google Maps, Apple Maps, TransitApp, etc. Before GRaaS, transit agencies could either pay specialized companies or simply not provide real-time information. We created GRaaS to change that.

The software runs on variety of Android, iOS, and other internet capable devices and generates a set of URLs that journey planning apps accept. It’s easy for agencies and easy for riders, who see the up-to-date info right on their smartphones.

Components:

- Flask/Python server deployed to Google App engine
- A library of Java tools
- A web app for transit vehicles to post updates
- On-board internet capable device

Pre-requisites

- A dedicated internet-capable smartphone tablets on each transit vehicle
- The technical ability to deploy a GCP flask app

Setup & deployment
--------------------

To set up and begin running your own instance of GRaaS, follow [the GRaaS setup instructions](new-instance-setup.md).

To see if you have everything set up correctly. Try deploying the web app, as outlined in the [server README](server/README.md).

See the [Agency Onboarding Runbook](server/onboarding-runbook.md) for instructions on adding a specific agency to your GRaaS instance.

For instructions on how to get a transit agency set up with the GRaaS mobile app, see the [Agency Onboarding Instructions](https://docs.google.com/document/d/1wlE91hMZ4HbYk1TcTEhbaRaz_T_7rdsDazqphVUZ2l0/).


Recommended Hardware:

The team has also developed a hardware version of GRaaS that is completely dedicated to transmitting GTFS data. This was developed as a solution to various issues that we found during our demonstrations using different tablets and smartphones such as screen timeouts, OS updates, and more.

You can still use GRaaS on other hardware such as tablets and smartphones. However, the following operational guidelines should be followed:

- Use an internet enabled device, not a WIFI only device
- Ensure that the device is connected to data, either via a SIM card or WIFI connection
- Do not lock the device as it disrupts location data
- Check that all software is up-to-date before running the app to eliminate any pop-ups which disrupt location services

The team has tested the following tablets and phones in the field and are confident in their ability to run the software:

1. iPad
1. Samsung Galaxy Tab

Support
-------

Transit Agency or Operator in California:

If you would like to use this software as a part of the on-going California Integrated Travel Project (Cal-ITP), please contact <support@calitp.org>. Our team is available to help as a complementary service offered through Caltrans. If this message is displayed, the opportunity is still available for CA transit agencies and operators.

Transit Agency or Operator outside CA:

If you tried the software and would like to learn more about how Caltrans and the California Integrated Travel Project (Cal-ITP) are supporting open-source data solutions, please contact <hello@calitp.org>. If you would like to see a similar partnership in your state, encourage your state DOT to engage with Cal-ITP staff.


If you would like to ask a question, create an issue labeled as a question in github. If you would like to report an issue, submit a bug-report issue in github.

Development Roadmap
--------------------

Here are some areas that we working on:

- Removing driver interaction
- Automating trip reports
- Adding full trip updates feature (i.e., arrival predictions in-house, rather than through journey planning app)
- Setting up alerts feature with ADA integrations

If you would like to make an edit or request a new feature, send a pull request / submit an issue in github. Alternatively, you can email <support@calitp.org>.

Contributing
------------

We are still working on contribution guidelines as this is a new project. We expect to accept suggestions and edits, which adhere to a code of conduct, but are still refining the process.