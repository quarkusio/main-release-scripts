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

if [ -f work/branch ]; then
  BRANCH=$(cat work/branch)
else
  BRANCH="main"
fi

if [ -f work/maintenance ]; then
  MAINTENANCE="true"
else
  MAINTENANCE="false"
fi

if [ -f work/preview ]; then
  PREVIEW="true"
else
  PREVIEW="false"
fi

./work/quarkus/devtools/cli/distribution/release-cli.sh $VERSION $BRANCH $MAINTENANCE $PREVIEW

