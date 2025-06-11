#!/bin/bash
set -x -e

pushd work/quarkus

echo "Waiting for Quarkus Core artifacts to be synced"

if [ -z "${RELEASE_GITHUB_TOKEN}" ]; then
  export MAVEN_OPTS="-Dmaven.repo.local=$(realpath ../repository)"
fi

NJORD_BASEDIR=$(realpath ../njord)
NJORD_VERSION=$(xmlstarlet sel -t -v '/extensions/extension[groupId/text()="eu.maveniverse.maven.njord"]/version/text()' .mvn/extensions.xml)

env GITHUB_REPOSITORY="quarkusio/quarkus" ./mvnw njord:${NJORD_VERSION}:check-artifacts-availability \
 -Dnjord.basedir=${NJORD_BASEDIR} \
 -e -B -ntp \
 -s .github/release-settings.xml \
 -N \
 -Ddrop=false \
 -Dwait=true -DwaitDelay=PT30M -DwaitTimeout=PT120M -DwaitSleep=PT5M \
 -Dscan=false

popd
