if [%~1]==[] (
  echo "Usage: dockerized-report.sh <agency-id>"
  exit 1
)

docker build -t gtfsrt-weekly-report .
docker run -e AGENCY=%~1 gtfsrt-weekly-report
