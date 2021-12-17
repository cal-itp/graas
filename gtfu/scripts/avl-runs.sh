#! /bin/sh

echo "switching to gcloud TC project..."
gcloud config set project transitclock-282522
echo "checking cluster logs..."
java -cp build/classes/java/main/ gtfu.AVLRuns -dayoffset $1
