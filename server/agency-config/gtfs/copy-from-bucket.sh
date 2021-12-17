#! /bin/sh

# For new instances of GRaaS, replace 'graas-resources' with a globally unique directory name
gsutil -m cp -r gs://graas-resources/gtfs-aux .

