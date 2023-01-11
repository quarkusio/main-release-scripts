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

@Command(name = "postcorerelease", mixinStandardHelpOptions = true)
public class postcorerelease implements Runnable {

    @Override
    public void run() {
        try {
            GitHub github = GitHubBuilder.fromPropertyFile().build();
            GHRepository repository = getProject(github);

            String version = getVersion();
            Optional<GHMilestone> milestoneOptional = repository.listMilestones(GHIssueState.OPEN).toList().stream()
                    .filter(m -> version.equals(m.getTitle()))
                    .findFirst();
            if (milestoneOptional.isEmpty()) {
                fail("Cannot find the milestone " + version);
            }
            
            closeOldMilestone(milestoneOptional.get(), version);
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException was thrown, please see above");
        }
    }

    private static void closeOldMilestone(GHMilestone milestone, String version) throws IOException {
        milestone.close();
        System.out.println("Milestone " + version + " closed - " + milestone.getHtmlUrl());
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
