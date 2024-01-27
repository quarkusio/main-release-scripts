//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:3.6.0@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.kohsuke:github-api:1.315
//DEPS org.apache.maven:maven-artifact:3.9.6

//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//Q:CONFIG quarkus.log.level=SEVERE
//Q:CONFIG quarkus.banner.enabled=false

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.apache.maven.artifact.versioning.ComparableVersion;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "prerequisites", mixinStandardHelpOptions = true)
public class prerequisites implements Runnable {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]+$");
    private static final Pattern FINAL_VERSION_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");

    @Option(names = "--branch", description = "The branch to build the release on", required = true)
    String branch;

    @Option(names = "--qualifier", description = "The qualifier to add to the version. Example: CR1.", defaultValue = "")
    String qualifier;

    @Deprecated(forRemoval = true) // this is automatically resolved now
    @Option(names = "--micro", description = "Should we release a micro?", defaultValue = "false")
    boolean micro;

    @Option(names = "--major", description = "Should we release a major?", defaultValue = "false")
    boolean major;

    @Deprecated(forRemoval = true) // this is automatically resolved now
    @Option(names = "--maintenance", description = "Is it a maintenance branch?", defaultValue = "false")
    boolean maintenance;


    @Override
    public void run() {
        if (new File("work").exists()) {
            fail("Work directory exists, please execute cleanup.sh before releasing");
        }
        if (major && micro) {
            fail("Should be either micro or major, can't be both");
        }
        if (branch.isBlank()) {
            fail("Branch should be defined with --branch <branch>");
        }

        try {
            GitHub github;
            String releaseGitHubToken = System.getenv("RELEASE_GITHUB_TOKEN");
            if (releaseGitHubToken != null && !releaseGitHubToken.isBlank()) {
                github = new GitHubBuilder().withOAuthToken(releaseGitHubToken).build();
            } else {
                github = GitHubBuilder.fromPropertyFile().build();
            }
            GHRepository repository = getGithubProject(github);

            if (major) {
                System.out.println("Releasing a major release");
            }

            micro = micro || (!isMain(branch) && !major);
            boolean firstCrWithAutomatedRelease = releaseGitHubToken != null && isFirstCR(qualifier);

            if (!VERSION_PATTERN.matcher(branch).matches() && !isMain(branch)) {
                fail("Branch " + branch + " is not a valid version (X.y)");
            }
            if (!firstCrWithAutomatedRelease) {
                try {
                    repository.getBranch(branch);
                } catch (GHFileNotFoundException e) {
                    fail("Branch " + branch + " does not exist in the repository");
                }
            }
            System.out.println("Working on branch: " + branch);

            System.out.println("Listing tags of " + repository.getName());
            NavigableSet<ComparableVersion> tags = new TreeSet<>();
            tags.addAll(repository.listTags().toList().stream()
                    .map(t -> t.getName())
                    .map(n -> new ComparableVersion(n))
                    .collect(Collectors.toList()));

            tags = tags.descendingSet();

            if (tags.isEmpty()) {
                fail("No tags in repository " + repository.getName());
            }
            ComparableVersion tag = null;
            if (isMain(branch)) {
                // no branch, we take the last tag
                tag = tags.iterator().next();
            } else {
                // we defined a branch, we determine the last tag with the branch prefix
                for (ComparableVersion currentTag : tags) {
                    if (currentTag.toString().startsWith(branch + ".")) {
                        tag = currentTag;
                        break;
                    }
                }
            }

            String newVersion;
            if (tag != null) {
                System.out.println("Last tag is: " + tag);

                // Retrieve the associated release
                GHRelease release = repository.getReleaseByTagName(tag.toString());
                if (release == null) {
                    System.err.println("[WARNING] No release associated with tag " + tag);
                }

                // All good, compute new version.
                newVersion = computeNewVersion(tag.toString(), micro, major, qualifier);
            } else {
                if (!qualifier.isBlank()) {
                    newVersion = branch + ".0." + qualifier;
                } else {
                    newVersion = branch + ".0";
                }
            }

            // Check there are no tag with this version
            boolean tagAlreadyExists = tags.stream()
                    .anyMatch(t -> newVersion.equals(t.toString()));

            if (tagAlreadyExists) {
                fail("There is a tag with name " + newVersion + ", invalid increment");
            }

            // Check there is a milestone with the right name
            if (!firstCrWithAutomatedRelease) {
                checkIfMilestoneExists(repository, newVersion);
            }

            System.out.println("Listing releases of " + repository.getName());
            NavigableSet<ComparableVersion> releases = new TreeSet<>();
            releases.addAll(repository.listReleases().toList().stream()
                    .map(t -> t.getName())
                    .map(n -> new ComparableVersion(n))
                    .collect(Collectors.toList()));

            releases = releases.descendingSet();

            // Completion
            new File("work/").mkdirs();

            System.out.println("Writing " + newVersion + " into the 'work/newVersion' file");
            Files.writeString(Path.of("work", "newVersion"), newVersion, StandardCharsets.UTF_8);

            System.out.println("Writing " + branch + " into the 'work/branch' file");
            Files.writeString(Path.of("work", "branch"), branch, StandardCharsets.UTF_8);

            if (micro) {
                System.out.println("Releasing a micro release");
                new File("work/micro").createNewFile();
            }

            if (maintenance || isMaintenance(branch, releases)) {
                System.out.println("Releasing a maintenance release");
                new File("work/maintenance").createNewFile();
            }

            if (!newVersion.endsWith(".Final") && !FINAL_VERSION_PATTERN.matcher(newVersion).matches()) {
                new File("work/preview").createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException was thrown, please see above");
        }
    }

    private static void checkIfMilestoneExists(GHRepository repository, String version) throws IOException {
        Optional<GHMilestone> milestoneOptional = repository.listMilestones(GHIssueState.OPEN).toList().stream()
                .filter(m -> version.equals(m.getTitle()))
                .findFirst();

        if (milestoneOptional.isEmpty()) {
            fail("No milestone found with version " + version);
        } else {
            GHMilestone milestone = milestoneOptional.get();
            System.out.println("Found milestone " + milestone.getTitle());
            if (milestone.getOpenIssues() != 0) {
                System.err.println("[WARNING] Milestone " + version + " found, but " + milestone.getOpenIssues() + " issue(s) is/are still opened, check " + milestone.getHtmlUrl());
            }
        }
    }

    private static GHRepository getGithubProject(GitHub github) throws IOException {
        return github.getRepository("quarkusio/quarkus");
    }

    private static void fail(String message) {
        System.err.println("[ERROR] " + message);
        System.exit(2);
    }

    private static String computeNewVersion(String previousVersion, boolean micro, boolean major, String qualifier) {
        String[] segments = previousVersion.split("\\.");
        if (segments.length < 3) {
            fail("Invalid version " + previousVersion + ", number of segments must be at least 3, found: " + segments.length);
        }

        String newVersion;
        if (micro) {
            if (segments.length == 3) {
                // previous version was a final, we increment
                newVersion = segments[0] + "." + segments[1] + "." + (Integer.parseInt(segments[2]) + 1);
            } else {
                String previousQualifier = segments[3];
                if ("Final".equals(previousQualifier)) {
                    // previous version was a final, we increment
                    newVersion = segments[0] + "." + segments[1] + "." + (Integer.parseInt(segments[2]) + 1);
                    // previous version had a Final qualifier so we are releasing a micro of a version with Final qualifiers
                    qualifier = "Final";
                } else {
                    // previous version was a preview, we don't increment
                    newVersion = segments[0] + "." + segments[1] + "." + segments[2];
                }
            }
        } else if (major) {
            newVersion = (Integer.parseInt(segments[0]) + 1) + ".0.0";
        } else {
            newVersion = segments[0] + "." + (Integer.parseInt(segments[1]) + 1) + ".0";
        }
        if (!qualifier.isBlank()) {
            newVersion = newVersion + "." + qualifier;
        }

        return newVersion;
    }

    private static String getCurrentStableBranch(Set<ComparableVersion> tags) {
        for (ComparableVersion candidate : tags) {
            String[] segments = candidate.toString().split("\\.");
            if (segments.length == 3) {
                // this is the last stable version
                return segments[0] + "." + segments[1];
            }
        }

        fail("Unable to find the current stable branch");
        return null;
    }

    private static boolean isMaintenance(String branch, Set<ComparableVersion> tags) {
        if (isMain(branch)) {
            return false;
        }

        return new ComparableVersion(getCurrentStableBranch(tags)).compareTo(new ComparableVersion(branch)) > 0;
    }

    private static boolean isMain(String branch) {
        return "main".equals(branch);
    }

    private static boolean isFirstCR(String qualifier) {
        return "CR1".equalsIgnoreCase(qualifier);
    }
}
