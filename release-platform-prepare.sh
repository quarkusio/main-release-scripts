#!/bin/bash
set -x -e

if [ $# -eq 0 ]; then
  echo "Platform preparation branch is required"
  exit 1
fi
PLATFORM_PREP_BRANCH=$1
# When the origin branch is not main, we receive the origin branch as the second argument
# so that we can create the platform preparation branch from it
ORIGIN_BRANCH=$2

VERSION=$(cat work/newVersion)

echo "Preparing Platform PR for Quarkus ${VERSION} on branch ${PLATFORM_PREP_BRANCH}"

rm -rf work/quarkus-platform-prepare

echo "Cloning quarkus-platform"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://github.com/quarkusio/quarkus-platform.git work/quarkus-platform-prepare
else
  git clone git@github.com:quarkusio/quarkus-platform.git work/quarkus-platform-prepare
fi

pushd work/quarkus-platform-prepare

if [ -n "$ORIGIN_BRANCH" ]; then
    # Non-main origin: create the platform preparation branch from the origin branch
    git checkout ${ORIGIN_BRANCH}
    git pull origin ${ORIGIN_BRANCH}
    git checkout -b ${PLATFORM_PREP_BRANCH}
    git push origin ${PLATFORM_PREP_BRANCH}
else
    # Main origin: just checkout the existing preparation branch
    git checkout ${PLATFORM_PREP_BRANCH}
    git pull origin ${PLATFORM_PREP_BRANCH}
fi

# Create the PR branch and update the Quarkus version
git checkout -b quarkus-${VERSION}
./update-quarkus-version.sh ${VERSION}
git add .
git commit -m "Upgrade to Quarkus ${VERSION}"
git push origin quarkus-${VERSION}

popd
