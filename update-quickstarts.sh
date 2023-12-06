#!/bin/bash
set -x -e

export VERSION=""
if [ -f work/newVersion ]; then
  VERSION=$(cat work/newVersion)
else
  echo "'work/newVersion' file required"
  exit 1
fi
if [ $# -eq 0 ]; then
  if [ -f work/maintenance ]; then
    BRANCH=$(cat work/branch)
  else
    BRANCH="main"
  fi
else
  BRANCH=$1
fi

if [ -f work/preview ]; then
  echo "We do not update the quickstarts for preview releases"
  exit 1
fi

echo "Cleaning up work/quickstarts directory if exists"
rm -rf work/quickstarts

echo "Cloning quickstart"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://${RELEASE_GITHUB_TOKEN}:@github.com/quarkusio/quarkus-quickstarts.git work/quickstarts
else
  git clone git@github.com:quarkusio/quarkus-quickstarts.git work/quickstarts
fi

echo "Disabling protection"
if [[ $BRANCH == "main" ]]; then
  ./togglemainprotection.java --disable=true
fi

echo "Generating Quarkus template to fetch the updated Dockerfile resources"
TEMPLATE_FOLDER=$(mktemp -d)
TEMPLATE_NAME=REPLACE_WITH_QUICKSTART_NAME
pushd $TEMPLATE_FOLDER
# Using "io.quarkus:quarkus-maven-plugin" to work with either 999-SNAPSHOT and a released version
mvn -e -B io.quarkus:quarkus-maven-plugin:${VERSION}:create -DprojectGroupId=org.acme -DprojectArtifactId=$TEMPLATE_NAME
popd
DOCKERFILES=(Dockerfile.jvm Dockerfile.legacy-jar Dockerfile.native Dockerfile.native-micro)
# List of quickstarts that should not update the Dockerfile resources (list separated by comma)
EXCLUDE_DOCKERFILE_UPDATE_FOR_QUICKSTARTS="awt-graphics-rest-quickstart"

cd work/quickstarts
git checkout $BRANCH || (echo "Branch $BRANCH does not exist, please initialize it with the proper content before running this script" && exit 1)

if [[ $VERSION =~ .*\.0 ]]; then
  echo "Resetting main to development"
  git reset --hard origin/development
fi

./mvnw -e -B versions:set-property -Dproperty="quarkus.version" -DnewVersion="${VERSION}" -DgenerateBackupPoms=false
./mvnw -e -B versions:set-property -Dproperty="quarkus.platform.version" -DnewVersion="${VERSION}" -DgenerateBackupPoms=false
./mvnw -e -B versions:set-property -Dproperty="quarkus-plugin.version" -DnewVersion="${VERSION}" -DgenerateBackupPoms=false
./mvnw -e -B versions:set-property -Dproperty="quarkus.platform.group-id" -DnewVersion="io.quarkus.platform" -DgenerateBackupPoms=false

for quickstart in *-quickstart getting-started-*; do
  # Update `index.html` files:
  if [ -f ${quickstart}/src/main/resources/META-INF/resources/index.html ]; then
    # Update quickstarts that use the following template `<li>Quarkus Version: <code>xxx</code></li>`:
    sed -i -E "s@<li>Quarkus Version: <code>[^<]*</code></li>@<li>Quarkus Version: <code>${VERSION}</code></li>@g" ${quickstart}/src/main/resources/META-INF/resources/index.html
    # Update quickstarts that use the following template `<li>Quarkus Version: xxx</li>`:
    sed -i -E "s@<li>Quarkus Version: [^<]*</li>@<li>Quarkus Version: ${VERSION}</li>@g" ${quickstart}/src/main/resources/META-INF/resources/index.html
    # Stage the updated index.html:
    git add ${quickstart}/src/main/resources/META-INF/resources/index.html
  fi

  # Update Dockerfile files only for the quickstarts that are not in the excluded list:
  if [[ $EXCLUDE_DOCKERFILE_UPDATE_FOR_QUICKSTARTS != *"$quickstart"* ]]; then
    for dockerfile in ${DOCKERFILES[@]}; do
      if [ -f ${quickstart}/src/main/docker/$dockerfile ]; then
        cp -rf $TEMPLATE_FOLDER/$TEMPLATE_NAME/src/main/docker/$dockerfile ${quickstart}/src/main/docker/$dockerfile
        sed -i -E "s@$TEMPLATE_NAME@${quickstart##*/}@g" ${quickstart}/src/main/docker/$dockerfile
        # Stage the updated Dockerfile file:
        git add ${quickstart}/src/main/docker/$dockerfile
      fi
    done
  fi
done

./mvnw -e -B clean install -DskipTests -DskipITs

echo "Alright, let's commit!"
git commit -am "[RELEASE] - Bump version to ${VERSION}"
git tag "$VERSION"
echo "Pushing tag to origin"
git push origin "$VERSION"
echo "Pushing changes to ${BRANCH}"
if [[ $BRANCH == "main" ]]; then
  git push origin main --force
else
  git push origin $BRANCH
fi

echo "Deleting generated Quarkus template"
rm -rf $TEMPLATE_FOLDER

cd ../.. || exit 2
echo "Enabling protection"
if [[ $BRANCH == "main" ]]; then
  ./togglemainprotection.java --disable=false
fi

echo "Cleaning up work/quickstarts directory at the end"
rm -rf work/quickstarts
