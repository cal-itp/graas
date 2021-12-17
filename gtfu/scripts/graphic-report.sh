# requirements:
# - python3 with google-cloud-datastore package installed
# - relatively recent java version
# - gradle, curl, base64 cmd line tools
#! /bin/sh

PATH=/Library/Java/JavaVirtualMachines/jdk-10.jdk/Contents/Home/bin:/Library/Frameworks/Python.framework/Versions/3.7/bin:/usr/bin:/bin

if [ -z "$GOOGLE_APPLICATION_CREDENTIALS" ]
then
  echo "error: no DB access credentials set"
  exit 0
fi

if [ ! -d /tmp/graas-report ]
then
  mkdir /tmp/graas-report
fi

YMD=`date "+%Y-%m-%d"`
echo running DB extraction, please stand by...
python3 tools/get-db-entries-by-date.py --today > /tmp/graas-report/$YMD.txt
java -cp build/libs/gtfu.jar gtfu.tools.GPSLogSlicer /tmp/graas-report/$YMD.txt

for i in `ls /tmp/graas-report/*-$YMD.txt`
do
  #echo i: $i
  FILE_NAME=`basename $i`
  #echo FILE_NAME: $FILE_NAME
  AGENCY_ID=`echo $FILE_NAME | sed "s/-$YMD.txt//"`
  echo AGENCY_ID: $AGENCY_ID
  java -cp build/libs/gtfu.jar gtfu.GraphicReport ~/tmp/tuff $AGENCY_ID ~/tmp/gtfs-visualization.json $i
done

EMAIL_TO="{\"email\": \"kay.neuenhofen@dot.ca.gov\"}, {\"email\": \"mlaurengilbert@gmail.com\"}"
echo EMAIL_TO: $EMAIL_TO

CURL_DATA="'{\"personalizations\": [{\"to\": [$EMAIL_TO]}],\"from\": {\"email\": \"kay.neuenhofen@dot.ca.gov\"},\"subject\": \"GRaaS Report\",\"content\": [{\"type\": \"text/plain\", \"value\": \"Attached\"}],\"attachments\": ["

n=0
for i in `ls /tmp/graas-report/*.png`
do
  echo i: $i
  echo n: $n
  if [ $n -gt 0 ]
  then
    CURL_DATA+=","
  fi
  base64 -i $i -o base64.tmp
  BASE_64=`cat base64.tmp`
  CURL_DATA+="{\"type\": \"image/png\", \"filename\": \"report-$n.png\", \"content\": \"$BASE_64\"}"
  rm base64.tmp
  n=`expr $n + 1`
done

CURL_DATA+="]}'"
#echo CURL_DATA: $CURL_DATA

HEADERS=" --header 'Authorization: Bearer "$SENDGRID_API_KEY"' --header 'Content-Type: application/json'"
CMD="curl --request POST --url https://api.sendgrid.com/v3/mail/send $HEADERS --data $CURL_DATA"
#echo CMD: $CMD
# yes, eval is bad
eval $CMD

if [ ! -d ~/doc/graas-report-archive ]
then
  mkdir ~/doc/graas-report-archive
fi

mv /tmp/graas-report/*.png ~/doc/graas-report-archive
# rm -f /tmp/graas-report/*
