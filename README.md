# Quarkus Releases

## Prerequisites

In your home directory, create a `.github` file containing your OAuth token:

```
oauth=TOKEN
```

This token must have write permission on the Quarkus project.

Also, install [Kotlin](https://kotlinlang.org/) and [kscript](https://github.com/holgerbrandl/kscript).
Installation instructions are in the README.

You also need a GPG key. How to set it depends on your environment but here are a few hints for current Fedora:
```
gpg2 --full-generate-key
```

Then edit your `~/.m2/settings.xml` to define the following profile:
```
<profile>
  <id>release</id>
  <properties>
    <gpg.executable>/usr/bin/gpg2</gpg.executable>
  </properties>
</profile>
```

Your GPG key needs to be pushed to a public server:
```
# get the id of your key
gpg2 --list-keys
# push it
gpg2 --keyserver hkp://pool.sks-keyservers.net --send-keys YOUR_KEY_ID
```

Better do that the day before your first release (or at least wait for some time before trying to release, otherwise the deployment might fail).

You also need the authorization to push the artifacts to the `io.quarkus` groupId.

Once you have that authorization, add a `<server>` entry to your :
```
<server>
  <id>ossrh</id>
  <username>YOUR_SONATYPE_USERNAME</username>
  <password>YOUR_SONATYPE_PASSWORD</password>
</server>
```

To release the Gradle plugin, you need to follow these instructions: https://plugins.gradle.org/docs/submit . Be sure to ask George Gastaldi to add you to the owners of the plugin.

To announce the release on Twitter, you also need to be able to tweet from the QuarkusIO account.

For versions < 2.0, you need a JDK 8 installed as the release must be done with JDK 8.

For versions >= 2.0, you need a JDK 11 installed as the release must be done with JDK 11.

To execute the Java scripts, you need [JBang](https://www.jbang.dev/) installed.

To update the quickstarts:

* you need to have push access to the `quarkus-quickstarts` project
* you need to be part of the [quickstarts-guardians](https://github.com/orgs/quarkusio/teams/quickstarts-guardians/members)

### Setting up JReleaser

Starting with Quarkus 2.7, we are using JReleaser to publish the Quarkus CLI.

To get JReleaser working, you need to create create a `~/.jreleaser/config.properties` file with the following content:

```
JRELEASER_GITHUB_TOKEN=<a GitHub personal token with the permission to push commits>

JRELEASER_SDKMAN_CONSUMER_KEY=<your SDKMAN key>
JRELEASER_SDKMAN_CONSUMER_TOKEN=<your SDKMAN token>

JRELEASER_HOMEBREW_GITHUB_USERNAME=<your GitHub username>
JRELEASER_HOMEBREW_GITHUB_TOKEN=<the same GitHub token as above>

JRELEASER_CHOCOLATEY_GITHUB_USERNAME=<your GitHub username>
JRELEASER_CHOCOLATEY_GITHUB_TOKEN=<the same GitHub token as above>
```

## Branching

As soon as you release CR1, you need a version branch (e.g. X.Y). Everything will be pushed there.

## Backporting

One part of the release job is to do the backports.
As long as you're past the CR1 (and sometimes even for CR1), you will need to backport to the branch.

For that, we have the Backports application: https://github.com/quarkusio/quarkus-backports .

 * Clone the repository
 * Put your GitHub token in a `.env` file: `BACKPORTS_TOKEN=<your-token>`
 * Launch `./quarkus.sh` if you want to backport with the `triage/backport?` label or `./quarkus-1.7.sh` if you want to target the 1.7 label

Then you just go through the list of PRs to backport:

 * Update to latest upstream: `git remote update upstream`
 * Create a `X.Y.Z-backports-n` branch based on the `X.Y` branch
 * Cherry-pick button to copy/paste the cherry-pick command
 * Paste that in a terminal and execute it
 * Mark the PR as Done in the application
 * Next PR

Once done, push the branch to your fork and create a backport PR.
**Be extra careful to target the correct version branch when creating the PR.**
Mark the PR with the `area/infra` label, assign it to you and add a message mentioning you will merge it yourself.

## GitHub housekeeping

> Now that the bot is taking care of setting the versions, we don't really do that anymore.

<details>

Look for orphans:

 * [Pull requests](https://github.com/quarkusio/quarkus/pulls?utf8=✓&q=is%3Apr+is%3Aclosed+sort%3Aupdated-desc+-label%3Atriage%2Fduplicate+-label%3Atriage%2Finvalid+no%3Amilestone+-label%3Atriage%2Fwontfix+-label%3Atriage%2Fout-of-date+-label%3Aarea%2Finfra+-label%3Akind%2Fepic+)
 * [Issues](https://github.com/quarkusio/quarkus/issues?utf8=✓&q=is%3Aissue+is%3Aclosed+sort%3Aupdated-desc+-label%3Atriage%2Fduplicate+-label%3Atriage%2Finvalid+no%3Amilestone+-label%3Atriage%2Fwontfix+-label%3Atriage%2Fout-of-date+-label%3Akind%2Fquestion+-label%3Aarea%2Finfra+-label%3Akind%2Fepic+-label%3Atriage%2Fmoved+)

Check the issues in the [milestone](https://github.com/quarkusio/quarkus/milestones):

 * look for potential `noteworthy-feature`
 * clean up potential ellipses in issue titles

You also need to check that there are no remaining issues in the milestone.
</details>

## Pre-release

When starting the release of a new branch (typically when doing the CR1) create a version branch locally (e.g. 2.15) from the last commit you want to include and push the branch upstream.

Then you are ready to configure the release. And you have several options depending on which type of releases you want to push.

### Releasing a CR1

```
./prerequisites.java --branch=2.15 --qualifier=CR1
```
This will release a 2.15.0.CR1 release (if you haven't released any 2.15 yet).

### Releasing a Final release on top of a CR

```
./prerequisites.java --micro --branch=2.15 --qualifier=Final
```
(`--micro` is just there for the version number generation, it's a micro on top of the CR1)

### Releasing a maintenance micro (typically for RHBQ once we have a new community version that is the latest)

:warning: This is only for maintenance branches, once we have a new community version that is the latest. This should be used for additional LTS/RHBQ releases, for instance. See next paragraph for a standard micro release.

For a micro bugfix release for the 2.13 branch:
```
./prerequisites.java --micro --branch=2.13 --maintenance
```
Note the `--maintenance` option.

### Releasing a micro for the latest release

For a standard micro bugfix release for the current branch:
```
./prerequisites.java --micro --branch=2.15
```

Check there are no warnings and the generated version is the one that you expect.

**Something very important: you have to keep your release directory as is until the very end of the release, so for a `.0.Final` spread over several weeks, be sure you don't clean it up to release a new micro of the previous branch. My advice: create one `quarkus-release` directory per long term version (e.g. `quarkus-release` for the usual releases, `quarkus-release-1.7` for 1.7, `quarkus-release-1.3` for 1.3).**

### Releasing an alpha of 3.0

To release a new alpha of the 3.0 branch:
```
./prerequisites.java --micro --qualifier=AlphaX
```

Replace `X` by the number of the alpha.

Check there are no warnings and the generated version is the one that you expect.

**Something very important: you have to keep your release directory as is until the very end of the release, so for a `.0.Final` spread over several weeks, be sure you don't clean it up to release a new micro of the previous branch. My advice: create one `quarkus-release` directory per long term version (e.g. `quarkus-release` for the usual releases, `quarkus-release-1.7` for 1.7, `quarkus-release-1.3` for 1.3).**

## Release

1. **Be sure you use either JDK 8 for versions < 2.0 or JDK 11 for versions >= 2.0 to release**
2. Run `./release-prepare.sh` or on Fedora / Ubuntu `./release-prepare.sh ; paplay /usr/share/sounds/freedesktop/stereo/complete.oga`
3. Wait... a very long time....
4. Run `./release-perform.sh` or on Fedora / Ubuntu `./release-perform.sh ; paplay /usr/share/sounds/freedesktop/stereo/complete.oga`
5. At the moment, we have disabled the automatic release of the repository so you need to go to https://s01.oss.sonatype.org/#stagingRepositories and do it yourself (just click the `Release` button).
6. Wait... until all artifacts are available on the Maven repository. Wait for the version to appear on https://repo1.maven.org/maven2/io/quarkus/quarkus-core/ then wait for another 5 minutes to let the other artifacts sync.
7. Run `./release-gradle-plugin.sh` (if it fails with missing dependencies, just wait five more minutes and run it again)

Good luck...

### Troubleshooting Sonatype issues

Since we moved to the new s01 server, things are going smoothly so hiding this.

<details>

Keeping this part just in case but since we moved to the s01 server, things are far more stable and reliable so you shouldn't need that section.

Status page for the Sonatype services: https://status.maven.org/ .

You can have all sort of issues at step 4:

* An error in the first pass going through all the artifacts: it contacts Sonatype once for every artifacts. If it fails at this stage, just rerun the `./release-perform.sh` command.
* An error while creating the staging repository or uploading the artifacts at the end. Same here, if it fails, just rerun the command.
* You can also have a timeout when closing the repository at the end. This is not fatal. Closing the repository is a background task and it continues on Sonatype Nexus. In this case, you might want to go to [the Sonatype Nexus](https://s01.oss.sonatype.org/#stagingRepositories) to find out more about the error, especially the activity tab of the staging repository. Either it's still ongoing and you just have to wait or there is a rule error and you have to figure out what's going on. If there is a rule error, you will probably have to discard the staging repository and rerun the `./release-perform.sh` command.

</details>

## Close milestone

Run:
```
./postcorerelease.java
```

## Trigger performance lab

After the release, we need to trigger the performance benchmark to analyse regressions. This is made using the following script (**you need to be connected to the Red Hat VPN**):
```
./trigger-performance-testing.sh
```

## PAUSE

**In the case of a `.0.Final` release** when you need to release the core artifacts first and the Platform a week after, stop there for the first step and go back to it when releasing the Platform.

For other releases (either CRs or micros), you can continue.

## Release the Platform

1. Check if any PR needs merging: https://github.com/quarkusio/quarkus-platform/pulls
2. Use the proper branch!
3. Make sure you have the latest of the branch locally
4. Update the Quarkus version here: https://github.com/quarkusio/quarkus-platform/blob/main/pom.xml#L49
5. Then follow the release instructions: https://github.com/quarkusio/quarkus-platform#release-steps

## Release the Extension Catalog

**Before going through this step, make sure the Platform artifacts are properly available on Maven Central by checking https://repo1.maven.org/maven2/io/quarkus/quarkus-universe-bom/ and waiting 5 more minutes to make sure everything is synced.**

You need to publish the extension catalog for the new version by running the GitHub Action workflow here: https://github.com/quarkusio/quarkus-extension-catalog/actions/workflows/check_updates.yaml.

In the `Run Check Updates script` section of the log, just after the jBang initialization, you should see something like:

```
[jbang] or visit https://jbang.dev to download and install it yourself.
2021-09-07T16:42:49.861783Z[GMT] [INFO] Processing platform ./platforms/quarkus-bom.yaml
2021-09-07T16:42:49.911919Z[GMT] [INFO] ---------------------------------------------------------------
2021-09-07T16:42:49.993025Z[GMT] [INFO] Fetching latest version for io.quarkus.platform:quarkus-bom-quarkus-platform-descriptor
2021-09-07T16:42:50.924617Z[GMT] [INFO] New versions Found: [X.Y.Z.Final]
```

## GitHub actions

Run:

```
./postplatformrelease.java
```

This script:

1. Creates the release on GitHub
2. Generates an `announce-X.Y.Z.Final.txt` file containing the announce text, to be sent to the mailing list.

## PAUSE

**In the case of a `.CRx` release**, stop there.

For other releases, you can continue.

## Update the Quarkus JBang Catalog

Run:

```
./update-jbang-catalog.sh
```

It won't do anything for maintenance releases (i.e. releases started with `--maintenance`).

## Publish the CLI

**Only for latest stable community release**

**Do not execute it when releasing old branches**

```
./publish-cli.sh
```

## Update the quickstarts

```
./update-quickstarts.sh
```

## Update the documentation

```
./update-docs.sh
```

### Update code.quarkus.io

code.quarkus.io is now automatically updating the Platform from the registry every 5 minutes.

You can check the new version has been properly consumed by going to https://code.quarkus.io and hovering `X.Y / io.quarkus.platform`.

In case, things went south for whatever reasons (better check with Andy first), we can still force an update using these instructions:

<details>

**Only for latest stable community release**

 * Update **both** the Core and Platform versions in code.quarkus.io `pom.xml`
 * Follow the instructions there: https://github.com/quarkusio/code.quarkus.io
 * Check that the new version has been deployed to the production instance:
   * https://code.quarkus.io/api/config
   * https://ci.ext.devshift.net/job/openshift-saas-deploy-saas-quarkus-quarkus-production/ (if the job failed, just restart it)

If you have any issue with the code.quarkus.io release infrastructure:

 * https://app.slack.com/client/T027F3GAJ/CCRND57FW/
 * Channel #sd-app-sre and @app-sre-ic

Push to production deployment build: https://ci.ext.devshift.net/view/code-quarkus/job/openshift-saas-deploy-saas-quarkus-quarkus-production/

Archives:

 * https://github.com/quarkusio/code.quarkus.io-release

</details>

### Announcement

In a fresh/updated clone of https://github.com/quarkusio/quarkusio.github.io:

1. Write a blog post (use the old as a template, template is different if it's a major release or just a micro)
2. Update the `_data/versions.yaml` file of the website (the blog post must be pusblished first)
3. Push your changes

Wait for the website to be published, then:

1. Use `announce-X.Y.Z.Final.txt` to create an email and send it to `quarkus-dev`. Major items are sorted randomly by the script so it's always better to do a manual pass to reorder them. **Don't forget to include the announcement URL**.
2. Tweet about the release from the QuarkusIO account (use Tweetdeck for that and choose the right identity). **Don't forget to include the announcement URL**.

If upgrading GraalVM is required (i.e. not just recommended), make it prominent.

To get the list of contributors for a given version, you can use:

```
git fetch upstream --tags
git shortlog -s '3.0.0.Final'..'3.1.0.Final' | cut -d$'\t' -f 2 | grep -v dependabot | sort -d -f -i | paste -sd ',' - | sed 's/,/, /g'
```

I usually include the list only for a major release, getting everyone from X.Y.0.Final to X.Y+1.0.Final in the list.
