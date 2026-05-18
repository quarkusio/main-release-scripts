#!/bin/bash
set -x -e

if [ -f work/branch ]; then
  BRANCH=$(cat work/branch)
else
  echo "Could not find file work/branch"
  exit 1
fi

if [ ! -f work/lts ]; then
  echo "Not an LTS release, skipping guarded branches update"
  exit 0
fi

WORKING_DIRECTORY=work/quarkus-guarded-branches

echo "Cleaning up ${WORKING_DIRECTORY} directory if exists"
rm -rf ${WORKING_DIRECTORY}

echo "Cloning quarkus main branch"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone --branch main --single-branch --depth 1 https://github.com/quarkusio/quarkus.git ${WORKING_DIRECTORY}
else
  git clone --branch main --single-branch --depth 1 git@github.com:quarkusio/quarkus.git ${WORKING_DIRECTORY}
fi

pushd ${WORKING_DIRECTORY}

yq -i '.triage.guardedBranches = [{"ref": "'"${BRANCH}"'", "notify": .triage.guardedBranches[0].notify}] + .triage.guardedBranches' .github/quarkus-github-bot.yml

if [[ -z $(git status --porcelain) ]]; then
  echo "No changes were made. Exiting."
  popd
  rm -rf ${WORKING_DIRECTORY}
  exit 0
fi

echo "Alright, let's commit!"
git commit -am "Add ${BRANCH} LTS to guarded branches"
echo "Pushing changes"
git push origin main

popd

echo "Cleaning up ${WORKING_DIRECTORY} directory at the end"
rm -rf ${WORKING_DIRECTORY}
