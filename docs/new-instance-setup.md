GRaaS Instance Setup
====================
Follow these instructions to set up your own instance of GRaaS.

1. Clone or fork the GRaaS repo.
2. From `server/app-engine`, set up a [python virtual environment](https://docs.python.org/3/library/venv.html), and then run `pip install -r requirements.txt` to install dependencies.
3. Create a [new Google Cloud project](http://console.cloud.google.com) (you will need to login to a Google user account first).
4. Navigate to "Cloud Storage" and, if you haven't already, start a free trial. This is the one part of the setup where you'll need to put down a credit card - Google says you won't be charged without a warning.
5. Follow the quickstart instructions [here](http://cloud.google.com/sdk/docs/quickstart) to install the latest SDK version, and then follow steps 1-4 of under "Initializing the Cloud SDK".
6. Follow Google's instructions for *Creating a service account* and *Setting the environment variable* [here](http://cloud.google.com/docs/authentication/getting-started). Grant the service account the role of "Storage Admin."
7. Under IAM & Admin > IAM, grant your Google account email address and the service account with Storage Admin, Storage Object Admin, and Cloud Datastore Owner access.
8. Follow steps 1-3 under [Add Firebase to your Project](https://firebase.google.com/docs/web/setup?authuser=0). Enable Hosting.
9. Navigate to the [Firebase config object](http://firebase.google.com/docs/web/learn-more#config-object), and update `server/static/firebaseconfig.js` with the variables from your project.
10. Generate keys for HTTPS authentication, by running the following command:
`openssl req -x509 -newkey rsa:4096 -nodes -out cert.pem -keyout key.pem -days 365`. This will generate two files, cert.pem and key.pem.
11. You are ready to run the server locally: from your virtual environment, run  `python main.py -c <path-to-cert.pem> -k <path-to-key.pem>`. This should launch the local server - from a web browser, go to the URL listed (likely https://127.0.0.1:8080). If the webpage loads correctly, continue to the next step.
12. Follow instructions in the [Onboarding Runbook](server/onboarding-runbook.md) to create a new test agency. Note that you'll need to replace the directory path 'graas-resources' in `server/agency-config/gtfs/copy-to-bucket.sh`,`server/app-engine/static/graas.js`, `server/app-engine/util.py`.
13. Once you have a new agency created, load the local web app again and scan the QR you've just created (you'll need to pull it up on a separate device, or print it).
14. Deploy the app with `gcloud app deploy`.

