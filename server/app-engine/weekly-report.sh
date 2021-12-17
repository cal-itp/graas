#! /bin/sh

if [ "$#" -ne 1 ]; then
  echo "Usage: weekly-report.sh <agency-id>" >&2
  exit 1
fi

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
source $SCRIPTPATH/venv/bin/activate
echo "extracting raw GPS updates from DB, this takes a couple of minutes..."
python $SCRIPTPATH/get-pos.py -a $1 -i -t -c 7d > $SCRIPTPATH/log.txt || exit 1
echo "extraction complete, processing raw data..."
echo ""
python $SCRIPTPATH/group-gps-updates.py $SCRIPTPATH/log.txt || exit 1

