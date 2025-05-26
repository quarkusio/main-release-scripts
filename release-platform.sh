#!/bin/bash
set -x -e

if [ $# -eq 0 ]; then
  echo "Platform branch is required"
  exit 1
fi
PLATFORM_BRANCH=$1

VERSION=$(cat work/newVersion)

echo "Preparing Platform release ${VERSION} on branch ${PLATFORM_BRANCH}"

rm -rf work/quarkus-platform

echo "Cloning quarkus-platform"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://github.com/quarkusio/quarkus-platform.git work/quarkus-platform
else
  git clone git@github.com:quarkusio/quarkus-platform.git work/quarkus-platform
fi

pushd work/quarkus-platform
git checkout ${PLATFORM_BRANCH}
git pull origin ${PLATFORM_BRANCH}

./check-version.sh $VERSION

git tag -d $VERSION || true
git push --delete origin $VERSION || true

env GITHUB_REPOSITORY="quarkusio/quarkus-platform" ./mvnw -s .github/release-settings.xml release:prepare release:perform -DdevelopmentVersion=999-SNAPSHOT -DreleaseVersion=$VERSION -Dtag=$VERSION -DperformRelease -Prelease,releaseCi -DskipTests -Darguments="-DskipTests -Dnjord.autoPublish"

popd

rm -rf work/quarkus-platform
