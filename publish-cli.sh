#!/bin/bash
set -x -e

# GitHub releases are immutable once published so publishing the CLI is done in two steps:
# - release: creates a draft GitHub release with the CLI archives attached, before the release is published
# - publish: publishes the packages (SDKMAN, Homebrew, Chocolatey), after the release has been published
MODE="$1"
if [ "$MODE" != "release" ] && [ "$MODE" != "publish" ]; then
  echo "Mode required: release or publish"
  exit 1
fi

export VERSION=""
if [ -f work/newVersion ]; then
  VERSION=$(cat work/newVersion)
else 
  if [ $# -lt 2 ]
    then
      echo "Release version required, or 'work/newVersion' file"
      exit 1
  fi
  VERSION=$2
fi 

if [ -f work/branch ]; then
  BRANCH=$(cat work/branch)
else
  BRANCH="main"
fi

if [ -f work/maintenance ]; then
  MAINTENANCE="true"
else
  MAINTENANCE="false"
fi

if [ -f work/preview ]; then
  PREVIEW="true"
else
  PREVIEW="false"
fi

RELEASE_CLI=./work/quarkus/devtools/cli/distribution/release-cli.sh

# Branches that haven't been updated yet still use the single step process that doesn't support immutable releases:
# it would create a published GitHub release without the CLI archives that would become the latest release.
# We skip the CLI publication for these branches so that the rest of the release can proceed
# (the GitHub release is then created as a draft by the post-Platform-release operations).
if ! grep -q '^MODE="\$5"' "$RELEASE_CLI"; then
  echo "::warning::The release-cli.sh script of branch $BRANCH doesn't support immutable GitHub releases, skipping the CLI publication. Backport https://github.com/quarkusio/quarkus/pull/58803 to $BRANCH to publish the CLI."
  exit 0
fi

if [ "$MODE" == "release" ]; then
  # JReleaser doesn't see draft releases so it would create another draft release if we retry this step:
  # delete the draft releases that might have been created for this version by a previous attempt
  set +x
  if [ -n "${JRELEASER_GITHUB_TOKEN}" ]; then
    export GH_TOKEN="${JRELEASER_GITHUB_TOKEN}"
  fi
  set -x
  for RELEASE_ID in $(gh api "repos/quarkusio/quarkus/releases?per_page=20" --jq ".[] | select(.draft and .tag_name == \"${VERSION}\") | .id"); do
    echo "Deleting existing draft release ${RELEASE_ID} for ${VERSION}"
    gh api -X DELETE "repos/quarkusio/quarkus/releases/${RELEASE_ID}"
  done
fi

$RELEASE_CLI $VERSION $BRANCH $MAINTENANCE $PREVIEW $MODE

