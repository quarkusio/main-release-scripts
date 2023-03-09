//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:2.3.1.Final@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.kohsuke:github-api:1.300

//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//Q:CONFIG quarkus.log.level=SEVERE
//Q:CONFIG quarkus.banner.enabled=false

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "postplatformrelease", mixinStandardHelpOptions = true)
public class postplatformrelease implements Runnable {

    private static final String RELEASE_NOTEWORTHY_FEATURE_LABEL = "release/noteworthy-feature";

    // This doesn't need to be exact, just good enough
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("((?<!`)@[\\w\\.]+)", Pattern.CASE_INSENSITIVE);

    @Override
    public void run() {
        try {
            GitHub github = GitHubBuilder.fromPropertyFile().build();
            GHRepository repository = getProject(github);

            String version = getVersion();
            Optional<GHMilestone> milestoneOptional = repository.listMilestones(GHIssueState.CLOSED).toList().stream()
                    .filter(m -> version.equals(m.getTitle()))
                    .findFirst();
            if (milestoneOptional.isEmpty()) {
                fail("Cannot find the CLOSED milestone " + version + ". Either the milestone does not exist or it is not closed.");
            }
            
            GHMilestone milestone = milestoneOptional.get();

            List<GHIssue> issues = repository.getIssues(GHIssueState.CLOSED, milestone);

            createOrUpdateRelease(repository, issues, version);
            createAnnounce(version, getNextVersion(version), issues);
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException was thrown, please see above");
        }
    }

    private static void createOrUpdateRelease(GHRepository repository, List<GHIssue> issues, String version) throws IOException {
        GHRelease release = repository.getReleaseByTagName(version);

        if (release != null) {
            release.update().body(createReleaseDescription(issues)).update();
            System.out.println("Release " + version + " updated - " + release.getHtmlUrl());
            return;
        }

        release = repository.createRelease(version)
            .name(version)
            .body(createReleaseDescription(issues))
            .prerelease(!version.endsWith("Final"))
            .create();
        System.out.println("Release " + version + " created - " + release.getHtmlUrl());
    }

    private static String getNextVersion(String version) {
        String[] segments = version.split("\\.");
        if (segments.length < 3) {
            fail("Invalid version " + version + ", number of segments must be at least 3, found: " + segments.length);
        }
        String newVersion = segments[0] + "." + (Integer.valueOf(segments[1]) + 1) + ".0";

        return newVersion;
    }

    private static String issueTitle(GHIssue issue) {
        return "[" + issue.getNumber() + "] " + issue.getTitle();
    }

    private static String issueTitleInMarkdown(GHIssue issue) {
        return "[#" + issue.getNumber() + "](" + issue.getHtmlUrl() + ") - " +
                ANNOTATION_PATTERN.matcher(issue.getTitle()).replaceAll("`$1`");
    }

    private static String createReleaseDescription(List<GHIssue> issues) throws IOException {
        StringBuilder descriptionSb = new StringBuilder();

        List<GHIssue> majorChanges = issues.stream()
                .filter(i -> i.getLabels().stream().anyMatch(l -> RELEASE_NOTEWORTHY_FEATURE_LABEL.equals(l.getName())))
                .collect(Collectors.toList());
        if (!majorChanges.isEmpty()) {
            descriptionSb.append("### Major changes\n\n");
            for (GHIssue majorChange : majorChanges) {
                descriptionSb.append("  * ").append(issueTitleInMarkdown(majorChange)).append("\n");
            }
        }

        descriptionSb.append("\n### Complete changelog\n\n");
        for (GHIssue issue : issues) {
            descriptionSb.append("  * ").append(issueTitleInMarkdown(issue)).append("\n");
        }

        String description = descriptionSb.toString();
    
        Files.writeString(Path.of("work", "release"), description, StandardCharsets.UTF_8);
        return description;
    }

    private static void createAnnounce(String version, String newVersion, List<GHIssue> issues) throws IOException {
        List<GHIssue> majorChanges = issues.stream()
                .filter(i -> i.getLabels().stream().anyMatch(l -> RELEASE_NOTEWORTHY_FEATURE_LABEL.equals(l.getName())))
                .collect(Collectors.toList());

        String announce = "[RELEASE] Quarkus " + version + "\n" +
            "\n" +
            "Hello,\n" +
            "\n" +
            "Quarkus " + version + " has been released, and is now available from the Maven Central repository. The quickstarts and documentation have also been updated.\n" +
            "\n" +
            "More information in the announcement blog post: ***TODO URL***.\n" +
            "\n";
        if (!majorChanges.isEmpty()) {
            announce = announce + "* Major changes:\n" +
                    "\n" +
                    majorChanges.stream().map(mc -> "  * " + issueTitle(mc)).collect(Collectors.joining("\n")) +
                    "\n\n";
        }
        announce = announce + "* BOM dependency:\n" +
            "\n" +
            "  <dependency>\n" +
            "      <groupId>io.quarkus.platform</groupId>\n" +
            "      <artifactId>quarkus-bom</artifactId>\n" +
            "      <version>" + version + "</version>\n" +
            "      <type>pom</type>\n" +
            "      <scope>import</scope>\n" +
            "  </dependency>\n" +
            "\n" +
            "* Changelog and Download are available from https://github.com/quarkusio/quarkus/releases/tag/" + version + "\n" +
            "* Documentation: https://quarkus.io\n" +
            "\n" +
            "Next release should be cut in roughly four weeks. It will be " + newVersion + ".\n" +
            "Again, if you really want to see a feature or something in this release, don’t forget to mention it in your PR or issue.\n" +
            "\n" +
            "The Quarkus dev team";

        Files.writeString(Path.of("announce-" + version + ".txt"), announce, StandardCharsets.UTF_8);
    }

    private static String getVersion() throws IOException {
        return Files.readString(Path.of("work", "newVersion"), StandardCharsets.UTF_8).trim();
    }

    private static GHRepository getProject(GitHub github) throws IOException {
        return github.getRepository("quarkusio/quarkus");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }
}
