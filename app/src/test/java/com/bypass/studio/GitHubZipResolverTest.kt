package com.bypass.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubZipResolverTest {
    @Test
    fun parsesCommonGithubUrlForms() {
        assertEquals(
            GitHubZipResolver.Repo("owner", "repo"),
            GitHubZipResolver.parseRepo("https://github.com/owner/repo.git")
        )
        assertEquals(
            GitHubZipResolver.Repo("owner", "repo"),
            GitHubZipResolver.parseRepo("git@github.com:owner/repo")
        )
        assertEquals(
            GitHubZipResolver.Repo("owner", "repo"),
            GitHubZipResolver.parseRepo("github.com/owner/repo/")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidUrl() {
        GitHubZipResolver.parseRepo("https://example.com/not/github")
    }

    @Test
    fun buildsDefaultBranchAndArchiveUrls() {
        val repo = GitHubZipResolver.Repo("adintent999-creator", "joybaji-apk")
        assertEquals(
            "https://api.github.com/repos/adintent999-creator/joybaji-apk",
            GitHubZipResolver.defaultBranchApiUrl(repo)
        )
        assertEquals(
            "https://github.com/adintent999-creator/joybaji-apk/archive/refs/heads/main.zip",
            GitHubZipResolver.archiveUrl(repo, "main")
        )
        assertEquals(
            "https://github.com/zyvo9/ZYVO/archive/refs/heads/feature/test.zip",
            GitHubZipResolver.archiveUrl(GitHubZipResolver.Repo("zyvo9", "ZYVO"), "feature/test")
        )
    }

    @Test
    fun parsesDefaultBranchJson() {
        assertEquals(
            "bypass-studio",
            GitHubZipResolver.parseDefaultBranch("{\"name\":\"x\",\"default_branch\":\"bypass-studio\"}")
        )
    }

    @Test
    fun sanitizesArchiveEntriesAndRejectsTraversal() {
        assertEquals("Main.kt", GitHubZipResolver.safeRelativeEntry("repo-main/Main.kt"))
        assertEquals("a/b.txt", GitHubZipResolver.safeRelativeEntry("repo-main/a/b.txt"))
        assertNull(GitHubZipResolver.safeRelativeEntry("repo-main/../../secret.txt"))
        assertNull(GitHubZipResolver.safeRelativeEntry("/abs_only"))
    }
}
