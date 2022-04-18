#! /bin/sh

if [ $# -ne 1 ]
then
  echo "usage: $0 <gtfs-rt-position-feed-url>"
  exit 1
fi

URL=$1
echo URL: $URL

java -cp ../../gtfu/build/libs/gtfu.jar gtfu.MonitorPositionUpdates -raw -url $URL | grep "^id: " | sort > count.txt
COUNT=`wc -l count.txt | awk '{print $1}'`
echo COUNT: $COUNT

if [ $COUNT == "7" ]
then
  echo success
  exit 0
else
  echo fail: got $COUNT results
  exit 1
fi
