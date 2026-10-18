package com.deepseek.dsh.tooling.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * v4.0 设计文档 §5.6: 契约验证与回放器
 */
@Serializable
data class ContractFixture(
    val contractVersion: String,
    val expectedDigest: String,
    val mockEvents: List<FixtureEvent> = emptyList()
)

@Serializable
data class FixtureEvent(
    val type: String,
    val hostId: String? = null,
    val sessionId: String? = null,
    val delta: String? = null,
    val generation: Long? = null
)

class ContractReplayer(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun loadAndVerifyFixture(fixtureFile: File): Result<ContractFixture> {
        if (!fixtureFile.exists()) {
            return Result.failure(IllegalArgumentException("Fixture not found: ${fixtureFile.absolutePath}"))
        }

        return try {
            val content = fixtureFile.readText(Charsets.UTF_8)
            val fixture = json.decodeFromString<ContractFixture>(content)
            Result.success(fixture)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun calculateDigest(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
