#! /bin/sh

if [ $# -ne 1 ]
then
  echo "usage: $0 <gtfs-rt-position-feed-url>"
  exit 1
fi

URL=$1
echo URL: $URL

java -cp ../../gtfu/build/libs/gtfu.jar gtfu.MonitorPositionUpdates -raw -csvoutput -url $URL | egrep "vehicleid,timestamp|pr-test-vehicle-id-" > vehicles.csv
COUNT=`wc -l vehicles.csv | awk '{print $1}'`
echo COUNT: $COUNT

# 7 distinct vehicle IDs in post-position-update.js + 1 CSV header => 8 lines
if [ $COUNT == "8" ]
then
  echo success
  exit 0
else
  echo "* fail: got $COUNT results"
  exit 1
fi
