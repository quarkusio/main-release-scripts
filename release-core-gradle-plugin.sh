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

echo "Deploying Gradle plugin"

pushd devtools/gradle
./gradlew clean publishPlugins
popd

cd ../.. || exit 2
