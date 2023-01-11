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

curl -k "http://mwperf-server01.perf.lab.eng.rdu2.redhat.com:8080/job/Products/job/Quarkus/job/regression/job/quarkus-regression-pipeline/buildWithParameters?token=quarkus-release&VERSION=${VERSION}"
