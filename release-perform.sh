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

./mvnw clean deploy \
 -e -B \
 -Dscan=false \
 -Dgradle.cache.local.enabled=false \
 -DskipTests -DskipITs \
 -Dno-native=true \
 -DperformRelease=true \
 -Prelease \
 -Ddokka \
 -Dno-test-modules

# -Ddocumentation-pdf

cd ../.. || exit 2
