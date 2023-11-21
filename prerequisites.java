//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:3.2.2.Final@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.kohsuke:github-api:1.315
//DEPS io.smallrye.common:smallrye-common-version:2.2.0

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
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.smallrye.common.version.VersionScheme;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

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
            GitHub github = GitHubBuilder.fromPropertyFile().build();
            GHRepository repository = getGithubProject(github);

            if (major) {
                System.out.println("Releasing a major release");
            }

            micro = micro || (!isMain(branch) && !major);

            if (!VERSION_PATTERN.matcher(branch).matches() && !isMain(branch)) {
                fail("Branch " + branch + " is not a valid version (X.y)");
            }
            try {
                repository.getBranch(branch);
            } catch (GHFileNotFoundException e) {
                fail("Branch " + branch + " does not exist in the repository");
            }
            System.out.println("Working on branch: " + branch);

            // Retrieve the last tag
            System.out.println("Listing tags of " + repository.getName());
            SortedSet<GHTag> tags = new TreeSet<>(VersionComparator.INSTANCE);
            tags.addAll(repository.listTags().toList());

            if (tags.isEmpty()) {
                fail("No tags in repository " + repository.getName());
            }
            GHTag tag = null;
            if (isMain(branch)) {
                // no branch, we take the last tag
                tag = tags.iterator().next();
            } else {
                // we defined a branch, we determine the last tag with the branch prefix
                for (GHTag currentTag : tags) {
                    if (currentTag.getName().startsWith(branch + ".")) {
                        tag = currentTag;
                        break;
                    }
                }
            }

            String newVersion;
            if (tag != null) {
                System.out.println("Last tag is: " + tag.getName());

                // Retrieve the associated release
                GHRelease release = repository.getReleaseByTagName(tag.getName());
                if (release == null) {
                    System.err.println("[WARNING] No release associated with tag " + tag.getName());
                }

                // All good, compute new version.
                newVersion = computeNewVersion(tag.getName(), micro, major, qualifier);
            } else {
                if (!qualifier.isBlank()) {
                    newVersion = branch + ".0." + qualifier;
                } else {
                    newVersion = branch + ".0";
                }
            }

            // Check there are no tag with this version
            boolean tagAlreadyExists = tags.stream()
                    .anyMatch(t -> newVersion.equals(t.getName()));

            if (tagAlreadyExists) {
                fail("There is a tag with name " + newVersion + ", invalid increment");
            }

            // Check there is a milestone with the right name
            checkIfMilestoneExists(repository, newVersion);

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

            if (maintenance || isMaintenance(branch, tags)) {
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

    private static String getCurrentStableBranch(SortedSet<GHTag> tags) {
        for (GHTag candidate : tags) {
            String[] segments = candidate.getName().split("\\.");
            if (segments.length == 3) {
                // this is the last stable version
                return segments[0] + "." + segments[1];
            }
        }

        fail("Unable to find the current stable branch");
        return null;
    }

    private static boolean isMaintenance(String branch, SortedSet<GHTag> tags) {
        if (isMain(branch)) {
            return false;
        }

        return VersionScheme.MAVEN.compare(getCurrentStableBranch(tags), branch) > 0;
    }

    private static boolean isMain(String branch) {
        return "main".equals(branch);
    }

    private static class VersionComparator implements Comparator<GHTag> {

        private static final VersionComparator INSTANCE = new VersionComparator();

        @Override
        public int compare(GHTag o1, GHTag o2) {
            return -VersionScheme.MAVEN.compare(o1.getName(), o2.getName());
        }
    }
}
