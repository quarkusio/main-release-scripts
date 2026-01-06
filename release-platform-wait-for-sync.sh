#!/bin/bash
set -x -e

pushd work/quarkus-platform

echo "Waiting for Quarkus Platform artifacts to be synced"

if [ -z "${RELEASE_GITHUB_TOKEN}" ]; then
  export MAVEN_OPTS="-Dmaven.repo.local=$(realpath ../repository)"
fi

NJORD_BASEDIR=$(realpath ../njord)
NJORD_VERSION=$(xmlstarlet sel -t -v '/extensions/extension[groupId/text()="eu.maveniverse.maven.njord"]/version/text()' .mvn/extensions.xml)

env GITHUB_REPOSITORY="quarkusio/quarkus-platform" ./mvnw njord:${NJORD_VERSION}:check-artifacts-availability \
 -Dnjord.basedir=${NJORD_BASEDIR} \
 -Dnjord.wait=true -Dnjord.waitDelay=PT10M -Dnjord.waitTimeout=PT60M -Dnjord.waitSleep=PT5M \
 -e -B -ntp \
 -s .github/release-settings.xml \
 -N \
 -Dscan=false

popd
rm -rf work/quarkus-platform
