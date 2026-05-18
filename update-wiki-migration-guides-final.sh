#!/bin/bash
set -x -e

if [ -f work/branch ]; then
  BRANCH=$(cat work/branch)
else
  echo "Could not find file work/branch"
  exit 1
fi

if [ -f work/lts ]; then
  LTS_MARKER=" **LTS**"
  LTS_SIDEBAR_MARKER=" LTS"
else
  LTS_MARKER=""
  LTS_SIDEBAR_MARKER=""
fi

DAY=$(date +%-d)
case "${DAY}" in
  1|21|31) SUFFIX="st" ;;
  2|22)    SUFFIX="nd" ;;
  3|23)    SUFFIX="rd" ;;
  *)       SUFFIX="th" ;;
esac
RELEASE_DATE="$(date +%B) ${DAY}${SUFFIX} $(date +%Y)"

WORKING_DIRECTORY=work/quarkus-wiki

echo "Cleaning up ${WORKING_DIRECTORY} directory if exists"
rm -rf ${WORKING_DIRECTORY}

echo "Cloning quarkus wiki"
if [ -n "${RELEASE_GITHUB_TOKEN}" ]; then
  git clone https://github.com/quarkusio/quarkus.wiki.git ${WORKING_DIRECTORY}
else
  git clone git@github.com:quarkusio/quarkus.wiki.git ${WORKING_DIRECTORY}
fi

pushd ${WORKING_DIRECTORY}

# Update Migration-Guides.asciidoc:
# - Change "soon to be released" to "released on <date>" for the current version
sed -i "s|\(Migration-Guide-${BRANCH}\[${BRANCH}\]\)\(.*\) - .*|\1\2 - released on ${RELEASE_DATE}|" Migration-Guides.asciidoc

# Update _Sidebar.md: update "Current version" to this version
sed -i "/<!-- CURRENT_VERSION -->/{n;s|.*|- [${BRANCH}](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-${BRANCH})|}" _Sidebar.md

# If LTS, add to LTS versions in sidebar
if [ -n "${LTS_SIDEBAR_MARKER}" ]; then
  sed -i "/<!-- LTS_VERSION -->/a\\- [${BRANCH}${LTS_SIDEBAR_MARKER}](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-${BRANCH})" _Sidebar.md
fi

echo "Alright, let's commit!"
git add -A
git commit -am "Update migration guides for ${BRANCH} Final"
echo "Pushing changes"
git push origin master

popd

echo "Cleaning up ${WORKING_DIRECTORY} directory at the end"
rm -rf ${WORKING_DIRECTORY}
