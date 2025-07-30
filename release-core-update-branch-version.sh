#!/bin/bash
set -x -e

if [ -f work/branch ]; then
  BRANCH=$(cat work/branch)
else
  echo "Could not find file work/branch"
  exit 1
fi

WORKING_DIRECTORY=work/quarkus-branch
VERSION="${BRANCH}.999-SNAPSHOT"

rm -rf ${WORKING_DIRECTORY}

echo "Cloning quarkus"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone --branch ${BRANCH} --single-branch --depth 1 https://github.com/quarkusio/quarkus.git ${WORKING_DIRECTORY}
else
  git clone --branch ${BRANCH} --single-branch --depth 1 git@github.com:quarkusio/quarkus.git ${WORKING_DIRECTORY}
fi

pushd ${WORKING_DIRECTORY}
./update-version.sh ${VERSION}

if [[ -z $(git status --porcelain) ]]; then
  echo "No changes were made. Exiting."
  exit 0
fi

echo "Alright, commit"
git commit -am "[RELEASE] Set branch version to ${VERSION}"
echo "Pushing branch to origin"
git push origin "${BRANCH}"

popd
rm -rf ${WORKING_DIRECTORY}