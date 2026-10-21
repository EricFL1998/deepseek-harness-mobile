package com.deepseek.dsh.runtime.host

/**
 * 安装 spec 的解析与最小 semver 匹配。
 *
 * 插件页的「添加插件」接受三类输入：npm 包名、GitHub 仓库地址、本地绝对目录。
 * Host 内的 pnpm 在 proot 下会拿到 `ENOSYS`（rename/unlink/mkdtemp 不可用），
 * 因此 App 侧兜底安装只实现自己需要的那部分解析，不追求完整的 npm 语义。
 */
internal sealed class PluginInstallSpec {
    /** npm 包名，可带 `@range`。 */
    data class Registry(val name: String, val range: String?) : PluginInstallSpec()

    /** GitHub 仓库地址，可带 `#ref`。 */
    data class GitHub(val owner: String, val repo: String, val ref: String?) : PluginInstallSpec()

    /** rootfs 内的绝对目录。 */
    data class LocalPath(val path: String) : PluginInstallSpec()

    companion object {
        private val GITHUB = Regex(
            """^(?:https?://)?(?:www\.)?github\.com/([^/\s#]+)/([^/\s#]+?)(?:\.git)?(?:#(\S+))?/?$""",
            RegexOption.IGNORE_CASE,
        )
        private val PACKAGE_NAME = Regex("""^(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*$""")

        /**
         * 解析用户输入。
         * @param raw 对话框里的原始 spec。
         * @return 解析结果；无法识别时为 null。
         */
        fun parse(raw: String): PluginInstallSpec? {
            val spec = raw.trim()
            if (spec.isEmpty()) return null
            GITHUB.matchEntire(spec)?.let { match ->
                val owner = match.groupValues[1]
                val repo = match.groupValues[2]
                val ref = match.groupValues[3].takeIf { it.isNotBlank() }
                return GitHub(owner, repo, ref)
            }
            if (spec.startsWith("/")) return LocalPath(spec)
            val at = spec.indexOf('@', 1)
            val name = if (at == -1) spec else spec.substring(0, at)
            val range = if (at == -1) null else spec.substring(at + 1).takeIf { it.isNotBlank() }
            if (!PACKAGE_NAME.matches(name)) return null
            if (range != null && !Semver.isRangeLike(range)) return null
            return Registry(name, range)
        }
    }
}

/** 最小 semver：比较、range 判定、从版本表里挑最高匹配项。 */
internal object Semver {
    private val PLAIN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")
    private val RANGE = Regex("""^(?:[\^~<>=]*\s*)?v?\d+(?:\.\d+){0,2}(?:-[0-9A-Za-z.-]+)?(?:\s+\|\|\s+.*)?$""")

    /** 是否是 range 形态（不追求完整校验，只挡住明显非版本输入）。 */
    fun isRangeLike(raw: String): Boolean =
        raw == "*" || raw == "latest" || raw=="next" || RANGE.matches(raw)

    /** 解析成可比较的版本；非 semver 返回 null。 */
    fun parse(raw: String): Triple<Int, Int, Int>? {
        val match = PLAIN.matchEntire(raw.trim()) ?: return null
        return Triple(
            match.groupValues[1].toIntOrNull() ?: return null,
            match.groupValues[2].toIntOrNull() ?: return null,
            match.groupValues[3].toIntOrNull() ?: return null,
        )
    }

    /** 比较两个版本；无法解析时按字符串比较。 */
    fun compare(a: String, b: String): Int {
        val left = parse(a)
        val right = parse(b)
        if (left == null || right == null) return a.compareTo(b)
        for (index in 0..2) {
            val difference = left.toList()[index].compareTo(right.toList()[index])
            if (difference != 0) return difference
        }
        // 同号时：不带后缀的正式版更新。
        val aPre = a.contains('-')
        val bPre = b.contains('-')
        return when {
            aPre == bPre -> 0
            aPre -> -1
            else -> 1
        }
    }

    /**
     * 从候选版本里挑出满足 range 的最高版本。
     *
     * 支持 `*`/`latest`/`next`、精确版本、`^x.y.z`、`~x.y.z`、`>=x.y.z` 与
     * 空格分隔的多个条件；不认的形态退回「取最高正式版」，保证安装能继续，
     * 而不是卡在版本语法上。
     */
    fun maxSatisfying(versions: Collection<String>, range: String?): String? {
        val stable = versions.filter { parse(it) != null && !it.contains('-') }
        val pool = stable.ifEmpty { versions.filter { parse(it) != null } }
        if (pool.isEmpty()) return null
        val trimmed = range?.trim()
        if (trimmed.isNullOrEmpty() || trimmed == "*" || trimmed == "latest" || trimmed == "next") {
            return pool.maxWithOrNull { a, b -> compare(a, b) }
        }
        val matchers = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val matching = pool.filter { candidate -> matchers.all { matches(candidate, it) } }
        val effective = matching.ifEmpty { pool }
        return effective.maxWithOrNull { a, b -> compare(a, b) }
    }

    /** 单个 range 片段的匹配。 */
    private fun matches(candidate: String, token: String): Boolean {
        if (token == "*" || token == "latest" || token == "next") return true
        val operator = listOf("^", "~", ">=", "<=", ">", "<", "=").firstOrNull { token.startsWith(it) }
        val versionText = if (operator == null) token else token.removePrefix(operator)
        val version = parse(versionText) ?: return true
        val target = parse(candidate) ?: return false
        val comparison = compare(candidate, versionText)
        return when (operator) {
            "^" -> target.first == version.first && comparison >= 0
            "~" -> target.first == version.first && target.second == version.second && comparison >= 0
            ">=" -> comparison >= 0
            "<=" -> comparison <= 0
            ">" -> comparison > 0
            "<" -> comparison < 0
            else -> comparison == 0
        }
    }
}
