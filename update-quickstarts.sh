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
  SOURCE_BRANCH=development
elif [ $# -eq 1 ]; then
  BRANCH=$1
  SOURCE_BRANCH=development
else
  BRANCH=$1
  SOURCE_BRANCH=$2
fi

if [ -f work/preview ]; then
  echo "We do not update the quickstarts for preview releases"
  exit 1
fi

echo "Cleaning up work/quickstarts directory if exists"
rm -rf work/quickstarts

echo "Cloning quickstart"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://github.com/quarkusio/quarkus-quickstarts.git work/quickstarts
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
git checkout $BRANCH 2>/dev/null || git checkout -b $BRANCH development

if [ $BRANCH == "main" ] && [ $SOURCE_BRANCH != "main" ]; then
  echo "Resetting main to ${SOURCE_BRANCH}"
  git reset --hard origin/${SOURCE_BRANCH}
fi

find . -name pom.xml | xargs sed -ri "s@<quarkus.version>(.*)</quarkus.version>@<quarkus.version>${VERSION}</quarkus.version>@g"
find . -name pom.xml | xargs sed -ri "s@<quarkus.platform.version>(.*)</quarkus.platform.version>@<quarkus.platform.version>${VERSION}</quarkus.platform.version>@g"
find . -name pom.xml | xargs sed -ri "s@<quarkus-plugin.version>(.*)</quarkus-plugin.version>@<quarkus-plugin.version>${VERSION}</quarkus-plugin.version>@g"
find . -name pom.xml | xargs sed -ri "s@<quarkus.platform.group-id>(.*)</quarkus.platform.group-id>@<quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>@g"

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
