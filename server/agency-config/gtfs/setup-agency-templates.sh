# Run this script to create a folder for agency with 3 template json files.
# More detail in onboarding runbook
#! /bin/sh

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <agency-id>" >&2
  exit 1
fi

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
AGENCY_ID=$1
echo "AGENCY_ID: $AGENCY_ID"
AGENCY_PATH="$SCRIPTPATH/gtfs-aux/$AGENCY_ID"
echo "AGENCY_PATH: $AGENCY_PATH"

if [ -d $AGENCY_PATH ]; then
  echo "folder for $AGENCY_ID already exists"
else
  mkdir $AGENCY_PATH
fi

cat <<EOF >"$AGENCY_PATH/filter-params.json"
{
"is-filter-by-day-of-week": true,
"max-mins-from-start": 720,
"max-feet-from-stop": 4239840
}
EOF

cat <<EOF >"$AGENCY_PATH/trip-names.json"
[

]
EOF

cat <<EOF >"$AGENCY_PATH/vehicle-ids.json"
[

]
EOF