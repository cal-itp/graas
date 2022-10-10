GRaaS Quickstart for Evaluation purposes
========================================

**NOTE: This option is for evaluation purposes only and not suitable for production**

1. Create a venv using `server/app-engine/requirements-cloudless.txt`
1. Activate venv
1. Run `server/app-engine/cloudless-setup.sh`. This will generate an ad hoc agency key pair. The public key is added to the cloudless DB, and a QR code is generated as `server/app-engine/ad-hoc.png`.
1. Run `python server/app-engine/main.py -c <path-to-cert.pem> -k <path-to-key.pem>` _.pem files constitute a self-signed https cert. See below on how to generate cert_
1. (optional) Put the server online using a tool like [ngrok](https://www.ngrok.com)
1. Authenticate web app by pointing browser to server URL and scanning QR code

GRaaS Instance Setup
====================
Follow these instructions to set up your own instance of GRaaS.

1. Clone or fork the GRaaS repo.
1. From `server/app-engine`, set up a [python virtual environment](https://docs.python.org/3/library/venv.html), and then run `pip install -r requirements.txt` to install dependencies.
1. Create a [new Google Cloud project](http://console.cloud.google.com) (you will need to login to a Google user account first).
1. Navigate to "Cloud Storage" and, if you haven't already, start a free trial. This is the one part of the setup where you'll need to put down a credit card - Google says you won't be charged without a warning.
1. Follow the quickstart instructions [here](http://cloud.google.com/sdk/docs/quickstart) to install the latest SDK version, and then follow steps 1-4 of under "Initializing the Cloud SDK".
    - Verify installation by running `gcloud --version` from the command line
    - Verify that billing is enabled by running `gcloud beta billing projects describe YOUR_PROJECT_NAME` from the command line. Observe response like:

        <code>
        billingAccountName: billingAccounts/XXXXXX-XXXXXX-XXXXXX<br/>
        billingEnabled: true<br/>
        name: projects/YOUR_PROJECT_NAME/billingInfo<br/>
        projectId: YOUR_PROJECT_NAME
        </code>

1. Follow Google's instructions for *Creating a service account* and *Setting the environment variable* [here](http://cloud.google.com/docs/authentication/getting-started). Grant the service account the role of "Storage Admin."
1. Under IAM & Admin > IAM, grant your Google account email address and the service account with Storage Admin, Storage Object Admin, and Cloud Datastore Owner access.
1. Follow steps 1-3 under [Add Firebase to your Project](https://firebase.google.com/docs/web/setup?authuser=0). Enable Hosting.
1. Navigate to the [Firebase config object](http://firebase.google.com/docs/web/learn-more#config-object), and update `server/static/firebaseconfig.js` with the variables from your project.
1. Generate keys for HTTPS authentication, by running the following command:
`openssl req -x509 -newkey rsa:4096 -nodes -out cert.pem -keyout key.pem -days 365`. This will generate two files, cert.pem and key.pem.
1. You are ready to run the server locally: from your virtual environment, run  `python main.py -c <path-to-cert.pem> -k <path-to-key.pem>`. This should launch the local server - from a web browser, go to the URL listed (likely https://127.0.0.1:8080). If the webpage loads correctly, continue to the next step.
1. Follow instructions in the [Onboarding Runbook](server/onboarding-runbook.md) to create a new test agency. Note that you'll need to replace the directory path 'graas-resources' in `server/agency-config/gtfs/copy-to-bucket.sh`,`server/app-engine/static/graas.js`, `server/app-engine/util.py`.
1. Once you have a new agency created, load the local web app again and scan the QR you've just created (you'll need to pull it up on a separate device, or print it).
1. Deploy the app with `gcloud app deploy`.

