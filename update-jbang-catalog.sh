#!/bin/bash
set -x -e

export VERSION=""
if [ -f work/newVersion ]; then
  VERSION=$(cat work/newVersion)
else
  echo "'work/newVersion' file required"
  exit 1
fi

if [ -f work/maintenance ]; then
  echo "JBang catalog should not be updated for maintenance releases, exiting"
  exit 0
fi
if [ -f work/preview ]; then
  echo "JBang catalog should not be updated for preview releases, exiting"
  exit 0
fi

echo "Cloning Quarkus JBang catalog"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://${RELEASE_GITHUB_TOKEN}:@github.com/quarkusio/jbang-catalog.git work/jbang-catalog
else
  git clone git@github.com:quarkusio/jbang-catalog.git work/jbang-catalog
fi

cd work/jbang-catalog

sed -i -E "s@io/quarkus/quarkus-cli/[^/]+/quarkus-cli-[^/]+-runner.jar@io/quarkus/quarkus-cli/$VERSION/quarkus-cli-$VERSION-runner.jar@g" jbang-catalog.json

git add .
git commit -m "Update Quarkus CLI to $VERSION"
git push origin main

cd ../.. || exit 2
