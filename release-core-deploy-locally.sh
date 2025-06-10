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

echo "Checking out tag ${VERSION}"
cd work/quarkus
git fetch origin
git checkout "${VERSION}"

echo "Deploying release"

if [ -z "${RELEASE_GITHUB_TOKEN}" ]; then
  export MAVEN_OPTS="-Dmaven.repo.local=$(realpath ../repository)"
fi

NJORD_BASEDIR=$(realpath ../njord)

env GITHUB_REPOSITORY="quarkusio/quarkus" ./mvnw clean deploy \
 -Dnjord.basedir=${NJORD_BASEDIR} \
 -e -B \
 -s .github/release-settings.xml \
 -Dscan=false \
 -Dno-build-cache \
 -Dgradle.cache.local.enabled=false \
 -Dgradle.cache.remote.enabled=false \
 -Ddevelocity.cache.local.enabled=false \
 -Ddevelocity.cache.remote.enabled=false \
 -DskipTests -DskipITs \
 -Dno-native=true \
 -DperformRelease=true \
 -Prelease \
 -Ddokka \
 -Dno-test-modules

cd ../.. || exit 2
