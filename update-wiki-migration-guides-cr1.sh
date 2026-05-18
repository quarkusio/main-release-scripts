#!/bin/bash
set -x -e

if [ -f work/branch ]; then
  BRANCH=$(cat work/branch)
else
  echo "Could not find file work/branch"
  exit 1
fi

MAJOR=$(echo "${BRANCH}" | cut -d. -f1)
MINOR=$(echo "${BRANCH}" | cut -d. -f2)
NEXT_MINOR=$((MINOR + 1))
NEXT_BRANCH="${MAJOR}.${NEXT_MINOR}"

if [ -f work/lts ]; then
  LTS_MARKER=" **LTS**"
else
  LTS_MARKER=""
fi

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
# - Change current first line: [main] -> [BRANCH], "will be BRANCH" -> "soon to be released"
# - Insert new first line for NEXT_BRANCH
sed -i "s|\(Migration-Guide-${BRANCH}\)\[main\].*|\1[${BRANCH}]${LTS_MARKER} - soon to be released|" Migration-Guides.asciidoc
sed -i "1i\\ * https://github.com/quarkusio/quarkus/wiki/Migration-Guide-${NEXT_BRANCH}[main] - will be ${NEXT_BRANCH}" Migration-Guides.asciidoc

# Create new migration guide for the next version
cat > "Migration-Guide-${NEXT_BRANCH}.asciidoc" << 'EOF'
:toc:

[NOTE]
====
We highly recommend the use of https://quarkus.io/guides/update-quarkus[`quarkus update`] to update to a new version of Quarkus.

Items marked below with :gear: :white_check_mark: are automatically handled by https://quarkus.io/guides/update-quarkus[`quarkus update`].
====
EOF

# Update _Sidebar.md: update "Next version in main" link
sed -i "/<!-- NEXT_VERSION -->/{n;s|.*|- [${NEXT_BRANCH}](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-${NEXT_BRANCH})|}" _Sidebar.md

echo "Alright, let's commit!"
git add -A
git commit -am "Update migration guides for ${BRANCH} CR1"
echo "Pushing changes"
git push origin master

popd

echo "Cleaning up ${WORKING_DIRECTORY} directory at the end"
rm -rf ${WORKING_DIRECTORY}
