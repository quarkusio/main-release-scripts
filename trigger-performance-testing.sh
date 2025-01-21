#!/bin/bash
set -x -e

export VERSION=""
if [ -f work/newVersion ]; then
  VERSION=$(cat work/newVersion)
else 
  if [ $# -eq 0 ]
    then
      echo "Release version required, or 'work/newVersion' file"
      exit 1
  fi
  VERSION=$1
fi 

curl -k "https://ci.app-svc-perf.corp.redhat.com/job/Utility%20Scripts/job/quarkus-regression-pipeline/buildWithParameters?token=quarkus-release&VERSION=${VERSION}"

echo "Done. Visit https://ci.app-svc-perf.corp.redhat.com/job/Products/job/Quarkus/job/regression/job/quarkus-startup to verify that a new build has been created"
