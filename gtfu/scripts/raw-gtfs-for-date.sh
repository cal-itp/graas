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

if [ $# -ne 1 ]
then
  echo "usage: $0 <mm/dd/yy>"
fi

DATE=$1
echo DATE: $DATE

YMD=`echo $DATE | awk -F/ '{print "20" $3 "-" $1 "-" $2}'`
echo YMD: $YMD

echo running DB extraction for $YMD, please stand by...
python3 tools/get-db-entries-by-date.py -d $DATE > /tmp/graas-report/$YMD.txt
java -cp build/libs/gtfu.jar gtfu.tools.GPSLogSlicer /tmp/graas-report/$YMD.txt

HEADER=`head -1 /tmp/graas-report/$YMD.txt`
echo HEADER: $HEADER

for i in `ls /tmp/graas-report/*-$YMD.txt`
do
  echo $i
  MATCH=`grep $HEADER $i`

  if [ -z "$MATCH" ]
  then
    echo $HEADER > $i.bak
    cat $i >> $i.bak
    mv $i.bak $i
  fi
done


ls -l /tmp/graas-report/*-$YMD.txt
