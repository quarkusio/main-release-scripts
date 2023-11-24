//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:3.2.2.Final@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.kohsuke:github-api:1.315

//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//Q:CONFIG quarkus.log.level=SEVERE
//Q:CONFIG quarkus.banner.enabled=false

import java.io.IOException;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "togglemainprotection", mixinStandardHelpOptions = true)
public class togglemainprotection implements Runnable {

    @Option(names = "--disable", description = "Should we disable branch protection or disable it?")
    boolean disable;

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

            GHBranch branch = repository.getBranch("main");

            if (!disable) {
                if (branch.isProtected()) {
                    System.out.println("Branch " + branch.getName() + " already protected, doing nothing");
                } else {
                    GHTeam team = github.getOrganization("quarkusio").getTeamByName("quickstarts-guardians");
                    System.out.println("Number of guardians: " + team.getMembers().size());
                    System.out.println("Enabling protection on " + branch.getName());
                    branch.enableProtection().userPushAccess(team.getMembers()).enable();
                }
            } else {
                if (!branch.isProtected()) {
                    System.out.println("Branch " + branch.getName() + " already unprotected, doing nothing");
                } else {
                    System.out.println("Disabling protection on " + branch.getName());
                    branch.disableProtection();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            fail("IOException was thrown, please see above");
        }
    }

    private static GHRepository getProject(GitHub github) throws IOException {
        return github.getRepository("quarkusio/quarkus-quickstarts");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(2);
    }
}
