after adding new datastore kinds, or to run new queries against the datastore it is often necessary to update index.yaml. Changes to that file need to be deployed with:
gcloud datastore indexes create index.yaml
