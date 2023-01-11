#!/usr/bin/env kscript
@file:MavenRepository("jcenter","https://jcenter.bintray.com/")
@file:MavenRepository("maven-central","https://repo.maven.apache.org/maven2/")
@file:DependsOn("org.kohsuke:github-api:1.131")

import org.kohsuke.github.*
import java.io.File
import java.util.Date

fun GHIssue.line() : String {
    var title = this.getTitle();
    return "[${this.getNumber()}] ${title} - ${this.getHtmlUrl()}"
}

fun main(args: Array<String>) {
    val github: GitHub = GitHub.connect()
    val repository = getProject(github)

    val issues = repository.listIssues(GHIssueState.CLOSED).toList()
        // No milestone
        .filter { it -> it.getMilestone() == null}
        // Not marked as invalid or won't fix
        .filter { it -> isValid(it) }

    println("Orphans issues: ")    
    issues
        .filter { it -> ! it.isPullRequest()}
        .forEach { it -> println("  ${it.line()}")}

    val prs = issues
        .filter { it -> it.isPullRequest()}
        .map { it -> repository.getPullRequest(it.getNumber()) }
        .filter { it -> it.isMerged() }

   println("Orphans PRs: ")    
   prs
    .forEach { it -> println("  ${it.line()}")}

   println("Issues and PR names to fix:")
   repository.listIssues(GHIssueState.CLOSED).toList()
        // Not marked as invalid or won't fix
        .filter { it -> isValid(it) }
        .filter { it -> 
            it.getTitle().endsWith("...") || it.getTitle().endsWith("…") ||
            it.getTitle().startsWith("#")  || it.getTitle().startsWith("Fixes #") || it.getTitle().startsWith("Fix #") 
        }
        .forEach { it -> println("  ${it.line()}")}

}

fun getProject(github: GitHub) : GHRepository {
    return github.getRepository("quarkusio/quarkus")
}

fun isValid(issue: GHIssue) : Boolean {
    return ! issue.getLabels().stream().filter { it -> 
    it.getName() == "triage/wontfix"  || it.getName() == "triage/invalid"  || it.getName() == "kind/question" || it.getName() == "triage/out-of-date" || it.getName() == "triage/duplicate" || it.getName() == "area/infra" || it.getName() == "kind/epic" || it.getName() == "triage/moved" }
    .findAny().isPresent();
}

fun fail(message: String, code: Int = 2) {
    println(message)
    kotlin.system.exitProcess(code)
}

fun failWithPredicate(predicate: () -> Boolean, message: String, code: Int = 2) {
    val success = predicate.invoke()
    if (! success) {
        fail(message, code)
    }
}
