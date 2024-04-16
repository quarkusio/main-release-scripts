//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:3.2.2.Final@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.kohsuke:github-api:1.315

//JAVA 21
//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//Q:CONFIG quarkus.log.level=SEVERE
//Q:CONFIG quarkus.banner.enabled=false

import java.io.IOException;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import picocli.CommandLine.Command;

@Command(name = "updateextensioncatalog", mixinStandardHelpOptions = true)
public class updateextensioncatalog implements Runnable {

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
            GHRepository repository = github.getRepository("quarkusio/quarkus-extension-catalog");
            GHWorkflow workflow = repository.getWorkflow("check_updates.yaml");
            workflow.dispatch("main");
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException was thrown, please see above");
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }
}
