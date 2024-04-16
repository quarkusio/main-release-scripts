#!/bin/bash
set -x -e

export VERSION=""
if [ -f work/newVersion ]; then
  VERSION=$(cat work/newVersion)
else
  echo "'work/newVersion' file required"
  exit 1
fi
if [ $# -eq 1 ]; then
  BRANCH=$1
else
  echo "We need a branch as first argument"
  exit 1
fi

echo "Cleaning up work/quickstarts directory if exists"
rm -rf work/quickstarts

echo "Cloning quickstart"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://github.com/quarkusio/quarkus-quickstarts.git work/quickstarts
else
  git clone git@github.com:quarkusio/quarkus-quickstarts.git work/quickstarts
fi

pushd work/quickstarts
git fetch --tags
git checkout -b release-temp-branch-$BRANCH $VERSION || (echo "Tag $VERSION does not exist, please initialize it with the proper content before running this script" && exit 1)
git push origin release-temp-branch-$BRANCH:$BRANCH
popd

echo "Cleaning up work/quickstarts directory at the end"
rm -rf work/quickstarts
