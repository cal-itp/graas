FROM python:3
WORKDIR /usr/src/app
COPY get-pos.py get-pos.py
COPY group-gps-updates.py group-gps-updates.py
COPY weekly-report.sh weekly-report.sh
RUN mkdir keys
COPY keys/make-agency-keys.sh keys/make-agency-keys.sh
COPY docker-auth.json docker-auth.json
RUN chmod 777 /usr/src/app/weekly-report.sh
RUN chmod 777 /usr/src/app/keys/make-agency-keys.sh
RUN apt-get update && apt-get install -y vim
RUN pip install --upgrade google-cloud-datastore
ENV GOOGLE_APPLICATION_CREDENTIALS="/usr/src/app/docker-auth.json"

