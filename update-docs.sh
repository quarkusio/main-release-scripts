#!/bin/bash

if [ $# -eq 0 ]; then
  if [ -f work/maintenance ]; then
    BRANCH=$(cat work/branch)
  else
    BRANCH="main"
  fi
else
  BRANCH=$1
fi

if [ -f work/preview ]; then
  echo "We do not update the docs for preview releases"
  exit 1
fi

pushd work/quarkus/docs
QUARKUS_WEB_SITE_PUSH=true QUARKUS_RELEASE=true ./sync-web-site.sh ${BRANCH}
popd
