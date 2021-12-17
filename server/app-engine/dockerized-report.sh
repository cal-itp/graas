#! /bin/sh

if [ "$#" -ne 1 ]; then
  echo "Usage: dockerized-report.sh <agency-id>" >&2
  exit 1
fi

docker build -t gtfsrt-weekly-report .
docker run -e AGENCY=$1 gtfsrt-weekly-report
