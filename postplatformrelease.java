//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:3.2.2.Final@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.kohsuke:github-api:1.315

//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//Q:CONFIG quarkus.log.level=SEVERE
//Q:CONFIG quarkus.banner.enabled=false

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import picocli.CommandLine.Command;

@Command(name = "postplatformrelease", mixinStandardHelpOptions = true)
public class postplatformrelease implements Runnable {

    private static final String RELEASE_NOTEWORTHY_FEATURE_LABEL = "release/noteworthy-feature";
    private static final String RELEASE_BREAKING_CHANGE_LABEL = "release/breaking-change";

    // This doesn't need to be exact, just good enough
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("((?<!`)@[\\w\\.]+)", Pattern.CASE_INSENSITIVE);

    @Override
    public void run() {
        try {
            GitHub github;
            String releaseGitHubToken = System.getenv("RELEASE_GITHUB_TOKEN");
            if (releaseGitHubToken != null && !releaseGitHubToken.isBlank()) {
                github = new GitHubBuilder().withOAuthToken(releaseGitHubToken).build();
            } else {
                github = GitHubBuilder.fromPropertyFile().build();
            }
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

            if (isFirstFinal(version)) {
                final List<GHIssue> mergedIssues = new ArrayList<>();

                // we need to merge issues from all preview releases
                repository.listMilestones(GHIssueState.CLOSED).toList().stream()
                    .filter(m -> m.getTitle().startsWith(getMinorVersion(version) + ".0"))
                    .forEach(m -> {
                        try {
                            mergedIssues.addAll(repository.getIssues(GHIssueState.CLOSED, m));
                        } catch (IOException e) {
                            System.err.println("Ignored issues for milestone " + m.getTitle());
                            e.printStackTrace();
                        }
                    });
                createAnnounce(version, mergedIssues);
            } else {
                createAnnounce(version, issues);
            }
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
            .prerelease(Files.exists(Path.of("work/preview")))
            .create();
        System.out.println("Release " + version + " created - " + release.getHtmlUrl());
    }

    private static String issueTitle(GHIssue issue) {
        return "[#" + issue.getNumber() + "] " + issue.getTitle();
    }

    private static String issueTitleInMarkdown(GHIssue issue) {
        return "[#" + issue.getNumber() + "](" + issue.getHtmlUrl() + ") - " + issue.getTitle();
    }

    private static String issueTitleInMarkdownEscaped(GHIssue issue) {
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
                descriptionSb.append("  * ").append(issueTitleInMarkdownEscaped(majorChange)).append("\n");
            }
        }

        descriptionSb.append("\n### Complete changelog\n\n");
        for (GHIssue issue : issues) {
            descriptionSb.append("  * ").append(issueTitleInMarkdownEscaped(issue)).append("\n");
        }

        String description = descriptionSb.toString();

        Files.writeString(Path.of("work", "release"), description, StandardCharsets.UTF_8);
        return description;
    }

    private static void createAnnounce(String version, List<GHIssue> issues) throws IOException {
        List<GHIssue> majorChanges = issues.stream()
                .filter(i -> i.getLabels().stream().anyMatch(l -> RELEASE_NOTEWORTHY_FEATURE_LABEL.equals(l.getName())))
                .sorted((i1, i2) -> i1.getTitle().compareToIgnoreCase(i2.getTitle()))
                .collect(Collectors.toList());
        List<GHIssue> breakingChanges = issues.stream()
                .filter(i -> i.getLabels().stream().anyMatch(l -> RELEASE_BREAKING_CHANGE_LABEL.equals(l.getName())))
                .filter(i -> !majorChanges.stream().anyMatch(o -> o.getNumber() == i.getNumber()))
                .sorted((i1, i2) -> i1.getTitle().compareToIgnoreCase(i2.getTitle()))
                .collect(Collectors.toList());

        String announce = "";

        if (!majorChanges.isEmpty()) {
            announce += "### Newsworthy changes (in Markdown)\n\n";
            announce += "```\n";
            for (GHIssue majorChange : majorChanges) {
                announce += "* " + issueTitleInMarkdown(majorChange) + "\n";
            }
            announce += "```\n\n";
        }

        if (!breakingChanges.isEmpty()) {
            announce += "### Other breaking changes (FYI, in Markdown)\n\n";
            announce += "```\n";
            for (GHIssue breakingChange : breakingChanges) {
                announce += "* " + issueTitleInMarkdown(breakingChange) + "\n";
            }
            announce += "```\n\n";
        }

        if (isFirstFinal(version)) {
            announce += "It might also be a good idea to have a look at the [migration guide for this version](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-" + getMinorVersion(version) + ").\n\n";
        }

        announce += "### Announcement email template\n\n";

        announce += "Subject: `[RELEASE] Quarkus " + version + "`\n\n";
        announce += "```\n";
        announce += "Hello,\n" +
            "\n" +
            "Quarkus " + version + " has been released, and is now available from the Maven Central repository. The quickstarts and documentation have also been updated.\n" +
            "\n" +
            "More information in the announcement blog post: https://quarkus.io/blog/quarkus-" + version.replace('.', '-').toLowerCase(Locale.ROOT) + "-released/.\n" +
            "\n";
        if (!majorChanges.isEmpty()) {
            announce += "* Major changes:\n" +
                    "\n" +
                    majorChanges.stream().map(mc -> "  * " + issueTitle(mc)).collect(Collectors.joining("\n")) +
                    "\n\n";
        }
        announce += "* BOM dependency:\n" +
            "\n" +
            "  <dependency>\n" +
            "      <groupId>io.quarkus.platform</groupId>\n" +
            "      <artifactId>quarkus-bom</artifactId>\n" +
            "      <version>" + version + "</version>\n" +
            "      <type>pom</type>\n" +
            "      <scope>import</scope>\n" +
            "  </dependency>\n" +
            "\n";

        if (isFirstFinal(version)) {
            announce += "* Changelogs are available from https://github.com/quarkusio/quarkus/releases/tag/" + version + ".CR1 and https://github.com/quarkusio/quarkus/releases/tag/" + version + "\n";
            announce += "* Download is available from https://github.com/quarkusio/quarkus/releases/tag/" + version + "\n";
        } else {
            announce += "* Changelog and download are available from https://github.com/quarkusio/quarkus/releases/tag/" + version + "\n";
        }

        announce += "* Documentation: https://quarkus.io\n";
        announce += "\n";
        announce += "The Quarkus dev team\n";
        announce += "```\n\n";

        Files.writeString(Path.of("announce-" + version + ".txt"), announce, StandardCharsets.UTF_8);
    }

    private static boolean isFirstFinal(String version) {
        return version.endsWith(".0") || version.endsWith(".0.Final");
    }

    private static String getVersion() throws IOException {
        return Files.readString(Path.of("work", "newVersion"), StandardCharsets.UTF_8).trim();
    }

    private static String getMinorVersion(String version) {
        String[] elements = version.split("\\.");

        if (elements.length < 2) {
            return version;
        }

        return elements[0] + "." + elements[1];
    }

    private static GHRepository getProject(GitHub github) throws IOException {
        return github.getRepository("quarkusio/quarkus");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }
}
