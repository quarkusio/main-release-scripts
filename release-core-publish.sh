#!/bin/bash
set -x -e

pushd work/quarkus

echo "Publishing Quarkus Core release"

if [ -z "${RELEASE_GITHUB_TOKEN}" ]; then
  export MAVEN_OPTS="-Dmaven.repo.local=$(realpath ../repository)"
fi

NJORD_BASEDIR=$(realpath ../njord)
NJORD_VERSION=$(xmlstarlet sel -t -v '/extensions/extension[groupId/text()="eu.maveniverse.maven.njord"]/version/text()' .mvn/extensions.xml)

env GITHUB_REPOSITORY="quarkusio/quarkus" ./mvnw njord:${NJORD_VERSION}:publish \
 -Dnjord.basedir=${NJORD_BASEDIR} \
 -e -B \
 -s .github/release-settings.xml \
 -N \
 -Ddrop=false \
 -Dnjord.publishingType=AUTOMATIC \
 -Dscan=false

popd
