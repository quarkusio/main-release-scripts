#!/bin/bash
set -x -e

# logout from any OpenShift cluster as the Kubernetes tests will deploy things there
if command -v oc &> /dev/null; then
    oc logout || true
fi

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

if [ -f work/emergency-release-core-branch ]; then
  BRANCH=$(cat work/emergency-release-core-branch)
elif [ -f work/branch ]; then
  BRANCH=$(cat work/branch)
else
  BRANCH="HEAD"
fi

echo "Preparing release ${VERSION} on branch ${BRANCH}"

rm -rf work/quarkus

echo "Cloning quarkus"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://github.com/quarkusio/quarkus.git work/quarkus
else
  git clone git@github.com:quarkusio/quarkus.git work/quarkus
fi

cd work/quarkus
git checkout ${BRANCH}
HASH=$(git rev-parse --verify $BRANCH)
echo "Last commit is ${HASH} - creating detached branch"
git checkout -b "r${VERSION}" "${HASH}"
echo "Update version to ${VERSION}"

./update-version.sh ${VERSION}

if [ -z "${RELEASE_GITHUB_TOKEN}" ]; then
  export MAVEN_OPTS="-Dmaven.repo.local=$(realpath ../repository)"
fi

env GITHUB_REPOSITORY="quarkusio/quarkus" ./mvnw \
  -e -B -ntp \
  -s .github/release-settings.xml \
  clean install \
  -Dscan=false \
  -Dno-build-cache \
  -Dgradle.cache.local.enabled=false \
  -Dgradle.cache.remote.enabled=false \
  -Ddevelocity.cache.local.enabled=false \
  -Ddevelocity.cache.remote.enabled=false \
  -Prelease \
  -DskipTests -DskipITs \
  -Ddokka \
  -Dno-test-modules

echo "Enforcing releases"
env GITHUB_REPOSITORY="quarkusio/quarkus" ./mvnw -e -B -ntp -s .github/release-settings.xml -Dscan=false -Dgradle.cache.local.enabled=false -Dno-test-modules org.apache.maven.plugins:maven-enforcer-plugin:3.5.0:enforce -Drules=requireReleaseVersion,requireReleaseDeps

echo "Alright, commit"
git commit -am "[RELEASE] - Bump version to ${VERSION}"
git tag "$VERSION"
echo "Pushing tag to origin"
git push origin "$VERSION"

cd ../.. || exit 2
