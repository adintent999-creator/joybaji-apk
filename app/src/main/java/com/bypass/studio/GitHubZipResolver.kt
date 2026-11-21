package com.bypass.studio

import java.net.URLEncoder

object GitHubZipResolver {
    data class Repo(val owner: String, val repo: String)

    fun parseRepo(input: String): Repo {
        val clean = input.trim().trimEnd('/')
        require(clean.isNotBlank()) { "Repository URL is empty" }
        val rest = clean
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("git@github.com:")
            .removePrefix("github.com/")
        val parts = rest.split('/').filter { it.isNotBlank() }
        require(parts.size >= 2) { "Invalid GitHub URL. Use https://github.com/owner/repo" }
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        require(owner.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid owner" }
        require(repo.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid repository name" }
        return Repo(owner, repo)
    }

    fun defaultBranchApiUrl(repo: Repo): String =
        "https://api.github.com/repos/${encode(repo.owner)}/${encode(repo.repo)}"

    fun parseDefaultBranch(json: String): String {
        val match = Regex("\"default_branch\"\\s*:\\s*\"([^\"]+)\"").find(json)
            ?: throw IllegalArgumentException("default_branch missing in GitHub response")
        val branch = match.groupValues[1]
        require(branch.isNotBlank()) { "default_branch is blank" }
        return branch
    }

    fun archiveUrl(repo: Repo, branch: String): String {
        val encodedBranch = branch.split('/').joinToString("/") { encode(it) }
        return "https://github.com/${encode(repo.owner)}/${encode(repo.repo)}/archive/refs/heads/$encodedBranch.zip"
    }

    fun zipballUrl(repo: Repo): String =
        "https://api.github.com/repos/${encode(repo.owner)}/${encode(repo.repo)}/zipball"

    fun safeRelativeEntry(entryName: String): String? {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        val parts = normalized.split('/').filter { it.isNotBlank() && it != "." }
        if (parts.size < 2) return null
        if (parts.any { it == ".." }) return null
        return parts.drop(1).joinToString("/")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
